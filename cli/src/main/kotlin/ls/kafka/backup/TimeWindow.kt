package ls.kafka.backup

data class TimeWindow(val from: Long?, val to: Long?) {
    fun contains(ts: Long): Boolean = (from == null || ts >= from) && (to == null || ts <= to)
}
