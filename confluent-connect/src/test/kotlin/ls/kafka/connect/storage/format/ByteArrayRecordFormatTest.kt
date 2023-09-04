package ls.kafka.connect.storage.format

import io.confluent.connect.s3.S3SinkConnectorConfig
import io.confluent.connect.s3.storage.S3Storage
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.matchers.shouldBe
import ls.testcontainers.minio.MinioContainer
import ls.testcontainers.minio.MinioCredentials
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.connect.sink.SinkRecord

class ByteArrayRecordFormatTest : StringSpec({

    val bucket = "backup-bucket"
    val credentials = MinioCredentials("minioadmin", "minioadmin")
    val env = mapOf(
        "AWS_ACCESS_KEY_ID" to credentials.accessKey,
        "AWS_SECRET_ACCESS_KEY" to credentials.secretKey
    )

    val config = mapOf(
        "s3.bucket.name" to bucket,
        "format.class" to ByteArrayRecordFormat::class.qualifiedName,
        "format.bytearray.extension" to ".bytea",
        "flush.size" to "1",
        "storage.class" to "io.confluent.connect.s3.storage.S3Storage",
    )

    val sampleRecord = SinkRecord(
        "topic",
        1,
        null,
        "key".toByteArray(),
        null,
        "value".toByteArray(),
        10,
        1693526400000,
        TimestampType.CREATE_TIME
    )

    val minio = install(ContainerExtension(MinioContainer(credentials))) {
        createBucket(bucket)
    }

    afterEach { minio.clearFiles(bucket) }

    "backup file with correct extension" {
        val connectorConfig = S3SinkConnectorConfig(config)
        val s3Storage = S3Storage(connectorConfig, minio.getHostAddress())

        val formatter = ByteArrayRecordFormat(s3Storage)
        withEnvironment(env) {
            formatter.recordWriterProvider.getRecordWriter(connectorConfig, "file-name-1").use {writer ->
                writer.write(sampleRecord)
                writer.commit()
            }
        }

        minio.listFiles(bucket) shouldBe listOf("file-name-1.bytea")
    }

    "backup record with gzip compression" {
        val connectorConfig = S3SinkConnectorConfig(config + ("s3.compression.type" to "gzip"))
        val s3Storage = S3Storage(connectorConfig, minio.getHostAddress())

        val formatter = ByteArrayRecordFormat(s3Storage)
        withEnvironment(env) {
            formatter.recordWriterProvider.getRecordWriter(connectorConfig, "zip-file-name-1").use {writer ->
                writer.write(sampleRecord)
                writer.commit()
            }
        }
        minio.listFiles(bucket) shouldBe listOf("zip-file-name-1.bytea.gz")
    }
})
