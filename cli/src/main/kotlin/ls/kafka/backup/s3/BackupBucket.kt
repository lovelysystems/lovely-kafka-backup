package ls.kafka.backup.s3

import aws.sdk.kotlin.runtime.auth.credentials.EnvironmentCredentialsProvider
import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProviderChain
import aws.smithy.kotlin.runtime.net.Url
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.util.*

/**
 * Allows restore of Kafka records from a backup within an S3 bucket.
 */
class BackupBucket(private val bucket: String, s3Config: S3Config, private val kafkaConfig: Properties) {

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
    private suspend fun validateBucket() {
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
            backupFiles.collect {
                it.records.forEach { record ->
                    // skip records that do not match the offset range
                    if (fromOffset != 0L && record.offset < fromOffset) return@forEach
                    if (toOffset != null && record.offset >= toOffset) return@forEach

                    producer.send(
                        ProducerRecord(
                            targetTopic ?: it.topic,
                            null,
                            record.ts,
                            record.key,
                            record.value
                        )
                    )
                }
            }
        }
    }
}
