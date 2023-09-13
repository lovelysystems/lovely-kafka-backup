package ls.kafka.backup.s3

import aws.sdk.kotlin.runtime.auth.credentials.EnvironmentCredentialsProvider
import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProviderChain
import aws.smithy.kotlin.runtime.net.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import ls.kafka.model.DumpRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.util.*

/**
 * Allows restore of Kafka records from a backup within an S3 bucket.
 */
class BackupBucket(private val bucket: String, s3Config: S3Config, private val kafkaConfig: Properties) {

    private val logger = KotlinLogging.logger {  }

    private val s3 = runBlocking {
        S3Client.fromEnvironment {
            s3Config.endpoint?.let {
                endpointUrl = Url.parse(it)
            }
            credentialsProvider = CredentialsProviderChain(
                ProfileCredentialsProvider(profileName = s3Config.profile),
                EnvironmentCredentialsProvider()
            )
            region = s3Config.region
            forcePathStyle = true
        }
    }

    /**
     * Validates that the bucket exists and the user has access to it.
     */
    suspend fun validateBucket() {
        s3.headBucket {
            bucket = this@BackupBucket.bucket
        }
    }

    suspend fun restore(
        prefix: String = "",
        keyPattern: Regex = Regex(".*"),
        partition: Int? = null,
        targetTopic: String? = null,
        fromOffset: Long = 0L,
        toOffset: Long? = null,
    ) {
        validateBucket()
        logger.info { "Restoring from bucket $bucket with prefix: $prefix, keyPattern: ${keyPattern.pattern}, partition: $partition" }
        var backupFiles = s3.backupFiles(bucket, prefix, keyPattern)
        // ensure only files that match the offset range and partition are used for restore
        if (toOffset != null) {
            backupFiles = backupFiles.takeWhile { it.startOffset <= toOffset }
        }
        if (fromOffset != 0L) {
            backupFiles = backupFiles.filter { it.startOffset <= fromOffset }
        }
        if (partition != null) {
            backupFiles = backupFiles.filter { it.partition == partition }
        }

        KafkaProducer(
            kafkaConfig,
            ByteArraySerializer(),
            ByteArraySerializer()
        ).use { producer ->
            var count = 0
            backupFiles.withIndex().collect { (index, file) ->
                count = index
                if (count > 0 && count % FLUSH_SIZE == 0) {
                    producer.flush()
                    logger.info { "Restored $count records" }
                }
                file.records.forEach { record ->
                    // skip records that do not match the offset range
                    if (fromOffset != 0L && record.offset < fromOffset) return@forEach
                    if (toOffset != null && record.offset >= toOffset) return@forEach

                    producer.send(
                        ProducerRecord(
                            targetTopic ?: file.topic,
                            null,
                            record.ts,
                            record.key,
                            record.value
                        )
                    )
                }
            }
            producer.flush()
            logger.info { buildString {
                append("Restored a total of $count records ")
                targetTopic?.let {
                    append("to topic $it")
                }
            } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getRecordsForOffsets(
        topic: String,
        partition: Int?,
        offsets: Set<Long>,
    ): Flow<DumpRecord> =
        s3.backupFiles(bucket, null, Regex(".*")).filter { backupFile -> backupFile.topic == topic }
            .flatMapConcat { backupFile ->
                val allRecords = backupFile.records
                val recordsInPartition = if (partition != null) {
                    allRecords.filter { record -> record.partition == partition }
                } else {
                    allRecords
                }
                recordsInPartition.filter { record -> record.offset in offsets }.asFlow()
            }

    companion object {
        const val  FLUSH_SIZE = 100
    }
}
