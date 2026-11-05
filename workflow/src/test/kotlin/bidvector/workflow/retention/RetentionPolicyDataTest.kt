package bidvector.workflow.retention

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** 보존 기간을 1년으로 올린 날(사용자 결정) — 정책 표의 개정 항목이 이 날부터 선다. */
private val REVISION_DATE: LocalDate = LocalDate.of(2026, 11, 5)

/** 그 전날 — 개정 **앞쪽** 기준일이 여전히 옛 값을 받는지 재는 자리다. */
private val DAY_BEFORE_REVISION: LocalDate = REVISION_DATE.minusDays(1)

/** 운영자 결정 2026-09-26 ② 의 값. 개정 뒤에도 그 시대의 기준일에는 이 값이 선다(소급 금지). */
private const val DAYS_BEFORE_REVISION = 90

/** 사용자 결정 2026-11-05 — 1년. */
private const val DAYS_FROM_REVISION = 365

/**
 * P-5 — 보존 정책 값. 이 test 는 **값이 승인된 값인가**만 본다(개정 전·후의 두 값과 일괄 하한).
 *
 * 「그 값이 실제 DELETE 술어에 닿는가」는 DB 위에서 잰다 — `RetentionSweepTest`(경계 ±1초와
 * 통제군)와 `RetentionMaskingSweepTest` 다. 거기서 경계는 **test 자신의 앵커**로 서므로 정책 값을
 * 364·366 으로 바꾸면 RED 가 된다(cr L-1 — 앞 판은 저장소에 **없는** test 클래스를 그 실측 자리로
 * 가리켰다). 「출하 배선이 읽은 값이 러너에 닿는가」는 `RetentionWiringTest` 가 잰다(vr F-1).
 */
class RetentionPolicyDataTest {
    @Test
    fun `개정일부터 승인된 값은 1년이다`() {
        val resolution = RETENTION_POLICY.resolve(REVISION_DATE)

        (resolution as Resolution.Resolved).value.retentionDays shouldBe DAYS_FROM_REVISION
    }

    @Test
    fun `개정일 뒤 기준일에도 1년이다 — 개정 항목이 가장 늦은 항목이다`() {
        val resolution = RETENTION_POLICY.resolve(REVISION_DATE.plusMonths(6))

        (resolution as Resolution.Resolved).value.retentionDays shouldBe DAYS_FROM_REVISION
    }

    /**
     * 날짜 개정은 **옛 값을 지우지 않는다**(`data-dictionary.md` §4.1 계승 규율 — 소급 금지).
     * 개정 앞쪽 기준일로 해소한 run·감사는 그 시대의 90일을 그대로 받아야 한다.
     */
    @Test
    fun `개정일 전 기준일에는 90일이다 — 그 시대의 값이 남는다`() {
        val resolution = RETENTION_POLICY.resolve(DAY_BEFORE_REVISION)

        (resolution as Resolution.Resolved).value.retentionDays shouldBe DAYS_BEFORE_REVISION
    }

    /**
     * 해소값이 **어느 항목으로 섰는지**는 `effectiveFrom` 한 축이 말한다 — 아래 두 단언 가운데 판별자는
     * 그것이다. `source` 는 **정책 단위 문자열**이라(`EffectiveDatedPolicy.resolve` 가 모든 version 에 같은
     * 값을 붙인다) 개정 앞쪽 기준일로 해소해도 똑같이 통과한다(cr L-1). 그래서 그 두 줄이 재는 것은
     * 「정책의 출처가 **두 결정을 모두** 인용한다」까지이고, 항목별 출처는 이 타입이 갖지 않는다.
     */
    @Test
    fun `개정 항목의 유효 시작일과 출처가 두 결정을 가리킨다`() {
        val version = (RETENTION_POLICY.resolve(REVISION_DATE) as Resolution.Resolved).version

        version.effectiveFrom shouldBe EffectiveFrom.On(REVISION_DATE)
        version.source shouldContain "2026-09-26"
        version.source shouldContain "2026-11-05"
    }

    @Test
    fun `보존 기간 0 은 받지 않는다 — cutoff 가 현재가 되어 전부 파기가 된다`() {
        shouldThrow<IllegalArgumentException> { RetentionPolicyData(retentionDays = 0, batchSize = 10) }
    }

    @Test
    fun `보존 기간 음수는 받지 않는다 — cutoff 가 미래가 되어 기한 안쪽 행도 지운다`() {
        shouldThrow<IllegalArgumentException> { RetentionPolicyData(retentionDays = -1, batchSize = 10) }
    }

    @Test
    fun `일괄 크기 0 은 받지 않는다 — 한 번도 줄지 않는 루프다`() {
        shouldThrow<IllegalArgumentException> {
            RetentionPolicyData(retentionDays = DAYS_FROM_REVISION, batchSize = 0)
        }
    }

    @Test
    fun `cutoff 는 주어진 시각에서 보존 기간을 뺀 값이고 언제나 그 시각보다 과거다`() {
        val now = Instant.parse("2026-11-05T00:00:00Z")

        val cutoff = RetentionPolicyData(retentionDays = DAYS_FROM_REVISION, batchSize = 10).cutoffFrom(now)

        cutoff.instant shouldBe Instant.parse("2025-11-05T00:00:00Z")
    }
}
