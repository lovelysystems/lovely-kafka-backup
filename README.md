# lovely-kafka-backup

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/lovelysystems/lovely-kafka-backup/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/lovelysystems/lovely-kafka-backup/tree/master)

This application is meant to be used as a backup tool for Kafka. It comes with connectors to read and write data from
Kafka to different storage systems and provides a CLI for manual backup and restore operations.

## Features

- Backup Kafka topics to any S3 compatible storage system (e.g. AWS S3, Minio, etc.)
- Multiple formats for backup files (e.g. Binary, Avro, JSON, etc.) supported
- Backup and restore Kafka topics using the `kbackup` cli
- Repair corrupted Kafka log files using the `kbackup` cli
- Prometheus metrics for Kafka S3 Backup Connector

## Requirements

- Docker
- Kafka
- S3 compatible storage system (e.g. AWS S3, Minio, etc.)

## Kafka S3 Backup Connector Setup

By default (no CMD or ENTRYPOINT defined) the container starts the Kafka S3 Backup Connector. This connector uses the
[Kafka Connect S3 Connector](https://docs.confluent.io/kafka-connectors/s3-sink/current/overview.html) to backup Kafka
topics to S3. It uses the [Kafka Connect API](https://kafka.apache.org/documentation/#connect) to stream data from
Kafka.

The connector is created automatically after the Kafka-Connect worker has started. See the [Configuration Properties](https://docs.confluent.io/kafka-connectors/s3-sink/current/overview.html#configuration-properties)
for a list of all available properties.

The connector can be configured using a configuration file. See `localdev` for an example configuration file.

## Metrics

Metrics are exported using the [Prometheus JMX Exporter](https://github.com/prometheus/jmx_exporter) and is
configured to expose metrics in Prometheus format at `/metrics`.

The following environment variables can be used to configure the exporter:

* `METRICS_ENABLED`: Enable or disable the metrics exporter. Default: `true`
* `METRICS_PORT`: The port the metrics are exposed on. Default: `9876`


# Kbackup CLI

The docker image also contains a CLI for manual restore operations. The CLI can be run using the `kbackup`
command. `kbackup` generally assumes that topics it writes to either exist or are auto created. It doesn't create any
topics.

## Usage

The cli can be run using gradle:

```bash
./gradlew :cli:run --args="kbackup <subcommand> <args>..."
```

or on container startup with docker:

```bash                     
docker run --network host lovelysystems/lovely-kafka-backup:dev kbackup <subcommand> <args>...
```
NOTE: network host is required if kafka is also running locally in a container

or kubectl:

```bash
kubectl run my-cli-container --rm -i --image lovelysystems/lovely-kafka-backup:dev "kbackup <subcommand> <args>..."
```

or within a standalone container:

```bash
docker run --rm -it --entrypoint /bin/bash lovelysystems/lovely-kafka-backup:dev
kbackup <subcommand>
```

or from within a running container:

```bash
$ docker exec -it <container> /bin/bash
kbackup <subcommand>
```

## Subcommands

### Restore

To restore records from a backup run the program. The restore reads backed up records from s3 and appends them to the
target topics. Offsets of the records are not restored.

#### Demo call

```bash
kbackup restore --bucket user-devices --s3Endpoint http://localhost:9000 --bootstrapServers localhost:9092
```

This command restores all the backed up records in the bucket `user-devices` on S3 hosted at `http://localhost:9000` to their original topics.

#### All options:

| Option name      | Short option | Required                                    | Format         | Description                                                                                                  |
|------------------|--------------|---------------------------------------------|----------------|--------------------------------------------------------------------------------------------------------------|
| bucket           | b            | always                                      | String         | Bucket in which the backup is stored                                                                         |
| prefix           | p            |                                             | String         | Limit S3 objects that begin with the specified prefix.                                                       |
| key              | k            |                                             | Regex          | Allows to filter S3 objects by the specified key pattern.                                                    |
| partition        |              |                                             | Number         | Partition of the source topics to restore.                                                                   |
| from             |              |                                             | Number         | Start Kafka offset of objects to restore. If not set, records from earliest available offset are restored.   |
| to               |              |                                             | Number         | End Kafka offset of records to restore (exclusive). If not set, records to latest available are restored.    |
| s3Endpoint       |              | If not restoring from AWS                   | Url            | Endpoint for S3 backup storage                                                                               |
| bootstrapServers |              | If env `KAFKA_BOOTSTRAP_SERVERS` is not set | (list of) Urls | Kafka cluster to restore the backup to                                                                       |
| topic            | t            |                                             | String         | The target topic where records are restored to. Otherwise topics get restored into their original topic name |
| profile          |              |                                             | String         | Profile to user for S3 access. If not set uses `AWS_PROFILE` environment variable or the default profile.    |

#### S3 Config

S3 can be configured using the environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` or by setting
a profile in the parameter `--profile`. Profile takes priority.

NOTE: configuration via profile is mostly useful for development and running the cli via `gradle :cli:run`. To use the
it in docker
the config file from `~/.aws` would need to be mounted into the container.

### KafkaConfig

Additional configs for kafka can be set via environment variables prefixed with `KAFKA_`. If an argument is passed the
argument takes priority.

### Repair

Repairs corrupted records from with records from backup:

#### Demo call

```bash                     
kbackup repair --bucket s3-backup --data-directory kafka-data
```

This calls checks the kafka data in `kafka-data` and repairs them with backed up records in `s3-backup` if there are any corrupted.

#### All options:
| Option name    | Short | Required  | Description                                                       |
|----------------|-------|-----------|-------------------------------------------------------------------|
| bucket         | b     | always    | Backup bucket to repair from                                      |
| data-directory |       | always    | Data directory to which contains the kafka data                   |
| filter         | f     |           | glob pattern to filter log directories to repair. Defaults to all |
| skipBroken     | s     |           | Flag. If set records which aren't in the backup are skipped       |
| repair         | r     |           | Flag. If set the files are repaired otherwise just listed         |
| s3Endpoint     |       |           | Url to S3. If not given defaults to AWS-S3                        |
| region         |       |           | The region to use for s3 access                                   |
| profile        |       |           | The profile to use for s3 access                                  |


#### S3 Config

S3 can be configured using the environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` or by setting 
a profile in the parameter `--profile`. Profile takes priority.

NOTE: configuration via profile is mostly useful for development and running the cli via `gradle :cli:run`. To use the it in docker
the config file from `~/.aws` would need to be mounted into the container.

## Run Tests

Tests can be run using Gradle:

```bash
./gradlew test
```

## Publish a new version

Use Gradle to build and publish a new version of the docker image. The version is read from the `CHANGES.md` file.

```bash
./gradlew createTag
./gradlew pushDockerImage
```

Publish the `lovely-kafka-format` library to Github Packages:

```bash
./gradlew publish
```
