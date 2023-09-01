#!/bin/bash

# Kafka Connect configuration (defaults)
export CONNECT_PLUGIN_PATH="/usr/share/java,/usr/share/confluent-hub-components"
export CONNECT_BOOTSTRAP_SERVERS=${CONNECT_BOOTSTRAP_SERVERS:-"localhost:9092"}
export CONNECT_GROUP_ID=${CONNECT_GROUP_ID:-"kafka-connect"}
export CONNECT_CONFIG_STORAGE_TOPIC=${CONNECT_CONFIG_STORAGE_TOPIC:-"connect-config"}
export CONNECT_OFFSET_STORAGE_TOPIC=${CONNECT_OFFSET_STORAGE_TOPIC:-"connect-offsets"}
export CONNECT_STATUS_STORAGE_TOPIC=${CONNECT_STATUS_STORAGE_TOPIC:-"connect-status"}
export CONNECT_KEY_CONVERTER=${CONNECT_KEY_CONVERTER:-"org.apache.kafka.connect.json.JsonConverter"}
export CONNECT_VALUE_CONVERTER=${CONNECT_VALUE_CONVERTER:-"org.apache.kafka.connect.json.JsonConverter"}
export CONNECT_REST_ADVERTISED_HOST_NAME=${CONNECT_REST_ADVERTISED_HOST_NAME:-"localhost"}
export CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR=${CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR:-"1"}
export CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR=${CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR:-"1"}
export CONNECT_STATUS_STORAGE_REPLICATION_FACTOR=${CONNECT_STATUS_STORAGE_REPLICATION_FACTOR:-"1"}

# s3 connector configuration
export S3_CONNECTOR_NAME=${S3_CONNECTOR_NAME:-"s3-sink-connector"}
export S3_CONNECTOR_CONFIG_FILE=${S3_CONNECTOR_CONFIG_FILE:-"/etc/kafka-connect/s3-config.json"}

export REST_URL="http://$CONNECT_REST_ADVERTISED_HOST_NAME:8083/connectors/$S3_CONNECTOR_NAME"

# Start Kafka Connect in the background
/etc/confluent/docker/run &

# Wait for Kafka Connect to start
echo "Waiting for Kafka Connect to start..."
while true; do
  curl_status=$(curl -s -o /dev/null -w %{http_code} "http://$CONNECT_REST_ADVERTISED_HOST_NAME:8083/connectors")
  if [ "$curl_status" = "200" ]; then
    echo "Kafka Connect has started."
    break
  fi
  sleep 1
done

# Create the S3 sink connector via REST
echo "Creating the S3 sink connector..."
curl -XPUT -H "Content-Type: application/json" -H "Accept: application/json" -d @${S3_CONNECTOR_CONFIG_FILE} "$REST_URL/config"

# Keep the script running to keep the container alive
tail -f /dev/null
