package bidvector.adapters.qualification

import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseVerdict
import bidvector.sharedkernel.AccountId
import bidvector.sharedkernel.NoticeRound
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [PinnedLicenseGate] 의 **계정 불일치 가드**(r1 리뷰 · verifier F3).
 *
 * `PinnedStrategyRepository` 쪽에는 같은 가드의 test(`고정한 계정이 아니면 load 가 거부한다`)가
 * 있는데 이쪽에는 짝이 없었다 — 변이로 `check` 를 무력화해도 96 건이 전부 초록이었다. 고정
 * 데코레이터가 계정 인자를 **조용히 무시**하면 남의 계정 자격 판정을 그대로 쓰는 조립이 거동
 * 변화 없이 통과하고, 그것이 이 slice 가 닫는 누출의 모양이다.
 *
 * 두 갈래를 **둘 다** 잰다 — 거부만 재면 「늘 던진다」는 변형이 초록이고, 통과만 재면 가드가
 * 사라져도 초록이다.
 */
class PinnedLicenseGateTest {
    @Test
    fun `고정한 계정이면 고정한 판정을 그대로 돌려준다`() {
        val verdict = LicenseVerdict.Eligible(emptySet())
        val gate = PinnedLicenseGate(PINNED_ACCOUNT, verdict)

        gate.verdictFor(PINNED_ACCOUNT, testNotice()) shouldBe verdict
    }

    @Test
    fun `고정한 계정이 아니면 판정을 돌려주지 않고 크게 실패한다`() {
        val gate = PinnedLicenseGate(PINNED_ACCOUNT, LicenseVerdict.Eligible(emptySet()))

        shouldThrow<IllegalStateException> { gate.verdictFor(OTHER_ACCOUNT, testNotice()) }
    }
}

private val PINNED_ACCOUNT = AccountId(1)
private val OTHER_ACCOUNT = AccountId(2)
private val NOW: Instant = Instant.parse("2026-01-01T00:00:00Z")

/** 공고는 이 가드의 축이 아니다 — 게이트가 계정을 먼저 보므로 최소 관측 하나면 된다. */
private fun testNotice(): Notice {
    val number = "20260101001"
    val observation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
            SourceEndpoint.NOTICE_LIST,
            NOW,
        )
    return Notice.collected(
        NoticeCollected(
            id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000")),
            businessCategory = null,
            baseAmount = null,
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = NOW.plusSeconds(86_400),
            openingScheduledAt = null,
            raw = observation,
        ),
    )
}
