package bidvector.decision.priority

import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import java.math.BigDecimal
import java.math.MathContext

/** 재정규화 나눗셈 정밀도 — 1D `ProvenanceRules.RATIO_DIVISION_CONTEXT` 관례와 같은 값. */
private val WEIGHT_DIVISION_CONTEXT = MathContext(20)

/** penalty 항의 이름(scope.md ②·③, 조사 §1.1) — [PriorityOutcome.Composed.appliedPenalties] 의 키. */
enum class PenaltyKind {
    LoadRatio,
    Workload,
    Complexity,
}

/**
 * [composePriority] 결과(scope.md ③). `match` 가 없으면 조합 자체를 시도하지 않는다
 * (D-4B4-2) — [Unavailable] 이 그 상태다. [Composed] 는 무엇이 빠졌는지
 * ([droppedComponents])·penalty 가 실제로 적용됐는지([appliedPenalties])를 값으로
 * 남긴다(설계 검토 (3) 「미달로 채택」).
 */
sealed interface PriorityOutcome {
    data class Unavailable(
        val reason: MlUnavailableReason,
    ) : PriorityOutcome

    data class Composed(
        val priority: UnitScore,
        val usedComponents: Set<Component>,
        val droppedComponents: Set<Component>,
        val appliedPenalties: Map<PenaltyKind, BigDecimal>,
    ) : PriorityOutcome
}

/**
 * priority 가중합 + penalty 순수 커널(scope.md ③, 조사 §1.1 `allocation.py:103-112`).
 * `match` 가 [ScoreFact.Absent] 면 즉시 [PriorityOutcome.Unavailable] — 나머지 성분은
 * 평가하지 않는다(D-4B4-2, 임베딩 없이는 시도조차 하지 않는다). 나머지 넷 중
 * [ScoreFact.Absent] 인 성분은 가중합에서 빠지고 **그 성분들의 가중치 합으로
 * 재정규화**한다(D-4B4-1, §6.3 sentinel 금지 — 0 을 대입하지 않는다). penalty 세 항
 * ([PenaltyKind])도 같은 규율 — 입력이 [ScoreFact.Absent] 면 그 항을 안 센다
 * (「모르면 깎지 않는다」가 아니라 「모르면 그 항이 없다」).
 */
fun composePriority(
    inputs: PriorityInputs,
    policy: PriorityPolicyData,
): PriorityOutcome {
    val match = inputs.match
    if (match is ScoreFact.Absent) return PriorityOutcome.Unavailable(match.reason)

    val present = Component.entries.filter { inputs.factFor(it) is ScoreFact.Present }
    val weighted = weightedScoreOf(inputs, policy, present)

    val appliedPenalties = penaltiesOf(inputs, policy)
    val totalPenalty = appliedPenalties.values.fold(BigDecimal.ZERO, BigDecimal::add)

    return PriorityOutcome.Composed(
        priority = UnitScore(clamp01(weighted - totalPenalty)),
        usedComponents = present.toSet(),
        droppedComponents = Component.entries.toSet() - present.toSet(),
        appliedPenalties = appliedPenalties,
    )
}

private fun weightedScoreOf(
    inputs: PriorityInputs,
    policy: PriorityPolicyData,
    present: List<Component>,
): BigDecimal {
    val weightSum = present.fold(BigDecimal.ZERO) { acc, component -> acc + policy.weights.getValue(component) }
    val numerator =
        present.fold(BigDecimal.ZERO) { acc, component ->
            val fact = inputs.factFor(component) as ScoreFact.Present<UnitScore>
            acc + policy.weights.getValue(component) * fact.value.value
        }
    return numerator.divide(weightSum, WEIGHT_DIVISION_CONTEXT)
}

private fun penaltiesOf(
    inputs: PriorityInputs,
    policy: PriorityPolicyData,
): Map<PenaltyKind, BigDecimal> =
    listOfNotNull(
        presentValue(inputs.loadRatio)?.let { PenaltyKind.LoadRatio to it * policy.loadPenalty.ratioWeight },
        presentValue(inputs.workload)?.let { PenaltyKind.Workload to it.value * policy.loadPenalty.workloadWeight },
        presentValue(inputs.complexity)?.let {
            PenaltyKind.Complexity to complexityPenaltyOf(it.value, policy.complexityPenalty)
        },
    ).toMap()

private fun <T> presentValue(fact: ScoreFact<T>): T? = (fact as? ScoreFact.Present<T>)?.value

private fun complexityPenaltyOf(
    complexity: BigDecimal,
    policy: ComplexityPenaltyPolicy,
): BigDecimal =
    if (complexity <= policy.threshold) {
        BigDecimal.ZERO
    } else {
        minOf(policy.cap, (complexity - policy.threshold) * policy.slope)
    }
