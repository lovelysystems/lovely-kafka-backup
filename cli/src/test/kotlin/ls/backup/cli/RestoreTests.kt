package ls.backup.cli

import io.confluent.connect.s3.S3SinkConnectorConfig
import io.confluent.connect.s3.storage.S3Storage
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.SystemEnvironmentTestListener
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.inspectors.forNone
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import ls.kafka.connect.storage.format.ByteArrayRecordFormat
import ls.testcontainers.minio.MinioContainer
import ls.testcontainers.minio.MinioCredentials
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.connect.sink.SinkRecord
import java.time.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class RestoreTests : FreeSpec({
    val bucket = "backup-bucket"
    val credentials = MinioCredentials("minioadmin", "minioadmin")

    val kafka = install(ContainerExtension(KraftKafkaContainer()))

    val kafkaConfig = mapOf(
        "bootstrap.servers" to kafka.bootstrapServers,
        "auto.offset.reset" to "earliest",
    ).toProperties()

    val config = mapOf(
        "s3.bucket.name" to bucket,
        "format.class" to ByteArrayRecordFormat::class.qualifiedName,
        "format.bytearray.extension" to ".bytea",
        "flush.size" to "1",
        "storage.class" to "io.confluent.connect.s3.storage.S3Storage",
        "partitioner.class" to "io.confluent.connect.storage.partitioner.DailyPartitioner",
        "bootstrap.servers" to kafka.bootstrapServers
    )

    fun sampleRecord(topic: String, number: Int, ts: Long) = SinkRecord(
        topic,
        1,
        null,
        "key".toByteArray(),
        null,
        "value_$number".toByteArray(),
        10L * number,
        ts,
        TimestampType.CREATE_TIME
    )

    val minio = install(ContainerExtension(MinioContainer(credentials))) {
        createBucket(bucket)
    }

    fun writeSampleRecords(topic: String, number: Int, ts: Long = 124515L) {
        val connectorConfig = S3SinkConnectorConfig(config)
        val s3Storage = S3Storage(connectorConfig, minio.getHostAddress())

        val date = LocalDate.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("UTC"))

        val formatter = ByteArrayRecordFormat(s3Storage)
        formatter.recordWriterProvider.getRecordWriter(
            connectorConfig,
            "topics/$topic/year=${date.year}/month=${date.month}/day=${date.dayOfMonth}/$topic+0+$number"
        ).use { writer ->
            repeat(number) {
                writer.write(sampleRecord(topic, it, ts))
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
        val sourceTopic = "test_some"
        val recordCount = 300
        writeSampleRecords(sourceTopic, recordCount)

        "to new topic" - {
            val prefix = "other_prefix_"
            backupBucket.restore(sourceTopic, prefix)

            "then created target topic should contain all records from backup" {
                consumeAllRecords(prefix + sourceTopic).shouldNotBeNull().shouldHaveSize(recordCount)
            }
        }
    }

    "when restoring without prefix" - {
        val topic = "test_topic"
        val recordCount = 300
        writeSampleRecords(topic, recordCount)
        "restored topic name should be the same and contain records" {
            backupBucket.restore(topic, "")
            consumeAllRecords(topic).shouldNotBeNull().shouldHaveSize(recordCount)
        }
    }

    "when restoring with time window" - {
        val sourceTopic = "windowed_test"
        val recordsPerDay = 100
        val now = Instant.now()
        writeSampleRecords(sourceTopic, recordsPerDay, now.toEpochMilli())
        writeSampleRecords(sourceTopic, recordsPerDay, now.toEpochMilli() - 24 * 60 * 60 * 1000)
        writeSampleRecords(sourceTopic, recordsPerDay, now.toEpochMilli() + 24 * 60 * 60 * 1000)

        val prefix = "some"
        val targetTopic = prefix + sourceTopic

        val timeWindow = TimeWindow(now.toEpochMilli(), now.toEpochMilli() + 23 * 60 * 60 * 1000)
        backupBucket.restore(sourceTopic, prefix, timeWindow)
        "then records in the time window should be restored" {
            consumeAllRecords(targetTopic).shouldNotBeNull().shouldHaveSize(recordsPerDay).forEach { record ->
                record.timestamp() shouldBeGreaterThanOrEqual timeWindow.from!!
                record.timestamp() shouldBeLessThan timeWindow.to!!
            }
        }

        "then records before the time window should be ignored" {
            consumeAllRecords(targetTopic).shouldNotBeNull().forNone { record ->
                record.timestamp() shouldBeLessThan timeWindow.from!!
            }
        }
        "then records after the time window should be ignored" {
            consumeAllRecords(targetTopic).shouldNotBeNull().forNone { record ->
                record.timestamp() shouldBeGreaterThan timeWindow.to!!
            }
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
