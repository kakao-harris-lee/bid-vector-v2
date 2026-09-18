package bidvector.adapters.evaluation

import bidvector.procurement.NoticeStatus
import bidvector.procurement.isBiddable
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * D-6F2-2 집합 등식 게이트 — SQL이 쓰는 상태 집합([biddableStatuses])이 도메인 술어
 * ([isBiddable])가 참인 집합과 **정확히** 같은지를 잰다. 하한 단언(`size >= n`)을 쓰지
 * 않는다 — [NoticeStatus]에 값이 추가되거나 [isBiddable]의 정의가 바뀌면 이 test가 독립
 * 계산으로 그 어긋남을 잡는다(우회 (1)·(2)).
 */
class CandidateStatusSetTest {
    @Test
    fun `biddableStatuses 는 isBiddable 가 참인 상태 집합과 정확히 같다`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val future = now.plusSeconds(1)
        val expected = NoticeStatus.entries.filter { status -> isBiddable(status, now, future) }.toSet()

        biddableStatuses() shouldBe expected
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다(전체 여섯 값을 그대로 쓰지 않는다). */
    @Test
    fun `biddableStatuses 는 전체 상태 집합과 다르다`() {
        biddableStatuses() shouldBe setOf(NoticeStatus.Open, NoticeStatus.Renoticed)
        (biddableStatuses().size < NoticeStatus.entries.size) shouldBe true
    }
}
