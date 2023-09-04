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

# Restore-CLI

To restore records from a backup run the program 

```bash

./gradlew :cli:run restore --bucket <s3-backup-bucket> --pattern <topicPattern>

```

The above command restores all records for a given topic to the same topic name.

### All options:
| Option name        | Short option | Required                                    | Format              | Description                                                                                                                      |
|--------------------|--------------|---------------------------------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------|
| bucket             | b            | always                                      | String              | Bucket in which the backup is stored                                                                                             |
| s3Endpoint         |              | If not restoring from AWS                   | Url                 | Endpoint for S3 backup storage                                                                                                   |
| bootstrapServers   |              | If env `KAFKA_BOOTSTRAP_SERVERS` is not set | (list of) Urls      | Kafka cluster to restore the backup to                                                                                           |
| awsAccessKeyId     |              | If env `AWS_ACCESS_KEY_ID` is not set       | String              | Access key id for s3                                                                                                             |
| awsSecretAccessKey |              | If env `AWS_SECRET_ACCESS_KEY` is not set   | String              | Secret access key for s3                                                                                                         |
| start              | s            |                                             | yyyy-MM-ddThh:mm:ss | Start time of records to restore, if not set records from earliest available are restored. NOTE: times are always treated as UTC |
| end                | e            |                                             | yyyy-MM-ddThh:mm:ss | End time of records to restore, if not set records to latest available are restored. NOTE: times are always treated as UTC       |
| pattern            | p            |                                             | Regex               | Pattern for topic names restored. If default all topics in bucket are restored.                                                  |
| outputPrefix       | o            |                                             | String              | Records are restored to their original topic, if this is set they are restored to the topic with the prefix                      |

### KafkaConfig

Additional configs for kafka can be set via environment variables prefixed with `KAFKA_`. If an argument is passed the argument takes priority.
