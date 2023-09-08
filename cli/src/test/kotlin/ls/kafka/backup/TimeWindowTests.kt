package ls.kafka.backup

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class TimeWindowTests : FreeSpec({
    "should return true if window has no boundaries" {
        val window = TimeWindow(null, null)
        (1..100L).forEach {
            window.contains(it * 10000) shouldBe true
        }
        window.contains(Long.MAX_VALUE) shouldBe true
    }
})
