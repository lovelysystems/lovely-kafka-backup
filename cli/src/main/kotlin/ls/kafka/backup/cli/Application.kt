package ls.kafka.backup.cli

import ls.kafka.backup.cli.commands.BackupCli
import ls.kafka.backup.cli.commands.Restore
import picocli.CommandLine

suspend fun main(args: Array<String>) {
    val restore = Restore()
    val cli = CommandLine(BackupCli())
    cli.addSubcommand("restore", restore)
    cli.parseArgs(*args)
    restore.execute()
}
