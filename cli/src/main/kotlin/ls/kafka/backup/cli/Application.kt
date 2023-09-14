package ls.kafka.backup.cli

import ls.kafka.backup.cli.commands.ApplicationCommand
import picocli.CommandLine

fun main(args: Array<String>) {
    CommandLine(ApplicationCommand()).execute(*args)
}
