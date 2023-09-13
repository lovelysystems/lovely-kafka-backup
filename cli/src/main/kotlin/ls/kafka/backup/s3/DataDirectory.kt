package ls.kafka.backup.s3

import kotlinx.coroutines.flow.*
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo

class DataDirectory(private val path: Path) {

    init {
        if (!path.isDirectory()) error("not a directory: $path")
    }

    fun logDirectories(glob: String): List<LogDirectory> {
        return path.listDirectoryEntries(glob).filter { it.isDirectory() }.sorted()
            .map { LogDirectory(it) }
    }

    fun brokenSegments(glob: String) = flow {
        for (ld in logDirectories(glob)) {
            println("checking for broken segments in : ${ld.path.fileName}")
            emitAll(ld.brokenSegments())
        }
    }

    suspend fun repairBrokenSegments(
        bucket: BackupBucket,
        glob: String,
        backupSuffix: String = "pre_repair_bak",
        skipBroken: Boolean = false
    ): Flow<SegmentFile> =
        brokenSegments(glob).map { sf ->
            val repairedFile = sf.segmentPath("repaired")
            if (repairedFile.exists()) error("repaired file exists $repairedFile")

            println("repairing log file to ${repairedFile.fileName} ...")
            sf.repairFromBackup(repairedFile.toFile(), bucket, failOnMissing = !skipBroken)

            for (ext in listOf("log", "index", "timeindex")) {
                val src = sf.segmentPath(ext)
                val trg = sf.segmentPath("$ext.$backupSuffix")
                if (trg.exists()) error("backupfile exists $trg")
                src.moveTo(trg)
            }

            println("use repaired file as log file ${sf.logFile.fileName}")
            repairedFile.moveTo(sf.logFile)
            sf
        }
}
