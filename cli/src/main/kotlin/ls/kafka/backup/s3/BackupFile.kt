package ls.kafka.backup.s3

import ls.kafka.model.DumpRecord

/**
 * Represents a single backup file in S3.
 */
data class BackupFile(
    val key: String,                // S3 object key
    val records: List<DumpRecord>   // records in the file
) {
    val topic: String       // corresponds to the Kafka topic from which the records were read
    val partition: Int      // Kafka partition number from which the records were read
    val startOffset: Long   // offset of the first record written to this file

    init {
        // key format is: <prefix>/../<topic>+<kafkaPartition>+<startOffset>.<format>
        key.split("/").last().substringBefore(".").split("+").let { nameParts ->
            topic = nameParts[0]
            partition = nameParts[1].toInt()
            startOffset = nameParts[2].toLong()
        }
    }

    override fun toString(): String {
        return "BackupFilePath(name='$key', topic='$topic', partition=$partition, startOffset=$startOffset, recordSize=${records.size})"
    }
}
