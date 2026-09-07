package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.RoundingMode

/** M1/1D D-1(a)+D-9 — shared-kernel 좁은 확장(3.1 커밋) 회귀. */
class RateArithmeticTest {
    private fun scalePolicy(digits: Int): Resolution.Resolved<RoundingPolicy> =
        Resolution.Resolved(RoundingPolicy(digits, RoundingMode.HALF_UP), PolicyVersion(EffectiveFrom.Initial, "test"))

    @Test
    fun `D-9 fraction 은 공개 읽기이고 Rate 는 fraction 값으로 비교된다`() {
        val smaller = Rate.ofFraction(BigDecimal("0.9"))
        val larger = Rate.ofFraction(BigDecimal("1.1"))

        smaller.fraction shouldBe BigDecimal("0.9")
        (smaller < larger) shouldBe true
        (larger > smaller) shouldBe true
        (smaller.compareTo(Rate.ofFraction(BigDecimal("0.90"))) == 0) shouldBe true
    }

    @Test
    fun `BidRate recommended 는 Recommended origin 을 낸다`() {
        val rate = BidRate.recommended(Rate.ofFraction(BigDecimal("0.91875")))

        rate.origin shouldBe BidRateOrigin.Recommended
        rate.rate.fraction shouldBe BigDecimal("0.91875")
    }

    @Test
    fun `AssessmentRate observed 는 파생이 아니라 재구성이다 — 값을 그대로 감싼다`() {
        val fraction = Rate.ofFraction(BigDecimal("1.0499"))

        val observed = AssessmentRate.observed(fraction)

        observed.rate shouldBe fraction
    }

    @Test
    fun `criticalAssessmentRate 는 추천 투찰율 나누기 낙찰하한율이다`() {
        val bid = BidRate.recommended(Rate.ofFraction(BigDecimal("0.91875")))
        val floor = FloorRate(Rate.ofFraction(BigDecimal("0.875")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))

        val result = criticalAssessmentRate(bid, floor, scalePolicy(6))

        result.shouldBeInstanceOf<Measurement.Measured<Derived<AssessmentRate>>>()
        val fraction = result.value.value.rate.fraction
        fraction.compareTo(BigDecimal("1.05")) shouldBe 0
    }

    @Test
    fun `verifier r1 F-1 — criticalAssessmentRate 는 형제 파생과 같은 형태로 DerivationRecord 를 싣는다`() {
        val bid = BidRate.recommended(Rate.ofFraction(BigDecimal("0.91875")))
        val floor = FloorRate(Rate.ofFraction(BigDecimal("0.875")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))
        val policy = scalePolicy(6)

        val result = criticalAssessmentRate(bid, floor, policy)

        result.shouldBeInstanceOf<Measurement.Measured<Derived<AssessmentRate>>>()
        result.value.derivedFrom.policyVersion shouldBe policy.version
    }

    @Test
    fun `낙찰하한율이 0이면 임계 사정률은 정의되지 않는다`() {
        val bid = BidRate.recommended(Rate.ofFraction(BigDecimal("0.9")))
        val floor = FloorRate(Rate.ofFraction(BigDecimal.ZERO), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))

        val result = criticalAssessmentRate(bid, floor, scalePolicy(6))

        result.shouldBeInstanceOf<Measurement.Unmeasurable>()
        result.reason shouldBe ReasonCode.EMPTY_INPUT
    }

    @Test
    fun `D-10 나눗셈 자리수는 호출부가 정책으로 준다 — 자리수를 바꾸면 결과가 바뀐다`() {
        val bid = BidRate.recommended(Rate.ofFraction(BigDecimal.ONE))
        val floor = FloorRate(Rate.ofFraction(BigDecimal("3")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))

        val coarse = criticalAssessmentRate(bid, floor, scalePolicy(2)) as Measurement.Measured<Derived<AssessmentRate>>
        val fine = criticalAssessmentRate(bid, floor, scalePolicy(6)) as Measurement.Measured<Derived<AssessmentRate>>

        coarse.value.value.rate.fraction shouldBe BigDecimal("0.33")
        fine.value.value.rate.fraction shouldBe BigDecimal("0.333333")
    }

    @Test
    fun `비정규 fraction 은 여전히 거부된다 — D-9 확장이 Rate 불변식을 열지 않는다`() {
        assertThrows<IllegalArgumentException> {
            Rate(BigDecimal("1.500"))
        }
    }
}
