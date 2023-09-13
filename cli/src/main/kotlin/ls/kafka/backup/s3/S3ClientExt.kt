package ls.kafka.backup.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.paginators.listObjectsV2Paginated
import aws.smithy.kotlin.runtime.content.toInputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import ls.coroutines.mapInOrder
import ls.kafka.io.RecordStreamReader
import ls.kafka.model.DumpRecord
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
 * Return a cold flow of dumped Kafka records from a given S3 bucket. It accepts a couple of filters
 * to limit the backup files that are used for restore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun S3Client.backupRecords(
    bucket: String,
    prefix: String?,
    keyPattern: Regex,
    topic: String? = null,
    partition: Int? = null,
    fromOffset: Long = 0L,
    toOffset: Long? = null,
): Flow<Pair<String, DumpRecord>> {
    val pages = listObjectsV2Paginated { this.bucket = bucket; this.prefix = prefix; }
    return pages.mapNotNull { it.contents }.flatMapConcat { objects ->
        var keys = objects
            .mapNotNull { it.key }
            .filter { it.matches(keyPattern) }

        // ensure only files that match the offset range and partition are used for restore
        if (toOffset != null) {
            keys = keys.takeWhile { BackupFile(it).startOffset <= toOffset }
        }
        if (fromOffset != 0L) {
            keys = keys.filter { BackupFile(it).startOffset <= fromOffset }
        }
        if (topic != null) {
            keys = keys.filter { BackupFile(it).topic == topic }
        }
        if (partition != null) {
            keys = keys.filter { BackupFile(it).partition == partition }
        }

        backupRecords(bucket, keys)
    }
}

/**
 * Fetches all given keys from the given S3 bucket.
 * The returned flow will emit all records from all keys in the order they were requested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun S3Client.backupRecords(
    bucket: String,
    keys: List<String>,
    concurrencyLevel: Int = 3
) = keys.mapInOrder(concurrencyLevel) { key ->
    getObject(GetObjectRequest { this.bucket = bucket; this.key = key }) { resp ->
        val compressed = key.endsWith(".gz")
        val inputStream = resp.body?.toInputStream() ?: error("Got object with empty body")
        val stream = if (compressed) {
            GzipCompressorInputStream(inputStream)
        } else {
            inputStream
        }
        val records = RecordStreamReader(stream).readAll().toList()
        key to records.asFlow()
    }
}.flatMapConcat { it.second.map { p -> it.first to p } }
