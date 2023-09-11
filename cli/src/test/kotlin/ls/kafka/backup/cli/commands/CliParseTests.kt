package ls.kafka.backup.cli.commands

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import picocli.CommandLine
import picocli.CommandLine.MissingParameterException

class CliParseTests : FreeSpec({

    val restore = Restore()
    val cli = CommandLine(BackupCli())
    cli.addSubcommand("restore", restore)

    "parsing " - {
        "should fail " - {
            "for empty arguments" {
                shouldThrowAny {
                    cli.parseArgs("")
                }
            }

            "for restore without arguments" {
                shouldThrowAny {
                    cli.parseArgs("restore")
                }
            }

            "for missing parameter " - {
                "bucket" {
                    shouldThrow<MissingParameterException> {
                        cli.parseArgs("restore", "-p", "sometopicpattern")
                    }
                }
            }
        }

        "should succeed" - {
            "for minimal arguments" {
                cli.parseArgs("restore", "--bucket", "some", "-p", "topic")
                restore.bucket shouldBe "some"
                restore.prefix shouldBe "topic"
            }

            "for all arguments" {
                cli.parseArgs(
                    "restore",
                    "--bucket",
                    "someBucket",
                    "--s3Endpoint",
                    "http://localhost:9000",
                    "--profile",
                    "non-default",
                    "--bootstrapServers",
                    "localhost:9092",
                    "--prefix",
                    "topic-name",
                    "--from",
                    "50",
                    "--to",
                    "100",
                    "--topic",
                    "restored_topic-name"
                )
                restore.bucket shouldBe "someBucket"
                restore.s3Endpoint shouldBe "http://localhost:9000"
                restore.profile shouldBe "non-default"
                restore.bootstrapServers shouldBe "localhost:9092"
                restore.fromOffset shouldBe 50L
                restore.toOffset shouldBe 100L
                restore.prefix shouldBe "topic-name"
                restore.targetTopic shouldBe "restored_topic-name"
            }
        }
    }
})
