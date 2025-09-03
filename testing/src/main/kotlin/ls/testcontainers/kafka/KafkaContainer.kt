package ls.testcontainers.kafka

import org.testcontainers.containers.BindMode
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

fun kafkaContainer(
    image: String = "apache/kafka-native:3.9.1",
    volumePath: String? = null,
    partitions: Int = 1,
): KafkaContainer = KafkaContainer(DockerImageName.parse(image)).apply {
    withEnv(
        mapOf(
            "KAFKA_NUM_PARTITIONS" to "$partitions",
            "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" to "1",
            "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "0",
            "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR" to "1",
            "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "1",
            "KAFKA_LOG_RETENTION_MS" to "-1",
            "KAFKA_LOG_RETENTION_BYTES" to "-1",
            "KAFKA_MESSAGE_TIMESTAMP_TYPE" to "CreateTime",
            "KAFKA_DELETE_TOPIC_ENABLE" to "true",
            "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "1",
            "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR" to "1",
            "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" to "1",
            "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "0",
        )
    )
    volumePath?.let { path ->
        withFileSystemBind(path, "/tmp/kafka-logs", BindMode.READ_WRITE)
    }
}
