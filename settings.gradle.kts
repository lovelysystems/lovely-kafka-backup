import java.net.URI

rootProject.name = "lovely-kafka-backup"
include("confluent-connect", "format", "cli", "testing")

pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        id("com.lovelysystems.gradle") version "1.12.0"
        id("com.github.johnrengelman.shadow") version "8.1.1"
        id("io.gitlab.arturbosch.detekt") version "1.23.1"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = URI("https://packages.confluent.io/maven")
        }
    }

    versionCatalogs {
        create("libs") {
            version("kotlinx-coroutines", "1.7.3")
            library(
                "kotlinx-coroutines-core",
                "org.jetbrains.kotlinx",
                "kotlinx-coroutines-core"
            ).version("kotlinx-coroutines")

            // Apache Kafka
            version("kafka", "3.5.1")
            library("kafka-connect-runtime", "org.apache.kafka", "connect-runtime").versionRef("kafka")
            library("kafka-clients", "org.apache.kafka", "kafka-clients").versionRef("kafka")

            // Confluent
            // NOTE: Last version that supports gzip compression for custom formatters
            // issue https://github.com/confluentinc/kafka-connect-storage-cloud/issues/545
            library("confluent-connect-s3", "io.confluent", "kafka-connect-s3").version("10.0.7")

            // compression
            library("apache-commons-compress", "org.apache.commons", "commons-compress").version("1.23.0")

            // S3 client
            library("s3-kotlin-client", "aws.sdk.kotlin", "s3").version("0.32.1-beta")

            // CLI
            library("picocli", "info.picocli", "picocli").version("4.7.5")

            // Logging
            version("slf4j", "2.0.9")
            library("kotlin-logging-jvm", "io.github.oshai", "kotlin-logging-jvm").version("5.1.0")
            library("logger-nothing", "org.slf4j", "slf4j-nop").versionRef("slf4j")
        }

        create("testLibs") {
            // Kotest
            version("kotest", "5.6.2")
            library("kotest-runner-junit5", "io.kotest", "kotest-runner-junit5").versionRef("kotest")
            library("kotest-assertions", "io.kotest", "kotest-assertions-core").versionRef("kotest")
            library(
                "kotest-extensions-testcontainers",
                "io.kotest.extensions",
                "kotest-extensions-testcontainers"
            ).version("2.0.2")

            library("mockk", "io.mockk", "mockk").version("1.13.7")

            // Testcontainers
            version("testcontainers", "1.19.0")
            library("testcontainers", "org.testcontainers", "testcontainers").versionRef("testcontainers")
        }
    }
}
