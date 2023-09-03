# lovely-kafka-backup

This application is meant to be used as a backup tool for Kafka. It comes with connectors to read and write data from
Kafka to different storage systems and provides a CLI for manual backup and restore operations.

## Features

- Backup Kafka topics to any S3 compatible storage system (e.g. AWS S3, Minio, etc.)
- Multiple formats for backup files (e.g. Binary, Avro, JSON, etc.) supported

## Requirements

- Docker
- Kafka
- S3 compatible storage system (e.g. AWS S3, Minio, etc.)

## Kafka S3 Backup Connector Setup

This connector uses the [Kafka Connect S3 Connector](https://docs.confluent.io/kafka-connectors/s3-sink/current/overview.html)
to backup Kafka topics to S3. It uses the [Kafka Connect API](https://kafka.apache.org/documentation/#connect) to
stream data from Kafka.

The connector is created automatically when the application is started. See the [Configuration Properties](https://docs.confluent.io/kafka-connectors/s3-sink/current/overview.html#configuration-properties)
for a list of all available properties.

The connector can be configured using a configuration file. See `localdev` for an example configuration file.
