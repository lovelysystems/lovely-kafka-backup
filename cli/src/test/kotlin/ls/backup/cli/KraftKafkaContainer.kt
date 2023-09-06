package ls.backup.cli

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket

fun findFreePort() = ServerSocket(0).use { it.localPort }
class KraftKafkaContainer(
    val hostPort: Int = findFreePort()
    //Cant rely on Testcontainer mapping because ADVERTISED_LISTENERS
    // need to be configured with an address that is reachable by the client, if relying on Testcontainer
    // mapping we won't know the outward port until the container has started
) : GenericContainer<KraftKafkaContainer>(DockerImageName.parse("bitnami/kafka:3.4.0")) {

    val kafkaInternalPort = 9092

    val bootstrapServers
        get() = "PLAINTEXT://${this.host}:$hostPort"

    init {
        val envs = mapOf(
            "KAFKA_BROKER_ID" to "1",
            "KAFKA_CFG_NODE_ID" to "1",
            "KAFKA_ENABLE_KRAFT" to "yes",
            "ALLOW_PLAINTEXT_LISTENER" to "yes",
            "KAFKA_CFG_PROCESS_ROLES" to "broker,controller",
            "KAFKA_CFG_CONTROLLER_QUORUM_VOTERS" to "1@127.0.0.1:9093",
            "KAFKA_CFG_CONTROLLER_LISTENER_NAMES" to "CONTROLLER", //this value must be listed in KAFKA_LISTENERS
            "KAFKA_LISTENERS" to "PLAINTEXT://:$kafkaInternalPort,CONTROLLER://:9093", //same as variable KAFKA_CFG_LISTENERS, either works but one has to be defined
            "KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP" to "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT",
            "KAFKA_CFG_ADVERTISED_LISTENERS" to "PLAINTEXT://127.0.0.1:$hostPort",
            "KAFKA_CFG_INTER_BROKER_LISTENER_NAME" to "PLAINTEXT",

            "KAFKA_CFG_OFFSETS_TOPIC_REPLICATION_FACTOR" to "1",
            "KAFKA_CFG_GROUP_INITIAL_REBALANCE_DELAY_MS" to "0",
            "KAFKA_CFG_TRANSACTION_STATE_LOG_MIN_ISR" to "1",
            "KAFKA_CFG_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "1",
            "KAFKA_CFG_LOG_RETENTION_MS" to "-1",
            "KAFKA_CFG_LOG_RETENTION_BYTES" to "-1",
            "KAFKA_CFG_MESSAGE_TIMESTAMP_TYPE" to "CreateTime",
            "KAFKA_CFG_DELETE_TOPIC_ENABLE" to "true",
            "KAFKA_CFG_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "1",
            "KAFKA_CFG_TRANSACTION_STATE_LOG_MIN_ISR" to "1",
            "KAFKA_CFG_OFFSETS_TOPIC_REPLICATION_FACTOR" to "1",
            "KAFKA_CFG_GROUP_INITIAL_REBALANCE_DELAY_MS" to "0",

            "KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE" to "true",
        )
        withEnv(envs)
        addFixedExposedPort(hostPort, kafkaInternalPort)
    }
}
