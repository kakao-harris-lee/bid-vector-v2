package bidvector.procurement

import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private val NOTICE_ID = NoticeId(NoticeNumber.of("20260101001"), NoticeRound.of("000"))
private val GATES = DetailFetchGates(ageGateHours = 24, recheckGateHours = 48)
private val OPENED_AT = Instant.parse("2026-09-01T00:00:00Z")

/** ⑪ COL-03 — 「무엇을 언제 조회할 가치가 있는가」라는 순수 술어. */
class DetailFetchTest {
    @Test
    fun `이미 저장된 공고는 AlreadyHeld 로 조회하지 않는다 — 상세 HTTP 호출 0회`() {
        val decision =
            decideDetailFetch(
                NOTICE_ID,
                alreadyHeld = true,
                openingObservedAt = null,
                lastCheckedAt = null,
                now = OPENED_AT,
                gates = GATES,
            )

        decision shouldBe DetailFetchDecision.Skip(DetailFetchSkipReason.AlreadyHeld)
    }

    @Test
    fun `age-gate 미만 경과는 AgeGateNotPassed 로 건너뛴다`() {
        val now = OPENED_AT.plus(Duration.ofHours(23))

        val decision = decideDetailFetch(NOTICE_ID, false, OPENED_AT, null, now, GATES)

        decision.shouldBeInstanceOf<DetailFetchDecision.Skip>()
        decision.reason.shouldBeInstanceOf<DetailFetchSkipReason.AgeGateNotPassed>()
    }

    @Test
    fun `age-gate 를 넘긴 뒤 정확히 Fetch 다`() {
        val now = OPENED_AT.plus(Duration.ofHours(24))

        val decision = decideDetailFetch(NOTICE_ID, false, OPENED_AT, null, now, GATES)

        decision shouldBe DetailFetchDecision.Fetch(NOTICE_ID)
    }

    @Test
    fun `recheck-gate 미만이면 다음 창까지 RecheckGateNotPassed 다`() {
        val lastChecked = OPENED_AT.plus(Duration.ofHours(30))
        val now = lastChecked.plus(Duration.ofHours(10))

        val decision = decideDetailFetch(NOTICE_ID, false, OPENED_AT, lastChecked, now, GATES)

        decision.shouldBeInstanceOf<DetailFetchDecision.Skip>()
        decision.reason.shouldBeInstanceOf<DetailFetchSkipReason.RecheckGateNotPassed>()
    }

    @Test
    fun `recheck-gate 를 넘기면 다시 Fetch 다`() {
        val lastChecked = OPENED_AT.plus(Duration.ofHours(30))
        val now = lastChecked.plus(Duration.ofHours(48))

        val decision = decideDetailFetch(NOTICE_ID, false, OPENED_AT, lastChecked, now, GATES)

        decision shouldBe DetailFetchDecision.Fetch(NOTICE_ID)
    }

    @Test
    fun `Fetch 는 internal 생성자다 — 이 술어 밖에서 조립할 수 없다(우회 8, 컴파일 시점 확인은 procurement 모듈 밖 test 몫)`() {
        val decision = decideDetailFetch(NOTICE_ID, false, null, null, OPENED_AT, GATES)

        decision shouldBe DetailFetchDecision.Fetch(NOTICE_ID)
    }

    @Test
    fun `DetailFetchGates 는 음수 시간을 거부한다`() {
        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            DetailFetchGates(ageGateHours = -1, recheckGateHours = 0)
        }
    }
}
