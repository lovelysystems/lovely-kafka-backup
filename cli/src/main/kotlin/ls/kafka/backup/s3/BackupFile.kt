package ls.kafka.backup.s3

data class BackupFile(val key: String) {
    val topic: String                    // corresponds to the Kafka topic from which the records were read
    val partition: Int        // Kafka partition number from which the records were read
    val startOffset: Long    // offset of the first record written to this file

    init {
        // key format is: <prefix>/../<topic>+<kafkaPartition>+<startOffset>.<format>
        key.split("/").last().substringBefore(".").split("+").let { nameParts ->
            topic = nameParts[0]
            partition = nameParts[1].toInt()
            startOffset = nameParts[2].toLong()
        }
    }
}
