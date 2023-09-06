package ls.backup.cli.commands

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import kotlinx.cli.*
import kotlinx.coroutines.runBlocking
import ls.backup.cli.BackupBucket
import ls.backup.cli.S3Config
import ls.backup.cli.TimeWindow
import org.apache.kafka.clients.admin.AdminClientConfig
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@OptIn(ExperimentalCli::class)
class Restore : Subcommand("restore", "restore records from backup") {
    val bucket by option(
        ArgType.String,
        shortName = "b",
        description = "Bucket to restore from"
    ).required()

    val s3Endpoint by option(
        ArgType.String,
        description = "Url to S3. If not given defaults to AWS-S3"
    )

    val bootstrapServers by option(
        ArgType.String,
        description = "Url or list of Urls of kafka servers to restore to."
    )

    val pattern by option(
        ArgType.String,
        shortName = "p",
        description = "Pattern for source topics to restore. If not set all topics in the bucket are restored"
    ).default(".*")

    val outputPrefix by option(
        ArgType.String,
        description = "Restored records are produced to the same topic. If this is set the output topic will be prefixed. Assumes the topic exists or is created."
    ).default("")

    val start by option(
        ArgType.String,
        shortName = "s",
        description = "Start time from which to restore records. If not set all records are restored."
    )

    val end by option(
        ArgType.String,
        shortName = "e",
        description = "Until when records are restored. If not set all records up to now are restored."
    )

    val awsAccessKeyId by option(
        ArgType.String,
    )

    val awsSecretAccessKey by option(
        ArgType.String
    )


    override fun execute() = runBlocking {
        val s3Config = S3Config(
            if (awsAccessKeyId != null && awsSecretAccessKey != null) Credentials(
                awsAccessKeyId!!,
                awsSecretAccessKey!!
            ) else null,
            s3Endpoint
        )

        val kafkaConfig =
            createPropertiesFromEnv(overrides = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers).mapNotNull { (k, v) -> v?.let { k to v } }
                .toMap())

        val backupBucket = BackupBucket(bucket, s3Config, kafkaConfig)
        val timeWindow = TimeWindow(start?.let { parseDateInput(it) }, end?.let { parseDateInput(it) })
        backupBucket.restore(pattern, outputPrefix, timeWindow)
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
