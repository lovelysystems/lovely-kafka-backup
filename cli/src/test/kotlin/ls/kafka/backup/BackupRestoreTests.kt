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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearConstructorMockk
import io.mockk.coEvery
import io.mockk.mockkConstructor
import kotlinx.coroutines.flow.toList
import ls.kafka.backup.s3.BackupBucket
import ls.kafka.backup.s3.S3Config
import ls.kafka.connect.storage.format.ByteArrayRecordFormat
import ls.testcontainers.kafka.kafkaContainer
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
    val topicNumPartitions = 3

    val kafka = install(ContainerExtension(kafkaContainer(partitions = topicNumPartitions)))

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

    fun consumeRecords(topic: String, partition: Int? = null): List<ConsumerRecord<String, ByteArray>> {
        val consumer = KafkaConsumer(kafkaConfig, StringDeserializer(), ByteArrayDeserializer())
        val partitions = consumer.partitionsFor(topic)!!
        partitions.size shouldBe topicNumPartitions
        val topicPartitions = partitions
            .filter { partition == null || partition == it.partition() }
            .map { TopicPartition(it.topic(), it.partition()) }
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
                consumeRecords("restored_test_restore_full_topic").shouldHaveSize(recordCount)
            }
        }
    }

    "when restoring a topic" - {
        val sourceTopic = "test_retain_partitions"
        val restoredTopic = "restored_$sourceTopic"
        writeSampleRecords(sourceTopic, 0, 100, partition = 0)
        writeSampleRecords(sourceTopic, 0, 50, partition = 2)
        writeSampleRecords(sourceTopic, 0, 200, partition = 1)

        "to new topic" - {
            backupBucket.restore("topics/$sourceTopic", targetTopic = restoredTopic)

            "then created target topic should retain the partition of the backup record" {
                consumeRecords(restoredTopic, partition = 0).shouldHaveSize(100)
                consumeRecords(restoredTopic, partition = 1).shouldHaveSize(200)
                consumeRecords(restoredTopic, partition = 2).shouldHaveSize(50)
            }
        }
    }

    "when restoring without prefix" - {
        val topic = "test_topic"
        val recordCount = 100
        writeSampleRecords(topic, 0, recordCount)

        "restored topic name should be the same and contain records" {
            backupBucket.restore()
            consumeRecords(topic).shouldHaveSize(recordCount)
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
            consumeRecords(restoreTopic).shouldHaveSize(60)
        }

        "open end offset window should restore all records from the start offset" {
            val restoreTopic = restoreTopicPrefix + "40"
            backupBucket.restore(
                "topics/$topic",
                targetTopic = restoreTopic,
                fromOffset = 40L,
            )
            consumeRecords(restoreTopic).shouldHaveSize(60)
        }

        "closed end offset window should restore all records from beginning" {
            val restoreTopic = restoreTopicPrefix + "0_50"
            backupBucket.restore(
                "topics/$topic",
                targetTopic = restoreTopic,
                toOffset = 50L
            )
            consumeRecords(restoreTopic).shouldHaveSize(50)
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
            consumeRecords(restoreTopic).shouldHaveSize(recordCount)
        }
    }

    "find records for offset" {
        writeSampleRecords("get_for_offset", 0, 100)

        val records = backupBucket.getRecordsForOffsets(
            "get_for_offset",
            partition = 0,
            offsets = (1..100L step 2).toSet()
        ).toList()
        records.shouldHaveSize(50)
        records.forAll { it.offset % 2 shouldBe 1 }
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
