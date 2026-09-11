package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import bidvector.decision.priority.clamp01
import bidvector.sharedkernel.Resolution
import java.math.BigDecimal
import java.math.MathContext

/** 재정규화·headroom·alignment 나눗셈 정밀도 — `PriorityComposition.WEIGHT_DIVISION_CONTEXT` 류와 같은 값. */
private val DERIVE_DIVISION_CONTEXT = MathContext(20)

/**
 * 재정규화 가중합(scope.md ③④ 공용) — 4B-4 `weightedScoreOf`(`priority` 패키지, `private`)
 * 와 같은 규율이지만 그 함수를 복사하지 않는다(4B-4 파일 편집 금지, `derive` 패키지에
 * 별도로 둔다). [present] 가 [weights] 의 부분집합이면 빠진 항의 가중치를 분모에서 제외해
 * 남은 항만으로 재정규화한다(sentinel 금지 — 빠진 항을 0 으로 두지 않는다).
 */
internal fun <K> renormalizedWeightedSum(
    present: Map<K, BigDecimal>,
    weights: Map<K, BigDecimal>,
): BigDecimal {
    require(present.isNotEmpty()) { "present 는 비어 있을 수 없다" }
    val weightSum = present.keys.fold(BigDecimal.ZERO) { acc, key -> acc + weights.getValue(key) }
    val numerator = present.entries.fold(BigDecimal.ZERO) { acc, (key, value) -> acc + weights.getValue(key) * value }
    return numerator.divide(weightSum, DERIVE_DIVISION_CONTEXT)
}

/**
 * ③ 기대 마진(scope.md, legacy `scoring.py:255-304` `_estimate_expected_margin_score`) —
 * 다섯 축 전부 non-null 이라 재정규화가 필요 없다(D-4B5-6, `MarginInputs` 자체를 못 만들면
 * 4B-6 이 이 함수를 부르지 않는다).
 */
fun deriveExpectedMargin(
    inputs: MarginInputs,
    policy: Resolution.Resolved<DerivationPolicyData>,
): UnitScore {
    val data = policy.value
    val floorHeadroom = floorHeadroomOf(inputs.recommendedRate.fraction, inputs.floorRate?.fraction)
    val alignment = alignmentOf(inputs.recommendedRate.fraction, inputs.predictedRate.fraction, data.alignmentTolerance)
    val present =
        mapOf(
            MarginComponent.RecommendedRate to inputs.recommendedRate.fraction,
            MarginComponent.FloorHeadroom to floorHeadroom,
            MarginComponent.PredictionAlignment to alignment,
            MarginComponent.PriceFitness to inputs.priceFitness.value,
            MarginComponent.Capacity to inputs.capacity.value,
        )
    return UnitScore(clamp01(renormalizedWeightedSum(present, data.marginWeights)))
}

/**
 * floor 가 없거나 0 이면 `recommended`(legacy `if floor_bid_rate > 0` 의 else 분기) —
 * floor = 1 이면 분모(`1 - floor`)가 0 이라 같은 값으로 되돌린다(설계 검토 우회 (6)).
 * `[MarginInputs]` 의 `init`(verifier r1 F-1)이 `floor ≤ 1` 을 이미 보장하므로 이 분기
 * 밖(`else`)에 오는 값은 항상 `0 < floor < 1` 이다.
 *
 * **legacy 와 값이 사실상 같다는 주장은 부정확하다(verifier r1 F-3, 정정)** — legacy 는
 * `floor = 1` 에서 분모를 `max(1e-6, 1-floor)` 로 두어 `(rec-1)/1e-6` → 큰 음수 →
 * `clamp01` → **0** 을 낸다(하한이 100% 라 여유 없음). 이 함수는 같은 입력에서
 * `recommended` 를 낸다 — 실측 차 `rec=0.95, floor=1.0` → V2 `0.7225` vs legacy `0.5325`.
 * **거동 자체는 scope.md ③ 이 명시 고정한 결정**(「floor = 1 이면 `rec`」)이라 계약
 * 위반이 아니다 — legacy 의 엡실론 분모(사실상 0 으로 접는 것)를 의도적으로 재현하지
 * 않고 `recommended` 로 둔 것이 이 slice 의 선택이다(`policy-values.md` §3 참고).
 */
private fun floorHeadroomOf(
    recommended: BigDecimal,
    floor: BigDecimal?,
): BigDecimal =
    when {
        floor == null || floor.signum() == 0 || floor.compareTo(BigDecimal.ONE) == 0 -> recommended
        else -> clamp01((recommended - floor).divide(BigDecimal.ONE - floor, DERIVE_DIVISION_CONTEXT))
    }

private fun alignmentOf(
    recommended: BigDecimal,
    predicted: BigDecimal,
    tolerance: BigDecimal,
): BigDecimal = clamp01(BigDecimal.ONE - (recommended - predicted).abs().divide(tolerance, DERIVE_DIVISION_CONTEXT))
