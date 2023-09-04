package ls.backup.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli
import ls.backup.cli.commands.Restore

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser("")
    parser.subcommands(Restore())
    parser.parse(args)
}
