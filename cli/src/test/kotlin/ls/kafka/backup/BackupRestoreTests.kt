package ls.kafka.backup

import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import io.confluent.connect.s3.S3SinkConnectorConfig
import io.confluent.connect.s3.storage.S3Storage
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.SystemEnvironmentTestListener
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.mockk.clearConstructorMockk
import io.mockk.coEvery
import io.mockk.mockkConstructor
import ls.kafka.backup.s3.BackupBucket
import ls.kafka.backup.s3.S3Config
import ls.kafka.connect.storage.format.ByteArrayRecordFormat
import ls.testcontainers.kafka.KafkaKraftContainer
import ls.testcontainers.minio.MinioContainer
import ls.testcontainers.minio.MinioCredentials
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.connect.sink.SinkRecord
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class BackupRestoreTests : FreeSpec({
    val bucket = "backup-bucket"
    val credentials = MinioCredentials("minioadmin", "minioadmin")

    val kafka = install(ContainerExtension(KafkaKraftContainer()))

    mockkConstructor(ProfileCredentialsProvider::class)
    coEvery {
        anyConstructed<ProfileCredentialsProvider>().resolve(any())
    } returns Credentials("minioadmin", "minioadmin")

    afterSpec {
        clearConstructorMockk(ProfileCredentialsProvider::class)
    }

    val kafkaConfig = mapOf(
        "bootstrap.servers" to kafka.bootstrapServers,
        "auto.offset.reset" to "earliest",
    ).toProperties()

    fun sampleRecord(topic: String, offset: Long, ts: Long, partition: Int) = SinkRecord(
        topic,
        partition,
        null,
        "key".toByteArray(),
        null,
        "value_$offset".toByteArray(),
        offset,
        ts,
        TimestampType.CREATE_TIME
    )

    val minio = install(ContainerExtension(MinioContainer(credentials))) {
        createBucket(bucket)
    }

    fun writeSampleRecords(topic: String, startOffset: Long, rowCount: Int, partition: Int = 0) {
        val connectorConfig = S3SinkConnectorConfig(
            mapOf(
                "s3.bucket.name" to bucket,
                "format.class" to ByteArrayRecordFormat::class.qualifiedName,
                "flush.size" to "10",
                "storage.class" to "io.confluent.connect.s3.storage.S3Storage",
            )
        )
        val s3Storage = S3Storage(connectorConfig, minio.getHostAddress())

        val formatter = ByteArrayRecordFormat(s3Storage)
        formatter.recordWriterProvider.getRecordWriter(
            connectorConfig,
            "topics/$topic/$topic+$partition+$startOffset"
        ).use { writer ->
            repeat(rowCount) {
                writer.write(sampleRecord(topic, startOffset + it, it * 1000L, partition))
            }
            writer.commit()
        }
    }

    fun consumeAllRecords(topic: String): List<ConsumerRecord<String, ByteArray>> {
        val consumer = KafkaConsumer(kafkaConfig, StringDeserializer(), ByteArrayDeserializer())
        val partitions = consumer.partitionsFor(topic)!!
        partitions.shouldNotBeEmpty()
        val topicPartitions = partitions.map { TopicPartition(it.topic(), it.partition()) }
        consumer.assign(topicPartitions)
        return consumer.poll(5.seconds.toJavaDuration()).toList()
    }

    val backupBucket = BackupBucket(bucket, S3Config(minio.getHostAddress()), kafkaConfig)

    "when restoring a full topic from backup " - {
        val sourceTopic = "test_restore_full_topic"
        val recordCount = 100
        writeSampleRecords(sourceTopic, 0, recordCount)

        "to new topic" - {
            backupBucket.restore("topics/$sourceTopic", targetTopic = "restored_test_restore_full_topic")

            "then created target topic should contain all records from backup" {
                consumeAllRecords("restored_test_restore_full_topic").shouldHaveSize(recordCount)
            }
        }
    }

    "when restoring without prefix" - {
        val topic = "test_topic"
        val recordCount = 100
        writeSampleRecords(topic, 0, recordCount)

        "restored topic name should be the same and contain records" {
            backupBucket.restore()
            consumeAllRecords(topic).shouldHaveSize(recordCount)
        }
    }

    "when restoring with offset window" - {
        val topic = "offset_window_test"
        val recordCount = 100
        writeSampleRecords(topic, 0, recordCount)

        val restoreTopicPrefix = "restored_offset_window_"

        "then records within the offset window should be restored" {
            val restoreTopic = restoreTopicPrefix + "10_70"
            backupBucket.restore(
                "topics/$topic",
                targetTopic = restoreTopic,
                fromOffset = 10L,
                toOffset = 70L
            )
            consumeAllRecords(restoreTopic).shouldHaveSize(60)
        }

        "open end offset window should restore all records from the start offset" {
            val restoreTopic = restoreTopicPrefix + "40"
            backupBucket.restore(
                "topics/$topic",
                targetTopic = restoreTopic,
                fromOffset = 40L,
            )
            consumeAllRecords(restoreTopic).shouldHaveSize(60)
        }

        "closed end offset window should restore all records from beginning" {
            val restoreTopic = restoreTopicPrefix + "0_50"
            backupBucket.restore(
                "topics/$topic",
                targetTopic = restoreTopic,
                toOffset = 50L
            )
            consumeAllRecords(restoreTopic).shouldHaveSize(50)
        }
    }

    "when restoring with partition" - {
        val topic = "partition_test"
        val recordCount = 1

        // write records to partition #1
        writeSampleRecords(topic, 0, recordCount, partition = 1)
        writeSampleRecords(topic, 0, recordCount, partition = 2)

        val restoreTopicPrefix = "restored_partition_"

        "then records within the partition should be restored" {
            val restoreTopic = restoreTopicPrefix + "1"
            backupBucket.restore(
                "topics/$topic",
                targetTopic = restoreTopic,
                partition = 1
            )
            consumeAllRecords(restoreTopic).shouldHaveSize(recordCount)
        }
    }

}) {
    override fun listeners() = listOf(
        SystemEnvironmentTestListener(
            mapOf(
                "AWS_ACCESS_KEY_ID" to "minioadmin",
                "AWS_SECRET_ACCESS_KEY" to "minioadmin",
            ), OverrideMode.SetOrOverride
        )
    )
}
