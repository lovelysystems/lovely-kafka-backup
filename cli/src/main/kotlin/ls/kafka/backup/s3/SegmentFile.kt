package ls.kafka.backup.s3

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import ls.kafka.model.DumpRecord
import org.apache.kafka.common.errors.CorruptRecordException
import org.apache.kafka.common.record.*
import org.apache.kafka.common.utils.ByteBufferOutputStream
import org.apache.kafka.storage.internals.log.OffsetIndex
import org.apache.kafka.storage.internals.log.OffsetPosition
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.math.max

// from https://github.com/apache/kafka/blob/db1f581da7f3440cfd5be93800b4a9a2d7327a35/core/src/main/scala/kafka/tools/DumpLogSegments.scala#L120
// see https://github.com/a0x8o/kafka/blob/master/core/src/main/scala/kafka/tools/DumpLogSegments.scala#L244
// tests https://github.com/a0x8o/kafka/blob/master/clients/src/test/java/org/apache/kafka/common/record/MemoryRecordsTest.java

// explanation of file https://rohithsankepally.github.io/Kafka-Storage-Internals/

fun RecordBatch.toRecords(): MemoryRecords {

    val buffer = ByteBuffer.allocate(sizeInBytes())
    val builder = MemoryRecordsBuilder(
        buffer,
        magic(),
        compressionType(),
        timestampType(),
        baseOffset(),
        RecordBatch.NO_TIMESTAMP,
        producerId(),
        producerEpoch(),
        baseSequence(),
        false,
        false,
        partitionLeaderEpoch(),
        buffer.limit()
    )
    forEach {
        builder.append(it)
    }
    return builder.build()
}

fun FileLogInputStream.FileChannelRecordBatch.missingOffsets(lastBatch: FileLogInputStream.FileChannelRecordBatch?): LongRange {
    @Suppress("NotImplementedDeclaration")
    if (lastBatch == null) TODO("missing offsets without previous batch")
    return if (position() > lastBatch.position() + lastBatch.sizeInBytes()) {
        lastBatch.nextOffset() until baseOffset()
    } else {
        LongRange.EMPTY
    }
}

fun Path.asSegmentFile(): SegmentFile {
    parent.name.let { name ->
        return SegmentFile(
            this,
            partition = name.substringAfterLast("-").toInt(),
            topic = name.substringBeforeLast("-")
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SegmentFile(
    val logFile: Path,
    val partition: Int,
    val topic: String
) {

    private val logger = KotlinLogging.logger { }

    private val indexFile = segmentPath("index")
    val startOffset = logFile.nameWithoutExtension.toLong()

    fun segmentPath(extension: String): Path =
        logFile.parent / "${logFile.nameWithoutExtension}.$extension"

    fun hasProducerSnapshot(): Boolean {
        return segmentPath("snapshot").exists()
    }

    private fun offsetPositions(): List<OffsetPosition> {
        if (!indexFile.isRegularFile()) {
            error("not a file $indexFile")
        }
        val index =
            OffsetIndex(indexFile.toFile(), startOffset, indexFile.fileSize().toInt(), false)

        val list = (0 until index.entries()).map { i ->
            val entry = index.entry(i)!!
            // since it is a sparse file, in the event of a crash there may be many zero entries, stop if we see one
            if ((index.baseOffset() == entry.offset) && i > 0) {
                error("corrupted index $indexFile")
            }
            entry
        }
        index.close()
        return list
    }

    fun getCorruptedOffsets() =
        batchPairs().flatMapConcat { (currentBatch, lastBatch) ->
            flow {
                lastBatch?.let {
                    currentBatch.missingOffsets(it).forEach { offset -> emit(offset) }
                }
            }
        }

    fun getFileRecords(): FileRecords {
        return FileRecords.open(logFile.toFile(), false, true, 0, false)
    }

    private fun findOffsetGaps() = flow {
        var last = startOffset - 1
        for (batch in getFileRecords().batchIterator()) {
            for (record in batch) {
                val step = record.offset() - last
                if (step != 1L) {
                    emit(Pair(record, last))
                }
                last = record.offset()
            }
        }
    }

    fun batchPairs(): Flow<Pair<FileLogInputStream.FileChannelRecordBatch, FileLogInputStream.FileChannelRecordBatch?>> =
        flow {
            val fileRecords = getFileRecords()

            var iterator: Iterator<FileLogInputStream.FileChannelRecordBatch> =
                fileRecords.batchIterator()

            var lastBatch: FileLogInputStream.FileChannelRecordBatch? = null
            var currentBatch: FileLogInputStream.FileChannelRecordBatch?
            var nextPosition = OffsetPosition(0, 0)

            var finished = false

            while (!finished) {
                try {
                    while (iterator.hasNext()) {
                        currentBatch = iterator.next()
                        lastBatch?.let { last ->
                            assert(currentBatch.baseOffset() != last.baseOffset()) {
                                "tried to yield the same offset twice ${currentBatch.baseOffset()}"
                            }
                        }
                        emit(Pair(currentBatch, lastBatch))
                        lastBatch = currentBatch
                    }
                    finished = true // No more elements in the iterator
                } catch (e: CorruptRecordException) {
                    val lastWorkingBatch = lastBatch ?: TODO("first batch failure not implemented")
                    val maxPosition = max(lastWorkingBatch.position(), nextPosition.position)
                    nextPosition = offsetPositions().first { it.position > maxPosition }
                    iterator = fileRecords.batchesFrom(nextPosition.position).iterator()
                }
            }

            // do not call close on the fileRecords since this will modify the file
            fileRecords.channel().close()

        }

    private fun memoryRecordsFromRestored(
        magic: Byte, initialOffset: Long, compressionType: CompressionType?,
        timestampType: TimestampType, producerId: Long, producerEpoch: Short,
        baseSequence: Int, partitionLeaderEpoch: Int,
        restored: List<Pair<Long, SimpleRecord?>>
    ): MemoryRecords {
        val records = restored.mapNotNull { it.second }

        if (records.isEmpty()) {
            logger.warn { "none of the offsets ${restored.map { it.first }} can be restored emitting empty batch" }
            return MemoryRecords.EMPTY
        } else if (records.size < restored.size) {

            logger.warn {
                "not all offsets can be restored: " +
                        "missing=${restored.filter { it.second == null }.map { it.first }} " +
                        "found=  ${restored.filter { it.second != null }.map { it.first }}"
            }
        }

        val sizeEstimate =
            AbstractRecords.estimateSizeInBytes(magic, compressionType, records)

        val bufferStream = ByteBufferOutputStream(sizeEstimate)
        var logAppendTime = RecordBatch.NO_TIMESTAMP
        if (timestampType == TimestampType.LOG_APPEND_TIME) logAppendTime =
            System.currentTimeMillis()
        val builder = MemoryRecordsBuilder(
            bufferStream,
            magic,
            compressionType,
            timestampType,
            initialOffset,
            logAppendTime,
            producerId,
            producerEpoch,
            baseSequence,
            false,
            false,
            partitionLeaderEpoch,
            sizeEstimate
        )

        for ((offset, record) in restored) {
            record?.let {
                builder.appendWithOffset(offset, record)
            }
        }
        return builder.build()
    }

    private fun fillBatch(
        templateBatch: RecordBatch,
        missing: LongRange,
        restoreLookup: (Long) -> DumpRecord?
    ): MemoryRecords {
        val restored = missing.map {
            Pair(it, restoreLookup(it)?.let { record -> SimpleRecord(record.ts, record.key, record.value) })
        }
        return memoryRecordsFromRestored(
            templateBatch.magic(),
            missing.first,
            templateBatch.compressionType(),
            templateBatch.timestampType(),
            templateBatch.producerId(),
            templateBatch.producerEpoch(),
            templateBatch.baseSequence(),
            templateBatch.partitionLeaderEpoch(),
            restored
        )
    }

    private suspend fun repair(outFile: File, restoreLookup: (Long) -> DumpRecord?) {
        if (outFile.exists()) error("output file $outFile already exists")
        val outRecords = FileRecords.open(outFile, true)
        batchPairs().collect { (currentBatch, lastBatch) ->
            if (lastBatch != null) {
                val missing = currentBatch.missingOffsets(lastBatch)
                if (!missing.isEmpty()) {
                    val filledBatch = fillBatch(lastBatch, missing, restoreLookup)
                    outRecords.append(filledBatch)
                }
                outRecords.append(currentBatch.toRecords())
            } else {
                // this is the first batch in the file
                outRecords.append(currentBatch.toRecords())
            }
        }
        outRecords.flush()
        outRecords.close()
        // sanity check
        val smOut = SegmentFile(outFile.toPath(), topic = "ignored", partition = 0)
        smOut.getCorruptedOffsets().toSet().takeIf { it.isNotEmpty() }?.let {
            error("repair failed, found broken offsets: ${it.joinToString(",")} in $outFile")
        }
        smOut.findOffsetGaps().collect { (record, last) ->
            logger.warn { "gap found in repaired file: last=$last record=$record" }
        }
    }

    suspend fun repairFromBackup(
        outFile: File,
        backupBucket: BackupBucket,
        failOnMissing: Boolean = true
    ): Boolean {
        if (hasProducerSnapshot()) error("cannot repair produced segment file ${logFile.name}")
        val corruptedOffsets = getCorruptedOffsets().toSet()
        if (corruptedOffsets.isEmpty()) {
            // nothing to repair
            return false
        }
        val toRestore = backupBucket.getRecordsForOffsets(
            topic,
            partition = partition,
            offsets = corruptedOffsets,
        ).toList().associateBy { it.offset }

        if (failOnMissing && !toRestore.keys.containsAll(corruptedOffsets)) {
            //some corrupted offsets weren't found in the backup
            error("could not find all offsets: searched: $corruptedOffsets found: ${toRestore.keys}")
        }

        repair(outFile) {
            toRestore[it]
        }
        return true
    }
}

