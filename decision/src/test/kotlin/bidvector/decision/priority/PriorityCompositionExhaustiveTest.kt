package bidvector.decision.priority

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.MathContext

private val OPTIONAL_COMPONENTS =
    listOf(Component.Urgency, Component.Competitiveness, Component.BudgetCapture, Component.ExpectedMargin)

private val COMPONENT_VALUES =
    mapOf(
        Component.Match to BigDecimal("0.80"),
        Component.Urgency to BigDecimal("0.60"),
        Component.Competitiveness to BigDecimal("0.50"),
        Component.BudgetCapture to BigDecimal("0.40"),
        Component.ExpectedMargin to BigDecimal("0.70"),
    )

/** load `0.50×0.18` + workload `0.30×0.12` + complexity `min(0.12,(0.80−0.55)×0.18)`, 손계산. */
private val FIXED_TOTAL_PENALTY = BigDecimal("0.171000")

private fun subsetsOf(items: List<Component>): List<Set<Component>> =
    (0 until (1 shl items.size)).map { mask ->
        items.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }.toSet()
    }

/**
 * 독립 재구성 — `weightedScoreOf`를 부르지 않고 같은 산식(가중합 ÷ Present 가중치 합)을
 * 이 test 파일이 별도로 계산한다(verifier r1 T-1의 32 전수 방식과 같은 형태).
 */
private fun expectedWeightedScore(present: Set<Component>): BigDecimal {
    val weightSum = present.fold(BigDecimal.ZERO) { acc, c -> acc + TEST_PRIORITY_POLICY.weights.getValue(c) }
    val numerator =
        present.fold(BigDecimal.ZERO) { acc, c ->
            acc + TEST_PRIORITY_POLICY.weights.getValue(c) * COMPONENT_VALUES.getValue(c)
        }
    return numerator.divide(weightSum, MathContext(20))
}

private fun clampToUnit(value: BigDecimal): BigDecimal =
    when {
        value < BigDecimal.ZERO -> BigDecimal.ZERO
        value > BigDecimal.ONE -> BigDecimal.ONE
        else -> value
    }

/**
 * F-3(verifier r1 low) — 표본 하나(표본2)만으로는 Absent 조합의 **값**을 대조하지 못한다.
 * `match`를 상시 Present로 두고 나머지 넷의 **부분집합 16개 전부**를 순회해, 각 조합의
 * 재정규화 결과를 손계산(위 [expectedWeightedScore])과 대조한다 — penalty 셋은 상시
 * Present로 고정해 조합마다 상수([FIXED_TOTAL_PENALTY])로 뺀다.
 */
class PriorityCompositionExhaustiveTest {
    @Test
    fun `성분 부분집합 16 전수 대조(match 상시 Present, penalty 셋 상시 Present)`() {
        subsetsOf(OPTIONAL_COMPONENTS).forEach { presentOptional ->
            val present = setOf(Component.Match) + presentOptional
            val inputs =
                fullInputs(
                    urgency = valueOrNull(Component.Urgency, presentOptional),
                    competitiveness = valueOrNull(Component.Competitiveness, presentOptional),
                    budgetCapture = valueOrNull(Component.BudgetCapture, presentOptional),
                    expectedMargin = valueOrNull(Component.ExpectedMargin, presentOptional),
                    loadRatio = "0.50",
                    workload = "0.30",
                    complexity = "0.80",
                )

            val outcome = composePriority(inputs, TEST_PRIORITY_POLICY)

            withClue("present=$present") {
                outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
                val expected = clampToUnit(expectedWeightedScore(present) - FIXED_TOTAL_PENALTY)
                outcome.priority.value.closeTo(expected) shouldBe true
                outcome.usedComponents shouldBe present
                outcome.droppedComponents shouldBe (Component.entries.toSet() - present)
            }
        }
    }
}

private fun valueOrNull(
    component: Component,
    present: Set<Component>,
): String? = if (component in present) COMPONENT_VALUES.getValue(component).toPlainString() else null
