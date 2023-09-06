package ls.backup.cli

import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.writeToFile
import aws.smithy.kotlin.runtime.net.Url
import kotlinx.coroutines.runBlocking
import ls.kafka.io.RecordStreamReader
import ls.kafka.model.DumpRecord
import ls.kafka.model.Record
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.util.*
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream

data class BackupFilePath(val name: String) {
    val topic: String
    val partition: Int
    val startOffset: Long

    init {
        name.substringBefore(".").split("+").let { nameParts ->
            topic = nameParts[0]
            partition = nameParts[1].toInt()
            startOffset = nameParts[2].toLong()
        }
    }

    override fun toString(): String {
        return "BackupFilePath(name='$name')"
    }
}

class BackupFile(name: String, inputStream: InputStream) {

    val compressed = name.endsWith(".gz")

    val topic: String
    val partition: Int
    val startOffset: Long

    private val stream: InputStream

    init {
        name.split("/").last().substringBefore(".").split("+").let { nameParts ->
            topic = nameParts[0]
            partition = nameParts[1].toInt()
            startOffset = nameParts[2].toLong()
        }
        stream = if (compressed) {
            GzipCompressorInputStream(inputStream)
        } else {
            inputStream
        }
    }

    fun records(): List<DumpRecord> = RecordStreamReader(stream).readAll()
}


fun DumpRecord.withTopic(topic: String) = Record(topic, partition, offset, ts, key, value)

data class S3Config(val endpoint: String?, val profile: String? = null, val region: String = "eu-west-1")

class BackupBucket(val bucket: String, s3Config: S3Config, val kafkaConfig: Properties) {

    private val s3 = runBlocking {
        S3Client.fromEnvironment {
            s3Config.endpoint?.let {
                endpointUrl = Url.parse(it)
            }
            //if (s3Config.profile != null || System.getenv("AWS_PROFILE") != null) {
            credentialsProvider = ProfileCredentialsProvider(profileName = s3Config.profile)

            region = s3Config.region
            forcePathStyle = true
        }
    }

    private fun objectPrefix(topic: String) = "topics/$topic"

    suspend fun files(topicPattern: String): List<BackupFilePath> {
        validate()
        val objects = s3.listObjectsV2 {
            bucket = this@BackupBucket.bucket
        }.contents.orEmpty().filter { obj ->
            obj.key?.matches("${objectPrefix(topicPattern)}.*".toRegex()) ?: false
        }

        return objects.mapNotNull { obj ->
            obj.key?.let { BackupFilePath(it) }
        }
    }

    suspend fun backupFile(path: BackupFilePath): BackupFile {
        val stream = s3.getObject(GetObjectRequest.invoke {
            bucket = this@BackupBucket.bucket
            key = path.name
        }) { response ->
            val tempFile = createTempFile()
            val body = response.body ?: error("Got object with empty body")
            body.writeToFile(tempFile)
            tempFile.inputStream()
            //TODO remove tempfile once the sdk has a released version which directly gives the input stream.
            // Fix is already merged in the sdk but not released, see:
            // https://github.com/awslabs/smithy-kotlin/pull/945
            // https://github.com/awslabs/aws-sdk-kotlin/issues/617
        }
        return BackupFile(path.name, stream)
    }

    suspend fun find(
        topicPattern: String,
        partition: Int? = null,
        fromOffset: Long = 0L,
        toOffset: Long? = null
    ): List<BackupFilePath> {
        var backupFiles = files(topicPattern)
        if (toOffset != null) {
            backupFiles = backupFiles.takeWhile { it.startOffset <= toOffset }
        }
        if (partition != null) {
            backupFiles = backupFiles.filter { it.partition == partition }
        }
        return if (fromOffset != 0L) {
            backupFiles.filter { file -> file.startOffset > fromOffset }
        } else {
            backupFiles
        }
    }

    suspend fun getRecords(
        topicPattern: String,
        partition: Int? = null,
    ): List<Record> = find(
        topicPattern,
        partition = partition,
    ).map { backupFile(it) }.flatMap { backupFile ->
        backupFile.records().map { it.withTopic(backupFile.topic) }
    }


    suspend fun validate() {
        s3.headBucket {
            bucket = this@BackupBucket.bucket
        }
    }

    suspend fun restore(
        topicPattern: String,
        outputPrefix: String,
        timeWindow: TimeWindow = TimeWindow(null, null),
    ) {
        val records = getRecords(topicPattern).filter { record -> timeWindow.contains(record.ts) }

        KafkaProducer(
            kafkaConfig,
            ByteArraySerializer(),
            ByteArraySerializer()
        ).use { producer ->
            records.forEach { record ->
                producer.send(
                    ProducerRecord(
                        outputPrefix + record.topic,
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

data class TimeWindow(val from: Long?, val to: Long?) {
    fun contains(ts: Long): Boolean = (from == null || ts >= from) && (to == null || ts <= to)
}
