package ls.kafka.backup.cli.commands

import ls.kafka.backup.s3.BackupBucket
import ls.kafka.backup.s3.S3Config
import org.apache.kafka.clients.admin.AdminClientConfig
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.*

@Command
class BackupCli

class Restore {

    @Option(names = ["-b", "--bucket"], required = true, description = ["Bucket to restore from"])
    lateinit var bucket: String

    @Option(names = ["--s3Endpoint"], description = ["Url to S3. If not given defaults to AWS-S3"])
    var s3Endpoint: String? = null

    @Option(names = ["--bootstrapServers"], description = ["Url or list of Urls of kafka servers to restore to."])
    var bootstrapServers: String? = null

    @Option(
        names = ["-p", "--prefix"],
        defaultValue = "",
        description = ["Path prefix of the source topics to restore."]
    )
    lateinit var prefix: String

    @Option(
        names = ["-k", "--key"],
        defaultValue = ".*",
        description = ["Regex pattern to match the keys to restore."]
    )
    lateinit var keyPattern: String

    @Option(names = ["--partition"], description = ["Partition of the source topics to restore."])
    var partition: Int? = null

    @Option(names = ["--from"], defaultValue = "0", description = ["First offset to restore from."])
    var fromOffset: Long = 0

    @Option(names = ["--to"], description = ["Last offset to restore to."])
    var toOffset: Long? = null

    @Option(
        names = ["-t", "--topic"],
        description = ["Restored records are produced to the same topic. Set to override the target topic name. Assumes the topic exists or is created."]
    )
    var targetTopic: String? = null

    @Option(names = ["--profile"], description = ["The profile to use for s3 access."])
    var profile: String? = null

    suspend fun execute() {
        val s3Config = S3Config(s3Endpoint, profile)
        val kafkaConfig = createPropertiesFromEnv(
            overrides = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)
                .mapNotNull { (k, v) -> v?.let { k to v } }
                .toMap()
        )

        val backupBucket = BackupBucket(bucket, s3Config, kafkaConfig)
        backupBucket.restore(prefix, Regex(keyPattern), partition, targetTopic, fromOffset, toOffset)
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
