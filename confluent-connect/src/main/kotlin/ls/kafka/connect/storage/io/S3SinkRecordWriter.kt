package ls.kafka.connect.storage.io

import io.confluent.connect.s3.storage.IORecordWriter
import io.confluent.connect.s3.storage.S3OutputStream
import io.github.oshai.kotlinlogging.KotlinLogging
import ls.kafka.io.RecordStreamWriter
import ls.kafka.model.DumpRecord
import org.apache.kafka.connect.sink.SinkRecord
import org.apache.kafka.connect.storage.Converter

/**
 * Allows to write a Kafka Connect [SinkRecord] to the given s3 stream in a binary format using the shared
 * [RecordStreamWriter]. Supports the allowed compression types of the Confluent S3 connector.
 */
class S3SinkRecordWriter(private val s3out: S3OutputStream, private val converter: Converter) : IORecordWriter {
    private val logger = KotlinLogging.logger {}
    private val s3outWrapper = s3out.wrapForCompression()
    private val writer = RecordStreamWriter(s3outWrapper)

    override fun write(record: SinkRecord) {
        val topic = record.topic()
        val data = converter.fromConnectData(topic, record.valueSchema(), record.value())
        val key = converter.fromConnectData(topic, record.keySchema(), record.key())
        val r = DumpRecord(record.kafkaPartition(), record.kafkaOffset(), record.timestamp(), key, data)
        logger.debug { "Sink record: $r" }
        writer.write(r)
    }

    override fun commit() {
        writer.flush()
        s3out.commit()
    }

    override fun close() {
        writer.close()
    }
}
