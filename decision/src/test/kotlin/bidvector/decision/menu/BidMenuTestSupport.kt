package bidvector.decision.menu

import bidvector.decision.AssessmentBand
import bidvector.decision.FloorShortfallPolicyData
import bidvector.decision.ShortfallComparison
import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.RoundingPolicy
import bidvector.sharedkernel.VatTreatment
import java.math.BigDecimal
import java.math.RoundingMode

internal val MENU_TEST_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-bid-menu-policy")
internal val SHORTFALL_TEST_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-floor-shortfall-policy")

private val FULL_RANGE_BAND = AssessmentBand(BigDecimal("-1000000"), BigDecimal("1000000"))
private val TIGHT_INDETERMINATE_BAND = AssessmentBand(BigDecimal("0.999"), BigDecimal("1.001"))

internal fun fraction(value: String): Rate = Rate.ofFraction(BigDecimal(value))

internal fun testBase(won: Long = 1_000_000L): BaseAmount =
    BaseAmount(won, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Published(NoticeRound.of("000")))

/** 출처 미선언 기초금액 — 산술이 `UNDECLARED_PROVENANCE` 로 막히는 경로를 연다(올림 대상이 아니다). */
internal fun undeclaredBase(won: Long = 1_000_000L): BaseAmount =
    BaseAmount(won, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Undeclared)

internal fun testFloorRate(value: String): FloorRate =
    FloorRate(fraction(value), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))

/**
 * 인자 순서가 **값 오름차순**이다 — 공격이 가장 낮고 안전이 가장 높다(라운드 2 HIGH-1).
 * 읽는 순서와 값의 순서를 맞춰 호출부에서 축을 헷갈릴 자리를 없앤다.
 */
internal fun testRates(
    aggressive: String,
    recommended: String,
    safe: String,
): StanceRates =
    StanceRates(safe = fraction(safe), recommended = fraction(recommended), aggressive = fraction(aggressive))

internal fun testSamples(vararg values: String): List<AssessmentRate> =
    values.map { AssessmentRate.observed(fraction(it)) }

/**
 * 하한 미달 정책 — `FloorShortfallKernelTest` 의 `policyOf` 와 같은 값이다. 메뉴 커널은 이
 * 정책을 **해석하지 않고** DEC-04 커널에 그대로 넘기므로, 여기서 값을 다르게 둘 이유가 없다.
 */
internal fun testShortfallPolicy(minAssessmentSamples: Int = 150): Resolution.Resolved<FloorShortfallPolicyData> =
    Resolution.Resolved(
        FloorShortfallPolicyData(
            minAssessmentSamples = minAssessmentSamples,
            minAssessmentSamplesRationale = "통계적 편의(운영자 승인 2026-08-26)",
            denominatorBand = FULL_RANGE_BAND,
            shortfallComparison = ShortfallComparison.StrictlyGreater,
            biasIndeterminateBand = TIGHT_INDETERMINATE_BAND,
            criticalRateRounding = RoundingPolicy(6, RoundingMode.HALF_UP),
        ),
        SHORTFALL_TEST_VERSION,
    )

internal fun testMenuPolicy(
    reading: FloorRateReading = FloorRateReading.Undecided,
    mode: RoundingMode = RoundingMode.HALF_UP,
): Resolution.Resolved<BidMenuPolicyData> =
    Resolution.Resolved(
        BidMenuPolicyData(floorRateReading = reading, amountRounding = RoundingPolicy(0, mode)),
        MENU_TEST_VERSION,
    )

internal fun testInput(
    base: BaseAmount = testBase(),
    rates: StanceRates = testRates("0.90", "0.93", "0.96"),
    publishedFloorRate: FloorRate? = testFloorRate("0.88"),
    samples: List<AssessmentRate> = emptyList(),
    shortfallPolicy: Resolution.Resolved<FloorShortfallPolicyData> = testShortfallPolicy(),
): BidMenuInput =
    BidMenuInput(
        base = base,
        rates = rates,
        publishedFloorRate = publishedFloorRate,
        assessmentSamples = samples,
        shortfallPolicy = shortfallPolicy,
    )

internal fun atOrAbove(): FloorRateReading = FloorRateReading.AsBidRateLowerBound(FloorBoundComparison.AtOrAbove)

internal fun strictlyAbove(): FloorRateReading =
    FloorRateReading.AsBidRateLowerBound(FloorBoundComparison.StrictlyAbove)
