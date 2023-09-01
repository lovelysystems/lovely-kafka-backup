# confluent-connect

This subproject contains custom implementations (converters, formatters etc.) for connectors of Confluent that can
run in a Kafka Connect cluster. The connectors are packaged as a single JAR file that is copied to the `plugin.path`
directory of the `lovely-kafka-backup` docker image to make them available in the Kafka Connect worker.

## Available connectors

### Amazon S3 Sink Connector

The Amazon S3 sink connector exports records from Kafka topics to S3 objects in various formats. Aside of the built-in
formats (Avro, JSON, Parquet etc.) it adds support to export records as raw binary data with the ability to restore
them later without any schema information. Use the `"format.class": "ls.kafka.connect.storage.format.ByteArrayRecordFormat"`
in the connector configuration to use this export format for backup purposes. See the connector configuration used 
in the [localdev example connector config](../localdev/config/s3-config.json) for more details.
