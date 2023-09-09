package ls.kafka.backup.io

import ls.kafka.io.RecordStreamReader
import ls.kafka.model.DumpRecord
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.InputStream

class BackupFileContent(name: String, inputStream: InputStream): BackupFileDescription(name) {

    private val compressed = name.endsWith(".gz")

    private val stream: InputStream

    init {
        stream = if (compressed) {
            GzipCompressorInputStream(inputStream)
        } else {
            inputStream
        }
    }

    fun records(): List<DumpRecord> = RecordStreamReader(stream).readAll()
}
