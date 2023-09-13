package ls.kafka.backup.s3

import aws.sdk.kotlin.runtime.auth.credentials.EnvironmentCredentialsProvider
import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProviderChain
import aws.smithy.kotlin.runtime.net.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.util.*

/**
 * Allows restore of Kafka records from a backup within an S3 bucket.
 */
class BackupBucket(private val bucket: String, s3Config: S3Config, private val kafkaConfig: Properties) {

    private val logger = KotlinLogging.logger { }

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
        logger.info { "Restoring records from path s3://$bucket/$prefix" }
        val backupRecords = s3.backupRecords(
            bucket,
            prefix,
            keyPattern,
            partition = partition,
            fromOffset = fromOffset,
            toOffset = toOffset
        )

        KafkaProducer(
            kafkaConfig,
            ByteArraySerializer(),
            ByteArraySerializer()
        ).use { producer ->
            var count = 0L
            backupRecords.collect { (key, record) ->
                if (count > 0 && count % FLUSH_SIZE == 0L) {
                    producer.flush()
                    logger.info { "Restored $count records" }
                }

                // skip records that do not match the offset range
                if (fromOffset != 0L && record.offset < fromOffset) return@collect
                if (toOffset != null && record.offset >= toOffset) return@collect

                producer.send(
                    ProducerRecord(
                        targetTopic ?: BackupFile(key).topic,
                        null,
                        record.ts,
                        record.key,
                        record.value
                    )
                )
                count++
            }
            producer.flush()

            logger.info {
                buildString {
                    append("Restored a total of $count records ")
                    targetTopic?.let {
                        append("to topic $it")
                    }
                }
            }
        }
    }

    fun getRecordsForOffsets(
        topic: String,
        prefix: String = "",
        keyPattern: Regex = Regex(".*"),
        partition: Int?,
        offsets: Set<Long>,
    ) = s3.backupRecords(bucket, prefix, keyPattern, topic, partition)
        .filter { it.second.offset in offsets }.map { it.second }

    companion object {
        const val FLUSH_SIZE = 100
    }
}
