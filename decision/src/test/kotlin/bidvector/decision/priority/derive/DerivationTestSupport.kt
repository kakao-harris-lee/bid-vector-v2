package bidvector.decision.priority.derive

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BidAmount
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Derived
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.RoundingPolicy
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.times
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * 재정규화 값(scope.md, `PriorityTestSupport.TEST_PRIORITY_POLICY` 관례) — main 의
 * `DERIVATION_POLICY` 를 그대로 읽지 않는다(출하 인스턴스 대조는 `DerivationPolicyDataTest`
 * 가 직접 `DERIVATION_POLICY.resolve(...)` 를 읽는다).
 */
internal val TEST_DERIVATION_POLICY =
    DerivationPolicyData(
        urgencyLadder =
            Ladder.Ascending(
                bands =
                    listOf(
                        Band(Duration.ofHours(6), BigDecimal("1.0")),
                        Band(Duration.ofHours(24), BigDecimal("0.8")),
                        Band(Duration.ofHours(72), BigDecimal("0.55")),
                    ),
                beyondBandsScore = BigDecimal("0.25"),
            ),
        complexityDeadlineLadder =
            Ladder.Ascending(
                bands =
                    listOf(
                        Band(Duration.ofHours(6), BigDecimal("1.0")),
                        Band(Duration.ofHours(24), BigDecimal("0.78")),
                        Band(Duration.ofHours(72), BigDecimal("0.52")),
                    ),
                beyondBandsScore = BigDecimal("0.24"),
            ),
        complexityBudgetLadder =
            Ladder.Descending(
                bands =
                    listOf(
                        Band(500_000_000L, BigDecimal("0.92")),
                        Band(200_000_000L, BigDecimal("0.78")),
                        Band(100_000_000L, BigDecimal("0.62")),
                    ),
                beyondBandsScore = BigDecimal("0.38"),
            ),
        marginWeights =
            mapOf(
                MarginComponent.RecommendedRate to BigDecimal("0.35"),
                MarginComponent.FloorHeadroom to BigDecimal("0.20"),
                MarginComponent.PredictionAlignment to BigDecimal("0.20"),
                MarginComponent.PriceFitness to BigDecimal("0.15"),
                MarginComponent.Capacity to BigDecimal("0.10"),
            ),
        complexityWeights =
            mapOf(
                ComplexitySignal.Budget to BigDecimal("0.30"),
                ComplexitySignal.Keyword to BigDecimal("0.25"),
                ComplexitySignal.Deadline to BigDecimal("0.15"),
                ComplexitySignal.LoadRatio to BigDecimal("0.10"),
                ComplexitySignal.Match to BigDecimal("0.10"),
                ComplexitySignal.Capacity to BigDecimal("0.10"),
            ),
        noDeadlineUrgency = BigDecimal("0.3"),
        noDeadlineComplexity = BigDecimal("0.3"),
        alignmentTolerance = BigDecimal("0.12"),
        keywordBase = BigDecimal("0.24"),
        keywordStep = BigDecimal("0.08"),
        budgetCaptureRounding = RoundingPolicy(scaleDigits = 6, mode = RoundingMode.HALF_UP),
    )

internal fun testPolicyVersion(source: String = "test"): PolicyVersion = PolicyVersion(EffectiveFrom.Initial, source)

internal fun resolvedTestPolicy(
    data: DerivationPolicyData = TEST_DERIVATION_POLICY,
): Resolution.Resolved<DerivationPolicyData> = Resolution.Resolved(data, testPolicyVersion())

internal fun testMoneyRoundingPolicy(): Resolution.Resolved<RoundingPolicy> =
    Resolution.Resolved(RoundingPolicy(scaleDigits = 0, mode = RoundingMode.HALF_UP), testPolicyVersion())

internal fun testBaseAmount(
    won: Long,
    vat: VatTreatment = VatTreatment.INCLUSIVE,
    provenance: Provenance = Provenance.OperatorDeclared,
): BaseAmount = BaseAmount(won, Currency.KRW, vat, provenance)

/**
 * 임의 `won` 값의 [BidAmount] — 생성자가 `internal`(shared-kernel 전용)이라 `decision` 모듈
 * test 는 유일한 공개 경로(`BaseAmount × BidRate` → `roundedWith`)로 만든다. 이 helper 는
 * `base = won`·`rate = 1.0` 을 골라 결과 `won` 이 정확히 목표값이 되게 한다(테스트 fixture
 * 구성일 뿐 deriveBudgetCapture 산식과 무관).
 */
internal fun testBidAmount(
    won: Long,
    vat: VatTreatment = VatTreatment.INCLUSIVE,
    provenance: Provenance = Provenance.OperatorDeclared,
): BidAmount {
    val measurement =
        (testBaseAmount(won, vat, provenance) * BidRate.recommended(Rate.ofFraction(BigDecimal.ONE)))
            .roundedWith(testMoneyRoundingPolicy())
    check(measurement is Measurement.Measured<Derived<BidAmount>>) { "test 설정 오류 — 항상 Measured 여야 한다: $measurement" }
    return measurement.value.value
}

internal fun rateOf(fraction: String): Rate = Rate.ofFraction(BigDecimal(fraction))
