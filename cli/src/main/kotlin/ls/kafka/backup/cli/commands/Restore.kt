package ls.kafka.backup.cli.commands

import ls.kafka.backup.s3.BackupBucket
import ls.kafka.backup.s3.S3Config
import ls.kafka.backup.TimeWindow
import org.apache.kafka.clients.admin.AdminClientConfig
import picocli.CommandLine.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Command
class BackupCli

class Restore {

    @Option(names = ["-b", "--bucket"], required = true, description = ["Bucket to restore from"])
    lateinit var bucket: String

    @Option(names = ["--s3Endpoint"], description = ["Url to S3. If not given defaults to AWS-S3"])
    var s3Endpoint: String? = null

    @Option(names=["--bootstrapServers"], description = ["Url or list of Urls of kafka servers to restore to."])
    var bootstrapServers: String? = null

    @Option(names=["-p", "--topicPattern"], required = true, description = ["Pattern for source topics to restore."])
    lateinit var topicPattern: String

    @Option(names=["--outputPrefix"], defaultValue = "", description = ["Restored records are produced to the same topic. If this is set the output topic will be prefixed. Assumes the topic exists or is created."])
    lateinit var outputPrefix: String

    @Option(names=["--fromTs"], description = ["Start time from which to restore records. If not set all records are restored."])
    var fromTs: String? = null

    @Option(names=["--toTs"], description = ["Until when records are restored. If not set all records up to now are restored."])
    var toTs: String? = null

    @Option(names=["--profile"], description = ["The profile to use for s3 access."])
    var profile: String? = null

    suspend fun execute() {
        val s3Config = S3Config(s3Endpoint, profile)

        val kafkaConfig =
            createPropertiesFromEnv(overrides = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers).mapNotNull { (k, v) -> v?.let { k to v } }
                .toMap())

        val backupBucket = BackupBucket(bucket, s3Config, kafkaConfig)
        val timeWindow = TimeWindow(fromTs?.let { parseDateInput(it) }, toTs?.let { parseDateInput(it) })
        backupBucket.restore(topicPattern, outputPrefix, timeWindow)
    }

}

fun createPropertiesFromEnv(
    prefix: String = "KAFKA_",
    overrides: Map<String, String> = emptyMap(),
    getter: () -> Map<String, String> = System::getenv
): Properties {
    val props = Properties()
    getter().forEach { (k, v) ->
        if (k.startsWith(prefix)) {
            props[k.substring(prefix.length).replace("_", ".").lowercase()] = v
        }
    }
    overrides.forEach { (k, v) ->
        props[k] = v
    }
    return props
}

fun parseDateInput(text: String) = LocalDateTime.parse(text).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
