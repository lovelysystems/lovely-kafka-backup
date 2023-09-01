# Local Development Environment Setup

## Prerequisites

- [Docker](https://docs.docker.com/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [kcat CLI](https://github.com/edenhill/kcat)

## Setup

1. Check and adapt the S3 sink [configuration](./config/s3-config.json) as needed.

2. Start the local Kafka environment and wait for Kafka to be ready (this may take few seconds only):

    ```bash
    docker-compose up -d
    ```

3. Create the sample topic `loc_user_devices` with data:

    ```bash
   ./create-sample-records.sh
    ```

4. Verify that the topic was created and contains the sample data using the `kcat` CLI or check the Kafka UI
   at http://localhost:5000:

    ```bash
    kcat -b localhost:9092 -t loc_user_devices -C
    ``` 

5. The S3 sink connector should be running and writing data to the `user-devices` bucket in S3 (Minio). Verify that
   the data is being written by checking the bucket in the Minio UI at http://localhost:9001.
   (be aware that this may take a few seconds for sink connector to start writing data)

## Restart the s3 sink

Since the connector uses Kafka as offset storage, it will resume from the last offset when restarted. You will
need ot delete the topic and/or the consumer group to start from the beginning again (use kafka UI for this).
