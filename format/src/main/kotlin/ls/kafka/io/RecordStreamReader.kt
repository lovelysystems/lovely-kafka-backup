package ls.kafka.io

import ls.kafka.model.DumpRecord
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

class RecordStreamReader(input: InputStream) {
    private val dis = DataInputStream(input)

    fun readAll(): List<DumpRecord> {
        val records = mutableListOf<DumpRecord>()
        while (true) {
            val curr = read()
            if (curr == null) {
                break
            } else {
                records.add(curr)
            }
        }
        return records
    }

    /**
     * Reads a single record from the stream
     */
    fun read(): DumpRecord? {
        val partition = try {
            dis.readInt()
        } catch (e: EOFException) {
            -1
        }
        if (partition == -1) {
            return null
        }
        val offset = dis.readLong()
        val ts = dis.readLong()
        val keySize = dis.readInt()
        val key = ByteArray(keySize)
        dis.read(key)
        val valueSize = dis.readInt()
        val value = ByteArray(valueSize)
        dis.read(value)

        return DumpRecord(partition, offset, ts, key, value)
    }
}
