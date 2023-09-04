package ls.backup.cli

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.net.Url
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.util.*

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

    fun records(): Sequence<DumpRecord> {
        return stream.loadDump()
    }
}

data class DumpRecord(
    val partition: Int,
    val offset: Long,
    val ts: Long,
    val key: ByteArray,
    val value: ByteArray
) {
    fun withTopic(topic: String) = Record(topic, partition, offset, ts, key, value)
}

data class Record(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val ts: Long,
    val key: ByteArray,
    val value: ByteArray
)

fun InputStream.loadDump(): Sequence<DumpRecord> {
    val dis = DataInputStream(this)
    return sequence {
        dis.use { s ->
            while (true) {
                val partition = try {
                    s.readInt()
                } catch (e: EOFException) {
                    -1
                }
                if (partition == -1) {
                    break
                }
                val offset = s.readLong()
                val ts = s.readLong()
                val keySize = s.readInt()
                val key = ByteArray(keySize)
                s.read(key)
                val valueSize = s.readInt()
                val value = ByteArray(valueSize)
                s.read(value)

                val record = DumpRecord(partition, offset, ts, key, value)
                yield(record)
            }
        }
    }
}

data class S3Config(val credentials: Credentials?, val endpoint: String?, val region: String = "eu-west-1")

class BackupBucket(private val bucket: String, s3Config: S3Config, val kafkaConfig: Properties) {

    private val s3 = runBlocking {
        S3Client.fromEnvironment {
            s3Config.endpoint?.let {
                endpointUrl = Url.parse(it)
            }
            s3Config.credentials?.let { credentials ->
                credentialsProvider = StaticCredentialsProvider(
                    credentials
                )
            }
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
        }) {
            it.body?.toByteArray()?.inputStream()!!
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
