package bidvector.decision.priority.derive

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.RoundingPolicy
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

private const val WEIGHT_SUM_SCALE = 4
private val WEIGHT_SUM_TARGET: BigDecimal = BigDecimal.ONE.setScale(WEIGHT_SUM_SCALE)
private val UNIT_MIN: BigDecimal = BigDecimal.ZERO
private val UNIT_MAX: BigDecimal = BigDecimal.ONE

/** margin 가중합의 축 다섯(scope.md ③) — [DerivationPolicyData.marginWeights] 의 키. */
enum class MarginComponent {
    RecommendedRate,
    FloorHeadroom,
    PredictionAlignment,
    PriceFitness,
    Capacity,
}

/** 실행 복잡도 가중합의 축 여섯(scope.md ④) — [DerivationPolicyData.complexityWeights] 의 키. */
enum class ComplexitySignal {
    Budget,
    Keyword,
    Deadline,
    LoadRatio,
    Match,
    Capacity,
}

/**
 * 성분 파생 함수(4B-5) 정책 데이터(scope.md ⑧) — 밴드 셋(urgency·complexity-deadline·
 * complexity-budget)·가중치 표 둘(margin·complexity)·상수 다섯·[budgetCaptureRounding]
 * (`bidvector.sharedkernel.bidRateAgainst` 가 요구하는 [RoundingPolicy], `FloorShortfallPolicyData
 * .criticalRateRounding` 과 같은 이유로 이 슬롯이 있다 — D-4B5-4).
 *
 * **competitiveness 밴드·비율은 이 slice 가 만들지 않는다** — `deriveCompetitiveness`(scope.md
 * ⑤)는 shared-kernel 에 "임의 Money 를 정책 비율로 스케일"하는 연산이 없어(`Basis` 에 시장평균
 * 축이 없고 `BaseAmount.times(BidRate)` 하나뿐) 이 slice 밖으로 보고했다(checklist 「알려진
 * 제한」) — 소비자 없는 필드를 정책 데이터에 두지 않는다.
 */
data class DerivationPolicyData(
    val urgencyLadder: Ladder<Duration>,
    val complexityDeadlineLadder: Ladder<Duration>,
    val complexityBudgetLadder: Ladder<Long>,
    val marginWeights: Map<MarginComponent, BigDecimal>,
    val complexityWeights: Map<ComplexitySignal, BigDecimal>,
    val noDeadlineUrgency: BigDecimal,
    val noDeadlineComplexity: BigDecimal,
    val alignmentTolerance: BigDecimal,
    val keywordBase: BigDecimal,
    val keywordStep: BigDecimal,
    val budgetCaptureRounding: RoundingPolicy,
) {
    init {
        requireFullWeightMap(marginWeights, MarginComponent.entries.toSet(), "marginWeights")
        requireFullWeightMap(complexityWeights, ComplexitySignal.entries.toSet(), "complexityWeights")
        require(noDeadlineUrgency in UNIT_MIN..UNIT_MAX) {
            "noDeadlineUrgency 는 [0,1] 범위여야 한다: $noDeadlineUrgency"
        }
        require(noDeadlineComplexity in UNIT_MIN..UNIT_MAX) {
            "noDeadlineComplexity 는 [0,1] 범위여야 한다: $noDeadlineComplexity"
        }
        require(alignmentTolerance > UNIT_MIN) { "alignmentTolerance 는 0보다 커야 한다: $alignmentTolerance" }
        require(keywordBase in UNIT_MIN..UNIT_MAX) { "keywordBase 는 [0,1] 범위여야 한다: $keywordBase" }
        require(keywordStep >= UNIT_MIN) { "keywordStep 은 음수일 수 없다: $keywordStep" }
    }
}

/** 가중치 표 불변식(우회 (3) 「합 ≠ 1」) — 전 축을 덮고 각 값이 (0,1], 합이 scale 4 로 1이어야 한다. */
private fun <K> requireFullWeightMap(
    weights: Map<K, BigDecimal>,
    keys: Set<K>,
    label: String,
) {
    require(weights.keys == keys) { "$label 는 전 축을 덮어야 한다: ${weights.keys}" }
    weights.forEach { (key, weight) ->
        require(weight > UNIT_MIN && weight <= UNIT_MAX) { "$label.$key 는 (0,1] 범위여야 한다: $weight" }
    }
    val sum = weights.values.fold(BigDecimal.ZERO, BigDecimal::add)
    require(sum.setScale(WEIGHT_SUM_SCALE, RoundingMode.HALF_UP).compareTo(WEIGHT_SUM_TARGET) == 0) {
        "$label 합은 1(scale $WEIGHT_SUM_SCALE)이어야 한다: $sum"
    }
}

/**
 * 운영 정책 인스턴스 — **착수 시(2026-09-10) placeholder, 승인 대기**(`OPEN-4B5-POLICY-VALUES`).
 * 값은 legacy 산식 상수 그대로다(D-4B5-2 예외 외에는 4B-4 와 달리 확률 축을 빼는 재정규화가
 * 없다 — 이 축들은 legacy 에서부터 이미 독립 가중합이었다). 근거는
 * `reports/evidence/m4/4b5/policy-values.md`(legacy 좌표 대조).
 */
val DERIVATION_POLICY: EffectiveDatedPolicy<DerivationPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m4/4b5/policy-values.md — 착수 placeholder, 승인 대기 OPEN-4B5-POLICY-VALUES",
        entries =
            listOf(
                EffectiveFrom.Initial to
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
                    ),
            ),
    )
