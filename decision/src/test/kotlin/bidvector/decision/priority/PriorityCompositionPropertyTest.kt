package bidvector.decision.priority

import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.subsequence
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.random.Random

private val unitFraction: Arb<BigDecimal> = Arb.bigDecimal(BigDecimal.ZERO, BigDecimal.ONE)
private val nonNegativeDelta: Arb<BigDecimal> = Arb.bigDecimal(BigDecimal.ZERO, BigDecimal("0.5"))

/** F-2 — `loadRatio`가 `UnitScore`로 좁혀져 penalty 입력 셋 전부 `[0,1]`이다. */
private val unitFractionRatio: Arb<BigDecimal> = unitFraction

/** F-3 — `Arb.list`+`getOrNull` 접미사 패턴 대신 부분집합을 고르게 뽑는다(16 개 전부 도달 가능). */
private val optionalComponents =
    listOf(Component.Urgency, Component.Competitiveness, Component.BudgetCapture, Component.ExpectedMargin)

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
    fun `Composed 결과의 priority 는 항상 0 이상 1 이하다(F-3 — 부분집합 16 을 고르게 뽑는다)`() {
        runBlocking {
            checkAll(subsetCases) { case ->
                val inputs =
                    fullInputs(
                        match = case.match.toPlainString(),
                        urgency = valueIfPresent(Component.Urgency, case.present, case.values),
                        competitiveness = valueIfPresent(Component.Competitiveness, case.present, case.values),
                        budgetCapture = valueIfPresent(Component.BudgetCapture, case.present, case.values),
                        expectedMargin = valueIfPresent(Component.ExpectedMargin, case.present, case.values),
                        loadRatio = case.loadRatio.toPlainString(),
                    )

                val outcome = composed(inputs)

                (outcome.priority.value >= BigDecimal.ZERO && outcome.priority.value <= BigDecimal.ONE) shouldBe true
            }
        }
    }
}

/** F-3 — 성분 넷의 임의 값 조합(부분집합 판단은 [subsetCases]가 [SubsetCase.present]로 갖는다). */
private data class SubsetCase(
    val match: BigDecimal,
    val present: Set<Component>,
    val values: Map<Component, BigDecimal>,
    val loadRatio: BigDecimal,
)

private val optionalValues: Arb<Map<Component, BigDecimal>> =
    Arb.bind(unitFraction, unitFraction, unitFraction, unitFraction) { u, c, b, e ->
        mapOf(
            Component.Urgency to u,
            Component.Competitiveness to c,
            Component.BudgetCapture to b,
            Component.ExpectedMargin to e,
        )
    }

private val subsetCases: Arb<SubsetCase> =
    Arb.bind(
        unitFraction,
        Arb.subsequence(optionalComponents),
        optionalValues,
        unitFractionRatio,
    ) { match, present, values, loadRatio ->
        SubsetCase(match, present.toSet(), values, loadRatio)
    }

private fun valueIfPresent(
    component: Component,
    present: Set<Component>,
    values: Map<Component, BigDecimal>,
): String? = if (component in present) values.getValue(component).toPlainString() else null
