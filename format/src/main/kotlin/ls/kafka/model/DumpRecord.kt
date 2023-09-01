package ls.kafka.model

data class DumpRecord(
    val partition: Int,
    val offset: Long,
    val ts: Long,
    val key: ByteArray,
    val value: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DumpRecord

        if (partition != other.partition) return false
        if (offset != other.offset) return false
        if (ts != other.ts) return false
        if (!key.contentEquals(other.key)) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = partition
        result = 31 * result + offset.hashCode()
        result = 31 * result + ts.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }

    override fun toString(): String = "DumpRecord(partition=$partition, offset=$offset, ts=$ts)"
}
