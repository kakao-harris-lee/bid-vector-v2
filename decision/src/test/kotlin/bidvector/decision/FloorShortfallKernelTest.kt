package bidvector.decision

import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val TEST_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-floor-shortfall-policy")
private val FULL_RANGE_BAND = AssessmentBand(BigDecimal("-1000000"), BigDecimal("1000000"))
private val TIGHT_INDETERMINATE_BAND = AssessmentBand(BigDecimal("0.999"), BigDecimal("1.001"))

private fun rate(value: String): AssessmentRate = AssessmentRate.observed(Rate.ofFraction(BigDecimal(value)))

private fun policyOf(
    minAssessmentSamples: Int = 150,
    denominatorBand: AssessmentBand = FULL_RANGE_BAND,
    shortfallComparison: ShortfallComparison = ShortfallComparison.StrictlyGreater,
    biasIndeterminateBand: AssessmentBand = TIGHT_INDETERMINATE_BAND,
): Resolution.Resolved<FloorShortfallPolicyData> =
    Resolution.Resolved(
        FloorShortfallPolicyData(
            minAssessmentSamples = minAssessmentSamples,
            minAssessmentSamplesRationale = "통계적 편의(운영자 승인 2026-08-26)",
            denominatorBand = denominatorBand,
            shortfallComparison = shortfallComparison,
            biasIndeterminateBand = biasIndeterminateBand,
            criticalRateScale = 6,
        ),
        TEST_VERSION,
    )

/** M1/1D ④⑤⑥⑦⑧ — floor shortfall 커널 회귀. */
class FloorShortfallKernelTest {
    @Test
    fun `④ 경계 등가는 미달이 아니다 — strictly-greater`() {
        val critical = rate("1.05")

        isShortfall(rate("1.05"), critical, ShortfallComparison.StrictlyGreater) shouldBe false
        isShortfall(rate("1.0501"), critical, ShortfallComparison.StrictlyGreater) shouldBe true
        isShortfall(rate("1.0499"), critical, ShortfallComparison.StrictlyGreater) shouldBe false
    }

    @Test
    fun `④ greater-or-equal 정책에서는 경계 등가가 미달이다`() {
        val critical = rate("1.05")

        isShortfall(rate("1.05"), critical, ShortfallComparison.GreaterOrEqual) shouldBe true
    }

    @Test
    fun `⑥ 149 표본은 Unmeasurable(SampleInsufficient) 이다`() {
        val tally = ShortfallTally(rawCount = 149, outsideBand = 0, shortfallNumerator = 12)
        val policy = policyOf(minAssessmentSamples = 150)

        val judgement = measureFloorShortfall(tally, rate("1.05"), policy)

        val result = judgement.result
        result.shouldBeInstanceOf<FloorShortfall.Unmeasurable>()
        val reason = result.reason
        reason.shouldBeInstanceOf<FloorUnmeasurableReason.SampleInsufficient>()
        reason.required shouldBe 150
        reason.actual shouldBe 149
    }

    @Test
    fun `⑥ 150 표본은 측정 가능이다 — 경계값`() {
        val tally = ShortfallTally(rawCount = 150, outsideBand = 0, shortfallNumerator = 12)
        val policy = policyOf(minAssessmentSamples = 150)

        val judgement = measureFloorShortfall(tally, rate("1.05"), policy)

        val measured = judgement.result
        measured.shouldBeInstanceOf<FloorShortfall.Measured>()
        measured.frequency shouldBe Frequency(12, 150)
    }

    @Test
    fun `⑥ 151 표본도 측정 가능이다`() {
        val tally = ShortfallTally(rawCount = 151, outsideBand = 0, shortfallNumerator = 12)

        val judgement = measureFloorShortfall(tally, rate("1.05"), policyOf(minAssessmentSamples = 150))

        judgement.result.shouldBeInstanceOf<FloorShortfall.Measured>()
    }

    @Test
    fun `fs-005 전이 — 밴드 필터로 분모가 줄어 문턱 아래로 가면 Unmeasurable 이다`() {
        // rawCount 152 는 문턱(150) 이상이지만 밴드 밖 3개를 빼면 149로 줄어든다.
        val tally = ShortfallTally(rawCount = 152, outsideBand = 3, shortfallNumerator = 10)

        val judgement = measureFloorShortfall(tally, rate("1.05"), policyOf(minAssessmentSamples = 150))

        tally.qualifiedDenominator shouldBe 149
        val result = judgement.result
        result.shouldBeInstanceOf<FloorShortfall.Unmeasurable>()
        val reason = result.reason
        reason.shouldBeInstanceOf<FloorUnmeasurableReason.SampleInsufficient>()
        reason.required shouldBe 150
        reason.actual shouldBe 149
    }

    @Test
    fun `위협 모델 (f) — qualifiedDenominator 는 rawCount 빼기 outsideBand 다`() {
        runBlocking {
            checkAll(Arb.int(0, 500), Arb.int(0, 500)) { rawCount, outsideBandRaw ->
                val outsideBand = minOf(outsideBandRaw, rawCount)
                val tally = ShortfallTally(rawCount = rawCount, outsideBand = outsideBand, shortfallNumerator = 0)

                tally.qualifiedDenominator shouldBe rawCount - outsideBand
            }
        }
    }

    @Test
    fun `위협 모델 (2) — shortfallNumerator 는 qualifiedDenominator 를 넘을 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            ShortfallTally(rawCount = 10, outsideBand = 0, shortfallNumerator = 11)
        }
    }

    @Test
    fun `위협 모델 (2) — Frequency 는 denominator 가 0 이하면 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            Frequency(0, 0)
        }
    }

    @Test
    fun `위협 모델 (2) — numerator 가 denominator 를 넘으면 거부한다`() {
        shouldThrow<IllegalArgumentException> {
            Frequency(151, 150)
        }
    }

    @Test
    fun `위협 모델 (1) — Unmeasurable 은 소진 when 없이 값을 꺼낼 공개 경로가 없다`() {
        val result: FloorShortfall = FloorShortfall.Unmeasurable(FloorUnmeasurableReason.BidRateUnavailable)

        val collapsed =
            when (result) {
                is FloorShortfall.Measured -> result.frequency.numerator
                is FloorShortfall.Unmeasurable -> null
            }

        withClue("Unmeasurable 이 0 으로 접히지 않는다 — 소진 when 이 null 을 강제한다") {
            collapsed shouldBe null
        }
    }

    @Test
    fun `⑧ biasDirection — 임계가 불확정 밴드 밖 위쪽이면 Overestimates 다`() {
        val judgement =
            measureFloorShortfall(
                ShortfallTally(rawCount = 150, outsideBand = 0, shortfallNumerator = 10),
                rate("1.5"),
                policyOf(minAssessmentSamples = 150, biasIndeterminateBand = TIGHT_INDETERMINATE_BAND),
            )

        (judgement.result as FloorShortfall.Measured).biasDirection shouldBe BiasDirection.Overestimates
    }

    @Test
    fun `⑧ biasDirection — 임계가 불확정 밴드 안이면 Indeterminate 다`() {
        val judgement =
            measureFloorShortfall(
                ShortfallTally(rawCount = 150, outsideBand = 0, shortfallNumerator = 10),
                rate("1.0"),
                policyOf(minAssessmentSamples = 150, biasIndeterminateBand = TIGHT_INDETERMINATE_BAND),
            )

        (judgement.result as FloorShortfall.Measured).biasDirection shouldBe BiasDirection.Indeterminate
    }

    @Test
    fun `tallyOf — 밴드 밖 표본은 밖으로 빠지고 안쪽 표본만 미달을 센다`() {
        val band = AssessmentBand(BigDecimal("0.90"), BigDecimal("1.10"))
        val samples = listOf(rate("0.5"), rate("1.0"), rate("1.06"), rate("1.20"))
        val critical = rate("1.05")

        val tally = tallyOf(samples, critical, band, ShortfallComparison.StrictlyGreater)

        tally.rawCount shouldBe 4
        tally.outsideBand shouldBe 2
        tally.qualifiedDenominator shouldBe 2
        tally.shortfallNumerator shouldBe 1
    }
}
