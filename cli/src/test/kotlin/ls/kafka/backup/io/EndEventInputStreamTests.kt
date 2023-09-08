package ls.kafka.backup.io

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import ls.kafka.io.RecordStreamWriter
import kotlin.io.path.*

class EndEventInputStreamTests: FreeSpec({

    "should trigger end event and have read the input stream" {
        val tempFile = createTempFile()
        tempFile.writeText("some text")
        tempFile.shouldExist()

        var foundEnd = false

        val stream = tempFile.inputStream().onEnd {
            foundEnd = true
        }

        String(stream.readAllBytes()) shouldBe "some text"
        foundEnd shouldBe true

        tempFile.deleteIfExists()
    }

    "should be able to delete the underlying file at end" {
        val tempFile = createTempFile()
        tempFile.writeText("fleeting text content")

        val stream = tempFile.inputStream().onEnd {
            tempFile.deleteIfExists() shouldBe true
        }
        String(stream.readAllBytes()) shouldBe "fleeting text content"

        tempFile.shouldNotExist()
    }

    "should work with buffered streams" {
        val tempFile = createTempFile()
        tempFile.writeText("fleeting text content")

        val stream = tempFile.inputStream().buffered().onEnd {
            tempFile.deleteIfExists() shouldBe true
        }
        String(stream.readAllBytes()) shouldBe "fleeting text content"

        tempFile.shouldNotExist()
    }

    "should work for read method " - {
        "read()" {
            val tempFile = createTempFile()
            tempFile.writeText("fleeting text content")

            val stream = tempFile.inputStream().buffered().onEnd {
                tempFile.deleteIfExists() shouldBe true
            }

            do {
                val value = stream.read()
            } while (value != -1)
            tempFile.shouldNotExist()
        }
        "readNBytes(len)" {
            val tempFile = createTempFile()
            val text = "fleeting text content"
            tempFile.writeText(text)
            tempFile.shouldExist()

            val stream = tempFile.inputStream().buffered().onEnd {
                tempFile.deleteIfExists()
            }
            stream.readNBytes(text.toByteArray().size+1)
            tempFile.shouldNotExist()
        }
        "readAllBytes()" {
            val tempFile = createTempFile()
            val text = "fleeting text content"
            tempFile.writeText(text)

            val stream = tempFile.inputStream().buffered().onEnd {
                tempFile.deleteIfExists() shouldBe true
            }
            stream.readAllBytes()
            tempFile.shouldNotExist()
        }
        "read(byteArray)" {
            val tempFile = createTempFile()
            val text = "fleeting text content"
            tempFile.writeText(text)

            val stream = tempFile.inputStream().buffered().onEnd {
                tempFile.deleteIfExists() shouldBe true
            }
            stream.read(ByteArray(1000))
            tempFile.shouldNotExist()
        }
        "read(byteArray, off, len)" {
            val tempFile = createTempFile()
            val text = "fleeting text content"
            tempFile.writeText(text)

            val stream = tempFile.inputStream().buffered().onEnd {
                tempFile.deleteIfExists() shouldBe true
            }
            stream.read(ByteArray(1000), 10, 900)
            tempFile.shouldNotExist()
        }
        "readNBytes(byteArray, off, len)" {
            val tempFile = createTempFile()
            tempFile.shouldExist()
            val text = "fleeting text content"
            tempFile.writeText(text)

            val stream = tempFile.inputStream().buffered().onEnd {
                tempFile.deleteIfExists()
            }
            stream.readNBytes(ByteArray(2000), 10, 1000)
            tempFile.shouldNotExist()
        }
    }

    "should work with large inputs".config(enabled = false) {
        val textSnippet = "repetitive text content that goes on for quite a bit"
        val tempFile = createTempFile()
        repeat(1_000_000) {
            tempFile.writeText(textSnippet)
        }


        val stream = tempFile.inputStream().buffered().onEnd {
            tempFile.deleteIfExists()
        }

        do {
            val arr = ByteArray(10000)
            val len = stream.read(arr)
        } while (len != -1)

        tempFile.shouldNotExist()
    }

})