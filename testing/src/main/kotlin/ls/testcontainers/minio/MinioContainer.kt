package ls.testcontainers.minio

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import java.time.Duration

/**
 * Controls and launches a MINIO container
 */
class MinioContainer(
    credentials: MinioCredentials = MinioCredentials(
        System.getenv("AWS_ACCESS_KEY_ID"),
        System.getenv("AWS_SECRET_ACCESS_KEY")
    ),
    image: String = "minio/minio:latest"
) : GenericContainer<MinioContainer>(image) {

    init {
        addExposedPort(DEFAULT_PORT)//there is no method to add a random port, but the container doesn't listen on the specified port number anyway
        withEnv(MINIO_ROOT_USER, credentials.accessKey)
        withEnv(MINIO_ROOT_PASSWORD, credentials.secretKey)
        withCommand("server", DEFAULT_STORAGE_DIRECTORY)
        setWaitStrategy(
            HttpWaitStrategy()
                .forPort(DEFAULT_PORT)
                .forPath(HEALTH_ENDPOINT)
                .withStartupTimeout(Duration.ofMinutes(2))
        )
    }

    /**
     * Returns the host address of the container
     */
    fun getHostAddress(): String {
        return "http://$host:$firstMappedPort"
    }

    /**
     * Creates a bucket with the given name
     */
    fun createBucket(bucketName: String) {
        execInContainer("mkdir", "-p", "/data/$bucketName")
    }

    /**
     * Lists all files in the given bucket
     */
    fun listFiles(bucketName: String): List<String> {
        val result = execInContainer("ls", "/data/$bucketName")
        val files = result.stdout.split("\n")
        // remove empty line at the end
        return files.subList(0, files.size - 1)
    }

    /**
     * Removes all files from the given bucket
     */
    fun clearFiles(bucketName: String) {
        // run remove command in a shell to be able to use wildcards
        execInContainer("sh", "-c", "rm -rf /data/$bucketName/*")
    }

    companion object {
        private const val DEFAULT_PORT = 9000
        private const val MINIO_ROOT_USER = "MINIO_ROOT_USER"
        private const val MINIO_ROOT_PASSWORD = "MINIO_ROOT_PASSWORD"
        private const val DEFAULT_STORAGE_DIRECTORY = "/data"
        private const val HEALTH_ENDPOINT = "/minio/health/ready"
    }
}
