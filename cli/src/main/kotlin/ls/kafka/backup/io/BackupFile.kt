package ls.kafka.backup.io

import ls.kafka.io.RecordStreamReader
import ls.kafka.model.DumpRecord
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.InputStream

class BackupFile(name: String, inputStream: InputStream) {

    private val compressed = name.endsWith(".gz")

    val topic: String
    private val partition: Int
    private val startOffset: Long

    private val stream: InputStream

    init {
        name.split("/").last().substringBefore(".").split("+").let { nameParts ->
            topic = nameParts[0]
            partition = nameParts[1].toInt()
            startOffset = nameParts[2].toLong()
        }
        stream = if (compressed) {
            GzipCompressorInputStream(inputStream)
        } else {
            inputStream
        }
    }

    fun records(): List<DumpRecord> = RecordStreamReader(stream).readAll()
}
