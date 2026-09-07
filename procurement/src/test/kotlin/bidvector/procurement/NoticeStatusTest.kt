package bidvector.procurement

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

private val DEADLINE = Instant.parse("2026-09-10T00:00:00Z")

/** ⑨ §2.2.1 — 전이표 밖 (상태, 이벤트) 쌍은 거부이며 관측 가능해야 한다. */
class NoticeStatusTest {
    @Test
    fun `Open 에서 DeadlineReached 는 Closed 로 전이한다`() {
        val result = transition(NoticeStatus.Open, NoticeEvent.DeadlineReached)

        result shouldBe TransitionResult.Moved(NoticeStatus.Closed)
    }

    @Test
    fun `Closed 에서 RenoticeObserved 는 표에 없어 거부다 — 조용히 무시하지 않는다`() {
        val result = transition(NoticeStatus.Closed, NoticeEvent.RenoticeObserved)

        result shouldBe TransitionResult.Rejected(NoticeStatus.Closed, NoticeEvent.RenoticeObserved)
    }

    @Test
    fun `종단 상태 셋(Awarded Failed Cancelled)은 어떤 이벤트로도 나가는 전이가 없다`() {
        val terminal = listOf(NoticeStatus.Awarded, NoticeStatus.Failed, NoticeStatus.Cancelled)
        val events =
            listOf(
                NoticeEvent.RenoticeObserved,
                NoticeEvent.DeadlineReached,
                NoticeEvent.AwardObserved,
                NoticeEvent.FailureObserved,
                NoticeEvent.CancellationObserved,
            )

        for (status in terminal) {
            for (event in events) {
                transition(status, event).shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }
    }

    @Test
    fun `Open Renoticed Closed 는 CancellationObserved 로 Cancelled 에 도달한다`() {
        transition(NoticeStatus.Open, NoticeEvent.CancellationObserved) shouldBe
            TransitionResult.Moved(NoticeStatus.Cancelled)
        transition(NoticeStatus.Renoticed, NoticeEvent.CancellationObserved) shouldBe
            TransitionResult.Moved(NoticeStatus.Cancelled)
        transition(NoticeStatus.Closed, NoticeEvent.CancellationObserved) shouldBe
            TransitionResult.Moved(NoticeStatus.Cancelled)
    }

    @Test
    fun `isBiddable 은 Open Renoticed 이고 마감 전일 때만 참이다 — 상태 리터럴을 직접 고르지 않는다`() {
        isBiddable(NoticeStatus.Open, DEADLINE.minusSeconds(10), DEADLINE) shouldBe true
        isBiddable(NoticeStatus.Renoticed, DEADLINE.minusSeconds(10), DEADLINE) shouldBe true
        isBiddable(NoticeStatus.Closed, DEADLINE.minusSeconds(10), DEADLINE) shouldBe false
    }

    @Test
    fun `isBiddable 구간은 끝을 제외한다 — now 가 deadline 과 같으면 이미 마감`() {
        isBiddable(NoticeStatus.Open, DEADLINE, DEADLINE) shouldBe false
    }
}
