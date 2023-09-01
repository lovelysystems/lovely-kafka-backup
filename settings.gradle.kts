import java.net.URI

rootProject.name = "lovely-kafka-backup"
include("confluent-connect", "format")

pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        id("com.lovelysystems.gradle") version "1.12.0"
        id("com.github.johnrengelman.shadow") version "8.1.1"
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
            // Apache Kafka
            version("kafka", "3.5.1")
            library("kafka-connect-runtime", "org.apache.kafka", "connect-runtime").versionRef("kafka")

            // Confluent
            // NOTE: Last version that supports gzip compression for custom formatters
            // issue https://github.com/confluentinc/kafka-connect-storage-cloud/issues/545
            library("confluent-connect-s3", "io.confluent", "kafka-connect-s3").version("10.0.7")

            // Logging
            library("kotlin-logging-jvm", "io.github.oshai", "kotlin-logging-jvm").version("5.1.0")
        }

        create("testLibs") {
            // Kotest
            version("kotest", "5.6.2")
            library("kotest-runner-junit5", "io.kotest", "kotest-runner-junit5").versionRef("kotest")
            library("kotest-framework-api", "io.kotest", "kotest-framework-api-jvm").versionRef("kotest")
        }
    }
}
