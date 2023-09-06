package ls.backup.cli.commands

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import picocli.CommandLine
import picocli.CommandLine.MissingParameterException
import java.time.format.DateTimeParseException

class CliParseTests : FreeSpec({

    val restore = Restore()
    val cli = CommandLine(restore)

    "parsing " - {
        "should fail " - {
            "for empty arguments" {
                shouldThrowAny {
                    cli.parseArgs("")
                }
            }

            "for missing parameter " - {
                "bucket" {
                    shouldThrow<MissingParameterException> {
                        cli.parseArgs("-p", "sometopicpattern")
                    }
                }

                "pattern" {
                    shouldThrow<MissingParameterException> {
                        cli.parseArgs("--bucket", "backup-bucket")
                    }
                }
            }

            "for invalid parameter " - {
                "fromTs" {
                    shouldThrow<DateTimeParseException> {
                        cli.parseArgs("--bucket", "some", "-p", "topic", "--fromTs", "notaDateTime")
                        restore.execute()
                    }
                }

                "toTs" {
                    shouldThrow<DateTimeParseException> {
                        cli.parseArgs("--bucket", "some", "-p", "topic", "--toTs", "notaDateTime")
                        restore.execute()
                    }
                }
            }
        }

        "should succeed" - {
            "for minimal arguments" {
                cli.parseArgs("--bucket", "some", "-p", "topic")
                restore.bucket shouldBe "some"
                restore.topicPattern shouldBe "topic"
            }

            "for all arguments" {
                cli.parseArgs(
                    "--bucket",
                    "someBucket",
                    "--s3Endpoint",
                    "http://localhost:9000",
                    "--profile",
                    "non-default",
                    "--bootstrapServers",
                    "localhost:9092",
                    "--fromTs",
                    "1912-06-23T12:34:00",
                    "--toTs",
                    "2000-09-06T21:43:00",
                    "--topicPattern",
                    "topic-name",
                    "--outputPrefix",
                    "restored"
                )
                restore.bucket shouldBe "someBucket"
                restore.s3Endpoint shouldBe "http://localhost:9000"
                restore.profile shouldBe "non-default"
                restore.bootstrapServers shouldBe "localhost:9092"
                restore.fromTs shouldBe "1912-06-23T12:34:00"
                restore.toTs shouldBe "2000-09-06T21:43:00"
                restore.topicPattern shouldBe "topic-name"
                restore.outputPrefix shouldBe "restored"

                shouldNotThrowAny {
                    parseDateInput(restore.fromTs!!)
                    parseDateInput(restore.toTs!!)
                }
            }
        }
    }
})
