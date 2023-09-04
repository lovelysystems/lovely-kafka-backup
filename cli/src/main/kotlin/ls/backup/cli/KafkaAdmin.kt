package ls.backup.cli

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.KafkaFuture
import java.io.*
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

class KafkaAdmin(properties: Properties) : Closeable {

    private val client: AdminClient = AdminClient.create(properties)!!

    override fun close() {
        client.close()
    }

    private fun <T> get(future: KafkaFuture<T>): T {
        val res: T
        try {
            res = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            client.close(Duration.ofSeconds(2L))
            throw e
        }
        return res
    }

    companion object {
        private const val TIMEOUT_SECONDS = 5L
    }
}
