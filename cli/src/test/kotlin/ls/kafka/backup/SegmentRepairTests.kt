package ls.kafka.backup

import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import io.confluent.connect.s3.S3SinkConnectorConfig
import io.confluent.connect.s3.storage.S3Storage
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FreeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.SystemEnvironmentTestListener
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import ls.kafka.backup.s3.*
import ls.kafka.connect.storage.format.ByteArrayRecordFormat
import ls.testcontainers.kafka.kafkaContainer
import ls.testcontainers.minio.MinioContainer
import ls.testcontainers.minio.MinioCredentials
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.record.FileLogInputStream
import org.apache.kafka.common.record.FileRecords
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.connect.sink.SinkRecord
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@OptIn(ExperimentalCoroutinesApi::class)
class SegmentRepairTests : FreeSpec({
    val bucket = "backup-bucket"
    val credentials = MinioCredentials("minioadmin", "minioadmin")

    val kafkaData = tempdir().toPath()
    val kafka = install(ContainerExtension(kafkaContainer(volumePath = kafkaData.pathString)))

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

    val config = mapOf(
        "s3.bucket.name" to bucket,
        "format.class" to ByteArrayRecordFormat::class.qualifiedName,
        "format.bytearray.extension" to ".bytea",
        "flush.size" to "1",
        "storage.class" to "io.confluent.connect.s3.storage.S3Storage",
        "partitioner.class" to "io.confluent.connect.storage.partitioner.DailyPartitioner",
        "bootstrap.servers" to kafka.bootstrapServers
    )

    fun ConsumerRecord<String, ByteArray>.toSinkRecord(valueSuffix: String = ""): SinkRecord = SinkRecord(
        this.topic(),
        partition(),
        null,
        key().toByteArray(),
        null,
        value() + valueSuffix.toByteArray(),
        offset(),
        timestamp(),
        timestampType()
    )

    val minio = install(ContainerExtension(MinioContainer(credentials))) {
        createBucket(bucket)
    }

    fun produceSampleRecords(topic: String, number: Int) {
        KafkaProducer(kafkaConfig, ByteArraySerializer(), ByteArraySerializer()).use { producer ->
            repeat(number) {
                producer.send(ProducerRecord(topic, "key$it".toByteArray(), "value$it".toByteArray()))
            }
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

    "segment file loading" {
        produceSampleRecords("load-test", 100)

        val dataDir = DataDirectory(kafkaData)
        val logDir = dataDir.logDirectories("load-test*").shouldHaveSize(1).first()
        logDir.topic shouldBe "load-test"
        logDir.partition shouldBe 0

        val segments = logDir.segmentFiles().toList().shouldNotBeEmpty()
        val records = segments.flatMap { it.getFileRecords().records() }.toList()
        records.shouldHaveSize(100)
    }

    "read batches" {
        val topic = "batch-read"
        produceSampleRecords(topic, 100)
        val dataDir = DataDirectory(kafkaData)
        val logDir = dataDir.logDirectories("batch-read*").shouldHaveSize(1).first()
        logDir.segmentFiles().collect { sm ->
            sm.batchPairs().toList().forEach { (curr, last) ->
                if (last != null) {
                    last.position() + last.sizeInBytes() shouldBe curr.position()
                }
            }
        }
        val records = logDir.segmentFiles().flatMapConcat { sm ->
            flow {
                emitAll(sm.batchPairs().map { it.first.toRecords().records() })
            }
        }.toList().flatten()

        records.shouldHaveSize(100)
    }

    "repair broken segments" - {

        "should do nothing if nothing is corrupted" {
            produceSampleRecords("uncorrupted", 100)
            val topicDir = kafkaData / "uncorrupted-0"
            topicDir.listDirectoryEntries().shouldNotBeEmpty()

            val dataDir = DataDirectory(kafkaData)
            dataDir.repairBrokenSegments(backupBucket, "uncorrupted*").toList().shouldBeEmpty()
        }

        "write backupfile when offsets are missing" {
            val topic = "test-repair"
            produceSampleRecords(topic, 1000)
            val dataDir = DataDirectory(kafkaData)
            val sm = dataDir.logDirectories("$topic*").first().segmentFiles().toList().first()
            val spiedSegment = spyk(sm)
            every {
                spiedSegment.getCorruptedOffsets()
            } returns flow {
                (1..1000L step 10).forEach { emit(it) }
            }
            val bb = BackupBucket(bucket, S3Config(minio.getHostAddress()), Properties())
            val temp = tempdir().toPath()
            val out = temp / "${sm.startOffset}.log"
            spiedSegment.repairFromBackup(out.toFile(), bb, failOnMissing = false) shouldBe true
            out.fileSize() shouldBeGreaterThan 1024L * 5L
        }

        "should restore values from backup" {
            val topic = "backup-repair"
            val ts = 123456L
            produceSampleRecords(topic, 100)

            val connectorConfig = S3SinkConnectorConfig(config)
            val s3Storage = S3Storage(connectorConfig, minio.getHostAddress())
            val date = LocalDate.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("UTC"))

            val formatter = ByteArrayRecordFormat(s3Storage)
            formatter.recordWriterProvider.getRecordWriter(
                connectorConfig,
                "topics/$topic/year=${date.year}/month=${date.month}/day=${date.dayOfMonth}/$topic+0+0"
            ).use { writer ->
                consumeAllRecords("backup-repair").shouldHaveSize(100).forEach { record ->
                    writer.write(record.toSinkRecord("_backuped"))
                }
                writer.commit()
            }

            val brokenOffsets = 50..60L

            val dataDir = DataDirectory(kafkaData)
            val sm = dataDir.logDirectories("$topic*").first().segmentFiles().toList().last()

            mockkStatic(FileLogInputStream.FileChannelRecordBatch::missingOffsets)
            every {
                any<FileLogInputStream.FileChannelRecordBatch>().missingOffsets(matchNullable { it != null })
            } returnsMany listOf(brokenOffsets, brokenOffsets, LongRange.EMPTY)

            val bb = BackupBucket(bucket, S3Config(minio.getHostAddress()), Properties())
            val temp = tempdir().toPath()
            val out = temp / "${sm.startOffset}.log"
            sm.repairFromBackup(out.toFile(), bb, failOnMissing = false) shouldBe true

            val records = FileRecords.open(out.toFile()).records().toList()
            val values = records.map { StandardCharsets.UTF_8.decode(it.value()).toString() }
            values.shouldContainAll(brokenOffsets.map { "value${it}_backuped" })
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
