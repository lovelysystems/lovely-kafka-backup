package ls.kafka.backup.cli.commands

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import ls.kafka.backup.s3.BackupBucket
import ls.kafka.backup.s3.DataDirectory
import ls.kafka.backup.s3.S3Config
import picocli.CommandLine.*
import java.util.*
import kotlin.io.path.Path

@Command(name = "repair", description = [" - repair corrupted Kafka records from an S3 backup"])
class Repair : Runnable {

    private val logger = KotlinLogging.logger { }

    @Option(
        names = ["-b", "--bucket"],
        description = ["The S3 bucket to source backups used for repair"],
        required = true
    )
    lateinit var bucket: String

    @Option(
        names = ["--data-directory"],
        description = ["Data directory to which contains the kafka data"],
        required = true
    )
    lateinit var dataDirectory: String

    @Option(names = ["-f", "--filter"], description = ["glob pattern to filter log directories"])
    var filter = "*"

    @Option(names = ["-s", "--skipBroken"], description = ["Skip records which are not in the backup"])
    var skipBroken = false

    @Option(names = ["-r", "--repair"], description = ["Repair files inline"])
    var repair = false

    @Option(names = ["--s3Endpoint"], description = ["Url to S3. If not given defaults to AWS-S3"])
    var s3Endpoint: String? = null

    @Option(names = ["--profile"], description = ["The profile to use for s3 access."])
    var profile: String? = null

    override fun run() = runBlocking {
        val s3Config = S3Config(s3Endpoint, profile)

        val dataDir = DataDirectory(Path(dataDirectory))

        if (repair) {
            val bb = BackupBucket(bucket, s3Config, Properties())
            // ensure the bucket works before we do anything
            bb.validateBucket()
            dataDir.repairBrokenSegments(bb, filter, skipBroken = skipBroken).collect { sf ->
                logger.info { "repaired ${sf.logFile}" }
            }
        } else {
            dataDir.brokenSegments(filter)
                .collect { sf -> logger.info { "Broken: ${sf.logFile}" } }
        }
    }
}
