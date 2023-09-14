package ls.kafka.backup.cli.commands

import picocli.CommandLine

@CommandLine.Command(name = "kbackup", subcommands = [Restore::class, Repair::class])
class KBackup

/**
 * Because this cli is bundled into the docker image used for backups and we want users to explicitly start the cli with the `kbackup` argument
 * the application command does nothing but delegates to a named subcommand.
 */
@CommandLine.Command(
    subcommands = [KBackup::class],
    customSynopsis = ["Usage: kbackup [restore|repair] [arguments...]"]
)
class ApplicationCommand