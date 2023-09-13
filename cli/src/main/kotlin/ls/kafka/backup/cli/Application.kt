package ls.kafka.backup.cli

import io.github.oshai.kotlinlogging.KotlinLogging
import ls.kafka.backup.cli.commands.BackupCli
import picocli.CommandLine

fun main(args: Array<String>) {

    val logger = KotlinLogging.logger {  }

    logger.error { "test" }

    logger.info { "real" }
    logger.debug { "ignore this" }


    CommandLine(BackupCli()).execute(*args)
}
