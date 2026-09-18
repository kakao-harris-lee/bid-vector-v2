package bidvector.adapters.strategy

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [SystemClock]은 한 줄 위임이지만 그 위임 자체(단조 진행·`Instant.now()`로의 실 전달)를
 * 잰다 — scope.md in_scope 목록의 test.
 */
class SystemClockTest {
    @Test
    fun `now 는 Instant_now 호출 구간 안의 값을 낸다`() {
        val before = Instant.now()
        val observed = SystemClock().now()
        val after = Instant.now()

        val withinWindow = !observed.isBefore(before) && !observed.isAfter(after)
        withinWindow shouldBe true
    }

    @Test
    fun `연속 호출은 단조 진행한다`() {
        val clock = SystemClock()
        val first = clock.now()
        val second = clock.now()

        second.isBefore(first) shouldBe false
    }
}
