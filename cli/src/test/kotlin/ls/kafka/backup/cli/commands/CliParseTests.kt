package ls.kafka.backup.cli.commands

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import picocli.CommandLine
import picocli.CommandLine.MissingParameterException

class CliParseTests : FreeSpec({

    val restore = Restore()
    val repair = Repair()
    val cli = CommandLine(BackupCli())
    cli.addSubcommand("restoreTest", restore)
    cli.addSubcommand("repairTest", repair)
    //using different name for subcommand because original command is added via annotations (needed because there are multiple subcommands)
    // but we want to add it programmatically here so that we have a reference to assert on, but it can't be added with the same name

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
                cli.parseArgs("restoreTest", "--bucket", "some", "-p", "topic")
                restore.bucket shouldBe "some"
                restore.prefix shouldBe "topic"
            }

            "for all arguments" {
                cli.parseArgs(
                    "restoreTest",
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

        "of repair " - {
            "should fail " - {
                "for repair without arguments" {
                    shouldThrowAny {
                        cli.parseArgs("repairTest")
                    }
                }

                "for missing parameter " - {
                    "bucket" {
                        shouldThrow<MissingParameterException> {
                            cli.parseArgs("repairTest", "path/to/datadir")
                        }
                    }

                    "data-directory" {
                        shouldThrow<MissingParameterException> {
                            cli.parseArgs("repairTest", "--bucket", "some")
                        }
                    }
                }
            }

            "should succeed " - {
                "for minimal arguments" {
                    cli.parseArgs(
                        "repairTest",
                        "--bucket",
                        "user-devices",
                        "--data-directory",
                        "path/to/data/directory"
                    )
                    repair.bucket shouldBe "user-devices"
                    repair.dataDirectory shouldBe "path/to/data/directory"
                    repair.skipBroken shouldBe false
                    repair.repair shouldBe false
                    repair.filter shouldBe "*"
                }

                "for all arguments" {
                    cli.parseArgs(
                        "repairTest",
                        "-rs",
                        "--filter",
                        "dir_prefix*",
                        "--s3Endpoint",
                        "http://somewhere.io:9000",
                        "--profile",
                        "unit-test-profile",
                        "--bucket",
                        "user-devices",
                        "--data-directory",
                        "path/to/data/directory"
                    )
                    repair.bucket shouldBe "user-devices"
                    repair.dataDirectory shouldBe "path/to/data/directory"
                    repair.s3Endpoint shouldBe "http://somewhere.io:9000"
                    repair.profile shouldBe "unit-test-profile"
                    repair.skipBroken shouldBe true
                    repair.repair shouldBe true
                    repair.filter shouldBe "dir_prefix*"
                }
            }
        }
    }
})
