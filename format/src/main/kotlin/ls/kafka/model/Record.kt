package ls.kafka.model

data class Record(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val ts: Long,
    val key: ByteArray,
    val value: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Record

        if (topic != other.topic) return false
        if (partition != other.partition) return false
        if (offset != other.offset) return false
        if (ts != other.ts) return false
        if (!key.contentEquals(other.key)) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + partition
        result = 31 * result + offset.hashCode()
        result = 31 * result + ts.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}