package ls.kafka.connect.storage.format

import io.confluent.connect.s3.S3SinkConnectorConfig
import io.confluent.connect.s3.format.RecordViewSetter
import io.confluent.connect.s3.format.S3RetriableRecordWriter
import io.confluent.connect.s3.storage.S3Storage
import io.confluent.connect.s3.util.Utils
import io.confluent.connect.storage.format.Format
import io.confluent.connect.storage.format.RecordWriter
import io.confluent.connect.storage.format.RecordWriterProvider
import io.confluent.connect.storage.format.SchemaFileReader
import ls.kafka.connect.storage.io.S3SinkRecordWriter
import org.apache.kafka.connect.converters.ByteArrayConverter

/**
 * ByteArrayRecordFormat is a custom implementation of the Format interface that uses the ByteArrayConverter
 * to write binary data to S3.
 * This class can be used in the connector configuration by setting the format.class property to the FQCN of this class.
 * e.g. `format.class=ls.kafka.connect.storage.format.ByteArrayRecordFormat`
 */
class ByteArrayRecordFormat(private val storage: S3Storage) : Format<S3SinkConnectorConfig, String> {

    private val converter = ByteArrayConverter()

    init {
        this.converter.configure(emptyMap<String, Any>(), false)
    }

    override fun getRecordWriterProvider(): RecordWriterProvider<S3SinkConnectorConfig> =
        object : RecordViewSetter(), RecordWriterProvider<S3SinkConnectorConfig> {
            override fun getExtension(): String =
                storage.conf().byteArrayExtension + storage.conf().compressionType.extension

            override fun getRecordWriter(conf: S3SinkConnectorConfig, filename: String): RecordWriter {
                val adjustedFilename = Utils.getAdjustedFilename(recordView, filename, getExtension())
                val s3out = storage.create(adjustedFilename, true, ByteArrayRecordFormat::class.java)
                val ioWriter = S3SinkRecordWriter(s3out, converter)
                return S3RetriableRecordWriter(ioWriter)
            }
        }

    override fun getSchemaFileReader(): SchemaFileReader<S3SinkConnectorConfig, String> =
        throw UnsupportedOperationException("Reading schemas from S3 is not currently supported")

    @Deprecated("Deprecated Method", ReplaceWith("no replacement"))
    override fun getHiveFactory(): Any =
        throw UnsupportedOperationException("Hive integration is not currently supported in S3 Connector")
}
