package ls.kafka.backup.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.paginators.listObjectsV2Paginated
import aws.smithy.kotlin.runtime.content.toInputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import ls.coroutines.mapInOrder
import ls.kafka.io.RecordStreamReader
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
 * Fetches all backup files from the given S3 bucket.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun S3Client.backupFiles(
    bucket: String,
    prefix: String?,
    keyPattern: Regex,
    concurrencyLevel: Int = 3,
): Flow<BackupFile> {
    val pages = listObjectsV2Paginated { this.bucket = bucket; this.prefix = prefix; }
    return pages.flatMapConcat { resp ->
        val objects = resp.contents ?: return@flatMapConcat emptyFlow<BackupFile>()
        objects
            .takeWhile { !it.key.isNullOrEmpty() }
            .filter { it.key?.matches(keyPattern) ?: false }
            // Get objects in parallel but ensure they are processed in order
            .mapInOrder(concurrencyLevel) { listItem ->
                val key = listItem.key ?: error("Got object with empty key")
                getObject(GetObjectRequest {
                    this.key = key; this.bucket = bucket
                }) { response ->
                    val compressed = key.endsWith(".gz")
                    val inputStream = response.body?.toInputStream() ?: error("Got object with empty body")
                    val stream = if (compressed) {
                        GzipCompressorInputStream(inputStream)
                    } else {
                        inputStream
                    }
                    // we need to materialize the stream into a list of records here within the object response closure
                    // otherwise the stream will be closed before we can read it in a later stage.
                    val records = RecordStreamReader(stream).readAll()
                    BackupFile(key, records)
                }
            }
    }
}
