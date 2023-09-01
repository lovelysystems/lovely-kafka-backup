package ls.kafka.backup.cli

import ls.kafka.backup.cli.commands.BackupCli
import picocli.CommandLine

fun main(args: Array<String>) {
    CommandLine(BackupCli()).execute(*args)
}
