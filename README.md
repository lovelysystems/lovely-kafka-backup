# lovely-kafka-backup

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/lovelysystems/lovely-kafka-backup/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/lovelysystems/lovely-kafka-backup/tree/master)

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

## Metrics

Metrics are exported using the [Prometheus JMX Exporter](https://github.com/prometheus/jmx_exporter) and is
configured to expose metrics in Prometheus format at `/metrics`.

The following environment variables can be used to configure the exporter:

* `METRICS_ENABLED`: Enable or disable the metrics exporter. Default: `true`
* `METRICS_PORT`: The port the metrics are exposed on. Default: `9876`

# Restore-CLI

To restore records from a backup run the program. The restore reads backed up records from s3 and appends them to the
target topics. Offsets of the records are not restored.

## Running

Using docker:

```bash                     
docker run --network host lovelysystems/lovely-kafka-backup:dev restore --bucket <s3-backup-bucket> --topicPattern <topicPattern>
```

NOTE: network host is required if kafka is also running locally in a container

or gradle:

```bash

./gradlew :cli:run restore --bucket <s3-backup-bucket> --topicPattern <topicPattern>

```

The above command restores all records for a given topic to the same topic name.

### All options:

| Option name      | Short option | Required                                    | Format              | Description                                                                                                                        |
|------------------|--------------|---------------------------------------------|---------------------|------------------------------------------------------------------------------------------------------------------------------------|
| bucket           | b            | always                                      | String              | Bucket in which the backup is stored                                                                                               |
| topicPattern     | p            | always                                      | Regex               | Pattern for topic names restored to restore                                                                                        |
| s3Endpoint       |              | If not restoring from AWS                   | Url                 | Endpoint for S3 backup storage                                                                                                     |
| bootstrapServers |              | If env `KAFKA_BOOTSTRAP_SERVERS` is not set | (list of) Urls      | Kafka cluster to restore the backup to                                                                                             |
| outputPrefix     | o            |                                             | String              | Records are restored to their original topic, if this is set they are restored to the topic with the prefix                        |
| profile          |              |                                             | String              | Profile to user for S3 access. If not set uses `AWS_PROFILE` environment variable or the default profile.                          |
| fromTs           |              |                                             | yyyy-MM-ddThh:mm:ss | Start time of records to restore, if not set records from earliest available are restored. NOTE: times are always treated as UTC   |
| toTs             |              |                                             | yyyy-MM-ddThh:mm:ss | End time of records to restore, if not set records to latest available are restored. NOTE: times are always treated as UTC         |

## S3 Config

S3 can be configured using the environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` or by setting 
a profile in the parameter `--profile`. Profile takes priority.

NOTE: configuration via profile is mostly useful for development and running the cli via `gradle :cli:run`. To use the it in docker
the config file from `~/.aws` would need to be mounted into the container.

### KafkaConfig

Additional configs for kafka can be set via environment variables prefixed with `KAFKA_`. If an argument is passed the
argument takes priority.
