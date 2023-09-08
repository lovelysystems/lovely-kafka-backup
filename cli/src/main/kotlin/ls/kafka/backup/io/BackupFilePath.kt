package ls.kafka.backup.io

data class BackupFilePath(val name: String) {
    private val topic: String
    val partition: Int
    val startOffset: Long

    init {
        name.substringBefore(".").split("+").let { nameParts ->
            topic = nameParts[0]
            partition = nameParts[1].toInt()
            startOffset = nameParts[2].toLong()
        }
    }

    override fun toString(): String {
        return "BackupFilePath(name='$name')"
    }
}
