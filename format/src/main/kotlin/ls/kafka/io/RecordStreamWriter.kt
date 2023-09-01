package ls.kafka.io

import ls.kafka.model.DumpRecord
import java.io.DataOutputStream

/**
 * Writes records to a stream in a binary format. The format is as follows:
 *
 * 1. 4 bytes: partition number
 * 2. 8 bytes: offset
 * 3. 8 bytes: timestamp
 * 4. 4 bytes: key length
 * 5. var length bytes: key
 * 6. 4 bytes: value length
 * 7. var length bytes: value
 */
class RecordStreamWriter(private val out: DataOutputStream) {

    /**
     * Writes a single record to the stream.
     */
    fun write(record: DumpRecord) {
        out.writeInt(record.partition)
        out.writeLong(record.offset)
        out.writeLong(record.ts)
        out.writeInt(record.key.size)
        out.write(record.key)
        out.writeInt(record.value.size)
        out.write(record.value)
    }

    /**
     * Writes a special marker to indicate that this is the last entry in the stream.
     */
    fun flush() {
        out.writeInt(-1)  // last entry marker
    }

    /**
     * Closes the underlying stream.
     */
    fun close() {
        out.close()
    }
}
