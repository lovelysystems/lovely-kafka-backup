package ls.kafka.connect.storage

import io.confluent.connect.s3.storage.IORecordWriter
import io.confluent.connect.s3.storage.S3OutputStream
import io.github.oshai.kotlinlogging.KotlinLogging
import ls.kafka.io.RecordStreamWriter
import ls.kafka.model.DumpRecord
import org.apache.kafka.connect.sink.SinkRecord
import org.apache.kafka.connect.storage.Converter
import java.io.DataOutputStream

/**
 * Writes records to S3 in a binary format.
 */
class S3RecordWriter(private val s3out: S3OutputStream, private val converter: Converter) : IORecordWriter {
    private val logger = KotlinLogging.logger {}
    private val s3outWrapper = s3out.wrapForCompression()
    private val out = DataOutputStream(s3outWrapper)
    private val writer = RecordStreamWriter(out)

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
        writer.close()
    }

    override fun close() {}
}
