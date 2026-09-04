package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 조사 A(`_workspace/m1-1b/01_scout_legacy-money.md`) 8축 중 1B 축의 회귀 사례를 example test로
 * 고정한다. 「승인된 corpus 통과」로 계상하지 않는다 — 이 다섯은 승인 문면 위에 서는 example이고
 * corpus 계상은 `OPEN-1B-CONTRACT`가 닫혀야 가능하다(설계 검토 §4.6).
 */
class RegressionExampleTest {
    /** E-1 — R-PROV-08 · 조사 A M-8. `bid_amount or 0.0`가 NULL을 평균에 끼워 넣는 회귀. */
    @Test
    fun `E-1 부재가 집계 분자·분모 어디에도 0으로 들어가지 않는다`() {
        val amounts =
            listOf(
                Fact.Known(BaseAmount(1_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)),
                Fact.Absent(ReasonCode.UNIT_NOT_DECLARED),
                Fact.Known(BaseAmount(2_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)),
            )

        // legacy는 이 자리에서 `1_000_000 + 0 + 2_000_000 = 3_000_000`을 냈다(NULL이 0으로 들어감).
        // 1B는 부재가 하나라도 있으면 전체를 Absent로 낸다 — 0으로 끼워 넣는 경로 자체가 없다.
        sumOfBaseAmounts(amounts) shouldBe Fact.Absent(ReasonCode.UNIT_NOT_DECLARED)
    }

    /** E-2 — 조사 A M-9. `round(max(0.0, recommended), 2)`가 음수를 사유 없이 0으로 접는 회귀. */
    @Test
    fun `E-2 음수 금액 입력이 0이 아니라 사유 있는 거부를 낸다`() {
        val overflow =
            assertThrows<IllegalArgumentException> {
                BaseAmount(-1L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)
            }

        // legacy(`max(0.0, recommended)`)처럼 조용히 0으로 접지 않는다 — 구성 자체가 거부된다.
        overflow.message?.contains("음수") shouldBe true
    }

    /** E-3 — R-RATE-01 · 조사 A R-1·R-7. `n/100 if n > 1.5`류의 크기 기반 단위 추측 회귀. */
    @Test
    fun `E-3 변환은 이름이 단위인 생성 지점 한 곳뿐이고 크기로 추측하지 않는다`() {
        // legacy 임계 1.5 전후 값을 fraction 경로로 넣으면 크기와 무관하게 값 그대로 통과한다.
        val boundary = listOf("0.5", "1.5", "1.500001", "2.0")

        boundary.forEach { literal ->
            val asFraction = Rate.ofFraction(BigDecimal(literal))
            asFraction.fraction.compareTo(BigDecimal(literal)) shouldBe 0
        }

        // 같은 원시 값도 `ofPercent`로 넣으면 100으로 나뉜다 — 변환은 이름(단위 선언)이 정하지
        // 값 크기가 정하지 않는다. legacy의 `n > 1.5` 분기가 여기 없다.
        val asPercent = Rate.ofPercent(BigDecimal("1.5"))
        asPercent.fraction.compareTo(BigDecimal("0.015")) shouldBe 0
    }

    /** E-4 — R-RATE-04 · 조사 A R-10. `clamp_bid_rate = max(0.7, min(1.4, v))`가 사유 없이 값을 바꾸는 회귀. */
    @Test
    fun `E-4 밴드 밖 입력이 값 변경이 아니라 그대로 보존된다 — 클램프하지 않는다`() {
        val outOfLegacyBand = Rate.ofFraction(BigDecimal("2.0"))

        // legacy라면 1.4로 클램프됐을 값이 1B에서는 원본 그대로다 — 밴드는 도메인이 아니라
        // 축별 정책 데이터의 일이고(설계 검토 §1c), 1B는 상한 단언 자체를 두지 않는다(L-10).
        outOfLegacyBand.fraction.compareTo(BigDecimal("2.0")) shouldBe 0
    }

    /** E-5 — 조사 A M-7·D-2. `round(float(budget) * rate, 2)`가 투찰가에 소수 2자리를 만드는 회귀. */
    @Test
    fun `E-5 금액 축에 소수 자리를 만드는 생성 경로가 없다`() {
        val base = BaseAmount(1_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)
        val rate = BidRate(Rate.ofFraction(BigDecimal("0.9555")), BidRateOrigin.Recommended)
        val policy = RoundingPolicy(MONEY_AXIS_SCALE_DIGITS, RoundingMode.HALF_UP)
        val resolved = Resolution.Resolved(policy, PolicyVersion(EffectiveFrom.Initial, "test"))

        val result = (base * rate).roundedWith(resolved)

        // BidAmount.export().won 은 Long 이라 애초에 소수 자리를 표현할 수 없다 — 반올림이
        // 자리수 0으로 이미 닫혔고(§1.1 정의 ①), legacy 의 `round(…, 2)` 경로가 여기 없다.
        result.shouldBeInstanceOf<Measurement.Measured<BidAmount>>()
    }
}
