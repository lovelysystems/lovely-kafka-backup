package ls.kafka.io

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import ls.kafka.model.DumpRecord
import java.io.ByteArrayOutputStream
import java.io.DataInputStream

class RecordStreamWriterTest : StringSpec({

    "write a single record" {
        val out = ByteArrayOutputStream()
        val writer = RecordStreamWriter(out)
        val record = DumpRecord(1, 2, 1693562297, "key".toByteArray(), "value".toByteArray())

        writer.write(record)
        writer.flush()
        writer.close()

        val byteArray = out.toByteArray()
        byteArray.size shouldBe 4 + 8 + 8 + 4 + 3 + 4 + 5 + 4  // include last entry marker

        val inStream = DataInputStream(byteArray.inputStream())
        inStream.readInt() shouldBe 1
        inStream.readLong() shouldBe 2
        inStream.readLong() shouldBe 1693562297
        inStream.readInt() shouldBe 3
        inStream.readNBytes(3).toString(Charsets.UTF_8) shouldBe "key"
        inStream.readInt() shouldBe 5
        inStream.readNBytes(5).toString(Charsets.UTF_8) shouldBe "value"
    }
})
