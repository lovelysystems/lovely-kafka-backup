package ls.kafka.backup.s3

import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.writeToFile
import aws.smithy.kotlin.runtime.net.Url
import kotlinx.coroutines.runBlocking
import ls.kafka.backup.io.BackupFile
import ls.kafka.backup.io.BackupFilePath
import ls.kafka.backup.TimeWindow
import ls.kafka.model.DumpRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.util.*
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream

typealias BackupRecord = Pair<String, DumpRecord>

class BackupBucket(private val bucket: String, s3Config: S3Config, private val kafkaConfig: Properties) {

    private val s3 = runBlocking {
        S3Client.fromEnvironment {
            s3Config.endpoint?.let {
                endpointUrl = Url.parse(it)
            }
            credentialsProvider = ProfileCredentialsProvider(profileName = s3Config.profile)

            region = s3Config.region
            forcePathStyle = true
        }
    }

    private fun objectPrefix(topic: String) = "topics/$topic"

    private suspend fun files(topicPattern: String): List<BackupFilePath> {
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

    private suspend fun backupFile(path: BackupFilePath): BackupFile {
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

    private suspend fun find(
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

    private suspend fun getRecords(
        topicPattern: String,
        partition: Int? = null,
    ): List<BackupRecord> = find(
        topicPattern,
        partition = partition,
    ).map { backupFile(it) }.flatMap { backupFile ->
        backupFile.records().map { BackupRecord(backupFile.topic, it) }
    }

    private suspend fun validate() {
        s3.headBucket {
            bucket = this@BackupBucket.bucket
        }
    }

    suspend fun restore(
        topicPattern: String,
        outputPrefix: String,
        timeWindow: TimeWindow = TimeWindow(null, null),
    ) {
        val records = getRecords(topicPattern).filter { tr -> timeWindow.contains(tr.second.ts) }
        println("Found ${records.size} records to restore")

        KafkaProducer(
            kafkaConfig,
            ByteArraySerializer(),
            ByteArraySerializer()
        ).use { producer ->
            records.forEach { (topic, record) ->
                producer.send(
                    ProducerRecord(
                        outputPrefix + topic,
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
