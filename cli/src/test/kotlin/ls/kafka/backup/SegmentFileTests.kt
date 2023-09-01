package ls.kafka.backup

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import ls.kafka.backup.s3.SegmentFile
import ls.kafka.backup.s3.missingOffsets
import org.apache.kafka.common.record.FileLogInputStream.FileChannelRecordBatch
import kotlin.io.path.Path

class SegmentFileTests : FreeSpec({

    afterSpec {
        unmockkStatic(FileChannelRecordBatch::missingOffsets)
    }

    "should return missing offsets from underlying batches" {
        mockkStatic(FileChannelRecordBatch::missingOffsets)

        val sm = SegmentFile(Path("parent/irrelevant-for-this-test/00000.log"), 0, "irrelevant")
        val spiedSm = spyk(sm)
        every {
            spiedSm.batchPairs()
        } returns flow {
            emit(Pair(mockk {
                every {
                    missingOffsets(any())
                } returns LongRange.EMPTY
                every {
                    position()
                } returns 0
            }, null))

            emit(Pair(mockk {
                every {
                    missingOffsets(any())
                } returns 11..20L
            }, mockk {
                every {
                    missingOffsets(any())
                } returns LongRange.EMPTY
            }))
        }

        spiedSm.getCorruptedOffsets().toList().shouldHaveSize(10)
    }
})
