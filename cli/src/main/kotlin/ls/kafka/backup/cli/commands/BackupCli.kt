package ls.kafka.backup.cli.commands

import picocli.CommandLine

@CommandLine.Command(subcommands = [Restore::class, Repair::class])
class BackupCli
