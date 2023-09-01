#!/bin/bash

# Set the Kafka topic name
TOPIC_NAME="loc_user_devices"
KAFKA_CONTAINER_NAME="localdev-kafka"
BROKER_ADDRESS="localhost:9092"

# Create the Kafka topic if it doesn't exist (you may need to adjust this command based on your Kafka setup)
docker exec -it "$KAFKA_CONTAINER_NAME" \
kafka-topics.sh --bootstrap-server localhost:9092 --create --replication-factor 1 --partitions 1 --topic $TOPIC_NAME

NAMES=("Alice" "Bob" "Charlie" "David" "Eve" "Frank" "Grace" "Hannah" "Ivan" "Julia")
DEVICES=("iPhone" "Android" "Windows" "Mac" "Linux" "FreeBSD")

# Function to generate random values
function generate_random_json_value() {
    name="${NAMES[$((RANDOM%${#NAMES[@]}))]}"
    device="${DEVICES[$((RANDOM%${#DEVICES[@]}))]}"
    echo "{\"name\": \"$name\", \"device\": \"$device\"}"
}

# Produce 100 sample CSV records to the Kafka topic
for ((i=1; i<=100; i++))
do
    profile_id=$((RANDOM%1000))
    device_id=$((RANDOM%1000))
    key="$profile_id/$device_id"
    value=$(generate_random_json_value)

    # Use 'kcat' to produce the message to the Kafka topic
    echo "$key:$value" | kcat -b "$BROKER_ADDRESS" -t "$TOPIC_NAME" -P -K:
done

echo "Sample records produced successfully!"
