package ls.kafka.backup.s3

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class LogDirectory(val path: Path) {

    val partition: Int
    val topic: String

    init {
        if (!path.isDirectory()) error("$path is not a directory")
        path.name.let {
            partition = it.substringAfterLast("-").toInt()
            topic = it.substringBeforeLast("-")
        }
    }

    fun segmentFiles(): Flow<SegmentFile> = flow {
        path.listDirectoryEntries("*.log").sorted().forEach { emit(it.asSegmentFile()) }
    }

    fun brokenSegments(): Flow<SegmentFile> = segmentFiles().mapNotNull { sf ->
        if (sf.hasProducerSnapshot()) {
            // do not analyze segment files which are currently written to
            null
        } else {
            val corrupted = sf.getCorruptedOffsets().toList()
            if (corrupted.isNotEmpty()) {
                sf
            } else {
                null
            }
        }
    }
}
