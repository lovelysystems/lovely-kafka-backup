package ls.backup.cli

import ls.backup.cli.commands.Restore
import picocli.CommandLine

suspend fun main(args: Array<String>) {
    val restore = Restore()
    CommandLine(restore).parseArgs(*args);
    restore.execute()
}
