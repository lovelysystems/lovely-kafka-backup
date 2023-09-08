package ls.kafka.backup.io

import java.io.FilterInputStream
import java.io.InputStream

fun InputStream.onEnd(block: () -> Unit) = EndEventInputStream(this, block)

class EndEventInputStream(input: InputStream, private val onEnd: () -> Unit): FilterInputStream(input) {

    override fun read(): Int {
        val value = `in`.read()
        if (value == -1) {
            onEnd()
        }
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val readCount = `in`.read(b, off, len)
        if (readCount < len) {
            onEnd()
        }
        return readCount
    }

    override fun readAllBytes(): ByteArray {
        val all = `in`.readAllBytes()
        onEnd()
        return all
    }

    override fun readNBytes(b: ByteArray, off: Int, len: Int): Int {
        var n = 0
        while (n < len) {
            val count = read(b, off + n, len - n)
            if (count < 0)  {
                onEnd()
                break
            }
            n += count
        }
        return n
    }
}