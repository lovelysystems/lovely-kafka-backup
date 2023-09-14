package ls.kafka.backup.cli.commands

import picocli.CommandLine

@CommandLine.Command(name = "cli", subcommands = [Restore::class, Repair::class])
class BackupCli

/**
 * Because this cli is bundled into the docker image used for backups and we want users to explicitly start the cli with the `cli` argument
 * the application command does nothing but delegates to a named subcommand.
 */
@CommandLine.Command(
    subcommands = [BackupCli::class],
    customSynopsis = ["Usage: cli [restore|repair] [arguments...]"]
)
class ApplicationCommand