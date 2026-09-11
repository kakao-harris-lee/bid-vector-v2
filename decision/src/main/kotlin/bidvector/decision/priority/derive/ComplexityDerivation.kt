package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import bidvector.decision.priority.clamp01
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.export
import java.math.BigDecimal
import java.time.Duration

private val KEYWORD_SIGNAL_CAP: BigDecimal = BigDecimal.ONE

/**
 * ④ 실행 복잡도(scope.md, legacy `scoring.py:306-347` `_estimate_execution_complexity_score`)
 * — `budget`·`match`·`capacity` 부재는 그 항을 재정규화에서 뺀다(D-4B5-6, 4B-4 와 같은
 * 규율). `keywordHits`·`remaining`·`loadRatio` 는 [ComplexityInputs] 가 항상 값을 갖는다
 * (구조상 전 항 부재 불가, 위협 모델 우회 (8)).
 *
 * `budget` 의 밴드 비교는 `Money.export()` 로 읽은 원 단위 `Long` 매그니튜드를 정책
 * `Long` 임계(`500_000_000` 등)와 비교한다 — 두 `Money` 값을 서로 비교/스케일하는 것이
 * 아니라 하나의 `Money` 를 고정 정책 임계와 비교하는 것이라(우회 (9) 는 `Money` 끼리의
 * 나눗셈을 막는 것) `bidvector.decision.FloorTypes.AssessmentBand.contains` 가 `Rate.fraction`
 * 을 고정 경계와 비교하는 것과 같은 축이다 — vat/provenance 안전검사는 두 `Money` 를
 * 산술로 섞을 때만 필요하고, 정책 임계는 애초에 `Money` 가 아니라 순수 크기다.
 */
fun deriveExecutionComplexity(
    inputs: ComplexityInputs,
    policy: Resolution.Resolved<DerivationPolicyData>,
): ComplexityOutcome {
    val data = policy.value
    val keyword = keywordSignalOf(inputs.keywordHits, data.keywordBase, data.keywordStep)
    val deadline = deadlineSignalOf(inputs.remaining, data)

    val present =
        mutableMapOf(
            ComplexitySignal.Keyword to keyword,
            ComplexitySignal.Deadline to deadline,
            ComplexitySignal.LoadRatio to inputs.loadRatio.value,
        )
    inputs.budget?.let { present[ComplexitySignal.Budget] = budgetSignalOf(it, data.complexityBudgetLadder) }
    inputs.match?.let { present[ComplexitySignal.Match] = BigDecimal.ONE - it.value }
    inputs.capacity?.let { present[ComplexitySignal.Capacity] = BigDecimal.ONE - it.value }

    val score = clamp01(renormalizedWeightedSum(present, data.complexityWeights))
    return ComplexityOutcome(UnitScore(score), present.keys.toSet())
}

private fun budgetSignalOf(
    budget: BaseAmount,
    ladder: Ladder<Long>,
): BigDecimal = resolveBand(budget.export().won, ladder)

private fun keywordSignalOf(
    hits: KeywordHits,
    base: BigDecimal,
    step: BigDecimal,
): BigDecimal = minOf(KEYWORD_SIGNAL_CAP, base + step.multiply(BigDecimal(hits.count)))

private fun deadlineSignalOf(
    remaining: Duration?,
    data: DerivationPolicyData,
): BigDecimal = remaining?.let { resolveBand(it, data.complexityDeadlineLadder) } ?: data.noDeadlineComplexity
