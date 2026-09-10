package bidvector.decision.priority

import bidvector.decision.MlUnavailableReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * `composePriority` acceptance(scope.md ③, 조사 §1.1). 표본 넷은 `_workspace/m2-2e/
 * 01_scout_opportunity_scoring.md` §1.1 산식(가중합 − penalty, `clamp01`)과 `PRIORITY_POLICY`
 * 초기값(재정규화 가중치)을 손으로 곱셈·나눗셈해 얻은 값이다 — 구현 코드 경로를 재사용하지
 * 않는다. 나눗셈이 낀 표본(2)은 `MathContext(20)` 정밀도까지 손으로 맞추는 대신
 * epsilon(1e-6) 비교로 대조한다([closeTo]) — 관용은 실제 정밀도(20 유효숫자)보다 훨씬
 * 느슨해 산식 오류(가중치 누락·재정규화 생략 등)는 여전히 잡는다.
 */
class PriorityCompositionTest {
    // ---- 전수 표(legacy 값 표본 넷, 조사 §1.1 손계산 대조) ----

    @Test
    fun `표본1 — 다섯 성분 전부 Present, penalty 입력 없음`() {
        val outcome = composePriority(fullInputs(), TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        withClue("priority=${outcome.priority.value}") {
            outcome.priority.value.closeTo(BigDecimal("0.658350")) shouldBe true
        }
        outcome.usedComponents shouldBe Component.entries.toSet()
        outcome.droppedComponents shouldBe emptySet()
        outcome.appliedPenalties shouldBe emptyMap()
    }

    @Test
    fun `표본2 — competitiveness Absent, 나머지 넷의 가중치 합으로 재정규화`() {
        val outcome = composePriority(fullInputs(competitiveness = null), TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        outcome.priority.value.closeTo(BigDecimal("0.682705")) shouldBe true
        outcome.droppedComponents shouldBe setOf(Component.Competitiveness)
        outcome.usedComponents shouldBe
            setOf(Component.Match, Component.Urgency, Component.BudgetCapture, Component.ExpectedMargin)
        // ≠ 0 대입 계산(competitiveness=0 으로 두고 재정규화 없이 계산하면 0.59170 이 나온다 — 다른 값).
        outcome.priority.value.closeTo(BigDecimal("0.591700")) shouldBe false
    }

    @Test
    fun `표본3 — match 만 Present 면 priority 는 match 값과 같다(D-4B4-1)`() {
        val outcome =
            composePriority(
                fullInputs(urgency = null, competitiveness = null, budgetCapture = null, expectedMargin = null),
                TEST_PRIORITY_POLICY,
            )

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        outcome.priority.value.closeTo(BigDecimal("0.800000")) shouldBe true
        outcome.usedComponents shouldBe setOf(Component.Match)
        outcome.droppedComponents shouldBe
            setOf(Component.Urgency, Component.Competitiveness, Component.BudgetCapture, Component.ExpectedMargin)
    }

    @Test
    fun `표본4 — 다섯 성분 Present + penalty 셋 전부 적용`() {
        val inputs = fullInputs(loadRatio = "0.50", workload = "0.30", complexity = "0.80")
        val outcome = composePriority(inputs, TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        outcome.priority.value.closeTo(BigDecimal("0.487350")) shouldBe true
        outcome.appliedPenalties.getValue(PenaltyKind.LoadRatio).closeTo(BigDecimal("0.09000")) shouldBe true
        outcome.appliedPenalties.getValue(PenaltyKind.Workload).closeTo(BigDecimal("0.03600")) shouldBe true
        outcome.appliedPenalties.getValue(PenaltyKind.Complexity).closeTo(BigDecimal("0.04500")) shouldBe true
    }

    // ---- match 부재(D-4B4-2, 위협 모델 (6)(g)) ----

    @Test
    fun `match Absent 면 즉시 Unavailable 이고 사유를 그대로 옮긴다`() {
        val inputs = fullInputs().copy(match = ScoreFact.Absent(MlUnavailableReason.ContractViolation))

        val outcome = composePriority(inputs, TEST_PRIORITY_POLICY)

        outcome shouldBe PriorityOutcome.Unavailable(MlUnavailableReason.ContractViolation)
    }

    @Test
    fun `전부 Absent 면 match 부재만으로 Unavailable 이다(0 이 아니다, 위협 모델 6)`() {
        val inputs =
            fullInputs(urgency = null, competitiveness = null, budgetCapture = null, expectedMargin = null)
                .copy(match = ScoreFact.Absent(MlUnavailableReason.ScoreNotProvided))

        val outcome = composePriority(inputs, TEST_PRIORITY_POLICY)

        outcome shouldBe PriorityOutcome.Unavailable(MlUnavailableReason.ScoreNotProvided)
    }

    // ---- penalty 부재 제외(「모르면 그 항이 없다」, 위협 모델 (8)) ----

    @Test
    fun `loadRatio 만 Absent 면 workload 항만 appliedPenalties 에 실린다`() {
        val outcome = composePriority(fullInputs(workload = "0.30"), TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        outcome.appliedPenalties.keys shouldBe setOf(PenaltyKind.Workload)
    }

    @Test
    fun `complexity 가 threshold 이하면 penalty 항목은 0 으로 기록된다(부재가 아니다)`() {
        val outcome = composePriority(fullInputs(complexity = "0.40"), TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        outcome.appliedPenalties.getValue(PenaltyKind.Complexity) shouldBe BigDecimal.ZERO
        outcome.appliedPenalties.keys shouldBe setOf(PenaltyKind.Complexity)
    }

    @Test
    fun `penalty 입력이 전부 Absent 면 appliedPenalties 는 비어있다(0 penalty 로 채우지 않는다)`() {
        val outcome = composePriority(fullInputs(), TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        outcome.appliedPenalties shouldBe emptyMap()
    }

    // ---- clamp 경계(위협 모델 (9)) ----

    @Test
    fun `penalty 가 가중합을 넘으면 priority 는 0 으로 clamp 된다`() {
        val inputs =
            fullInputs(
                match = "0.10",
                urgency = null,
                competitiveness = null,
                budgetCapture = null,
                expectedMargin = null,
                loadRatio = "1.0",
                workload = "1.0",
                complexity = "1.0",
            )

        val outcome = composePriority(inputs, TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        outcome.priority.value shouldBe BigDecimal.ZERO
    }

    // ---- F-2 — loadRatio 는 UnitScore 라 음수·>1 이 생성 단계에서 거부된다 ----

    @Test
    fun `loadRatio 가 음수면 PriorityInputs 를 만들 수 없다(F-2)`() {
        shouldThrow<IllegalArgumentException> {
            fullInputs(loadRatio = "-1.0")
        }
    }

    @Test
    fun `loadRatio 가 1 을 넘으면 PriorityInputs 를 만들 수 없다(F-2)`() {
        shouldThrow<IllegalArgumentException> {
            fullInputs(loadRatio = "1.01")
        }
    }

    @Test
    fun `penalty 없이 다섯 성분이 전부 1 이면 priority 는 1 이다`() {
        val inputs =
            fullInputs(
                match = "1.00",
                urgency = "1.00",
                competitiveness = "1.00",
                budgetCapture = "1.00",
                expectedMargin = "1.00",
            )

        val outcome = composePriority(inputs, TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<PriorityOutcome.Composed>()
        outcome.priority.value.closeTo(BigDecimal("1.000000")) shouldBe true
    }
}
