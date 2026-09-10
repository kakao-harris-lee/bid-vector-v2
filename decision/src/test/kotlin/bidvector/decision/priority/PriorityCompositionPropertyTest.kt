package bidvector.decision.priority

import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.random.Random

private val unitFraction: Arb<BigDecimal> = Arb.bigDecimal(BigDecimal.ZERO, BigDecimal.ONE)
private val nonNegativeDelta: Arb<BigDecimal> = Arb.bigDecimal(BigDecimal.ZERO, BigDecimal("0.5"))
private val loadRatioRange: Arb<BigDecimal> = Arb.bigDecimal(BigDecimal.ZERO, BigDecimal("2.0"))

private fun composed(inputs: PriorityInputs): PriorityOutcome.Composed {
    val outcome = composePriority(inputs, TEST_PRIORITY_POLICY)
    outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
    return outcome
}

/**
 * `composePriority` 순수 성질(scope.md 설계 검토 (5) 1, 위협 모델 (1)(7)(9)). 값 표본
 * (RED, `PriorityCompositionTest`)과 달리 여기는 임의 입력에서도 항상 성립해야 하는
 * 불변식만 잰다.
 */
class PriorityCompositionPropertyTest {
    @Test
    fun `가중치 맵의 구성 순서가 결과에 영향을 주지 않는다(셔플, 위협 모델 7)`() {
        runBlocking {
            checkAll(Arb.long(0L..1_000_000L)) { seed ->
                val shuffledWeights =
                    TEST_PRIORITY_POLICY.weights.entries
                        .shuffled(Random(seed))
                        .associate { it.key to it.value }
                val shuffledPolicy = TEST_PRIORITY_POLICY.copy(weights = shuffledWeights)

                val baseline = composePriority(fullInputs(), TEST_PRIORITY_POLICY)
                val shuffled = composePriority(fullInputs(), shuffledPolicy)

                shuffled shouldBe baseline
            }
        }
    }

    @Test
    fun `전부 Present 면 재정규화가 항등이다(가중치 합 1 이므로 직접 가중합과 같다)`() {
        runBlocking {
            checkAll(unitFraction, unitFraction, unitFraction, unitFraction, unitFraction) { m, u, c, b, e ->
                val inputs =
                    fullInputs(
                        match = m.toPlainString(),
                        urgency = u.toPlainString(),
                        competitiveness = c.toPlainString(),
                        budgetCapture = b.toPlainString(),
                        expectedMargin = e.toPlainString(),
                    )
                val outcome = composed(inputs)
                val valueOf: (Component) -> BigDecimal = { component ->
                    when (component) {
                        Component.Match -> m
                        Component.Urgency -> u
                        Component.Competitiveness -> c
                        Component.BudgetCapture -> b
                        Component.ExpectedMargin -> e
                    }
                }
                val direct =
                    TEST_PRIORITY_POLICY.weights.entries.fold(BigDecimal.ZERO) { acc, (component, weight) ->
                        acc + weight * valueOf(component)
                    }

                outcome.priority.value.closeTo(direct) shouldBe true
            }
        }
    }

    @Test
    fun `penalty 가 없으면 성분 하나를 올릴 때 priority 는 감소하지 않는다(단조)`() {
        runBlocking {
            checkAll(unitFraction, nonNegativeDelta) { base, delta ->
                val bumped = (base + delta).min(BigDecimal.ONE)
                val before = composed(fullInputs(match = base.toPlainString()))
                val after = composed(fullInputs(match = bumped.toPlainString()))

                after.priority.value shouldBeGreaterThanOrEqualTo before.priority.value
            }
        }
    }

    @Test
    fun `Composed 결과의 priority 는 항상 0 이상 1 이하다`() {
        runBlocking {
            checkAll(unitFraction, Arb.list(unitFraction, 0..4), loadRatioRange) { match, others, loadRatio ->
                val inputs =
                    fullInputs(
                        match = match.toPlainString(),
                        urgency = others.getOrNull(0)?.toPlainString(),
                        competitiveness = others.getOrNull(1)?.toPlainString(),
                        budgetCapture = others.getOrNull(2)?.toPlainString(),
                        expectedMargin = others.getOrNull(3)?.toPlainString(),
                        loadRatio = loadRatio.toPlainString(),
                    )

                val outcome = composed(inputs)

                (outcome.priority.value >= BigDecimal.ZERO && outcome.priority.value <= BigDecimal.ONE) shouldBe true
            }
        }
    }
}
