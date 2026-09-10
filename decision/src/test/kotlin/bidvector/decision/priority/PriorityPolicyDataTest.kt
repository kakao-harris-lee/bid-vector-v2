package bidvector.decision.priority

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private fun weightsOf(vararg overrides: Pair<Component, BigDecimal>): Map<Component, BigDecimal> =
    (TEST_PRIORITY_POLICY.weights + overrides.toMap())

// ---- F-1(verifier r1 medium) — 출하 값을 재정규화 산식으로 독립 재구성(리터럴 복제 아님) ----

private val LEGACY_PROBABILITY_WEIGHT = BigDecimal("0.40")

/** legacy 여섯 가중합(조사 §1.1 `allocation.py:38-43`)에서 확률 축을 뺀 다섯. */
private val LEGACY_COMPONENT_WEIGHTS: Map<Component, BigDecimal> =
    mapOf(
        Component.Match to BigDecimal("0.23"),
        Component.Urgency to BigDecimal("0.14"),
        Component.Competitiveness to BigDecimal("0.08"),
        Component.BudgetCapture to BigDecimal("0.06"),
        Component.ExpectedMargin to BigDecimal("0.09"),
    )

private const val WEIGHT_RENORMALIZATION_SCALE = 4

/**
 * `policy-values.md` §1 의 유도(÷(1-확률가중치) → scale 4 반올림 → 잔차를 `Match` 에
 * 흡수)를 이 test 가 독립적으로 재계산한다 — `PRIORITY_POLICY` 리터럴을 그대로 베끼지
 * 않는다. 값이 바뀌면(예: verifier r1 재현 — `Match` 를 0.3834→0.5834) 이 산식과 어긋나
 * F-1 이 다시 열린다.
 */
private fun renormalizedLegacyWeights(): Map<Component, BigDecimal> {
    val divisor = BigDecimal.ONE - LEGACY_PROBABILITY_WEIGHT
    val rounded =
        LEGACY_COMPONENT_WEIGHTS.mapValues { (_, weight) ->
            weight.divide(divisor, WEIGHT_RENORMALIZATION_SCALE, RoundingMode.HALF_UP)
        }
    val target = BigDecimal.ONE.setScale(WEIGHT_RENORMALIZATION_SCALE)
    val residual = target - rounded.values.fold(BigDecimal.ZERO, BigDecimal::add)
    return rounded + (Component.Match to rounded.getValue(Component.Match) + residual)
}

/** `PriorityPolicyData`·`PRIORITY_POLICY` 불변식(scope.md ②, 위협 모델 (2)). */
class PriorityPolicyDataTest {
    @Test
    fun `weights 키가 Component 전 값을 덮지 않으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_PRIORITY_POLICY.copy(weights = TEST_PRIORITY_POLICY.weights - Component.ExpectedMargin)
        }
    }

    @Test
    fun `weights 합이 0_9999 면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_PRIORITY_POLICY.copy(weights = weightsOf(Component.Match to BigDecimal("0.3833")))
        }
    }

    @Test
    fun `weights 합이 1_0001 이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_PRIORITY_POLICY.copy(weights = weightsOf(Component.Match to BigDecimal("0.3835")))
        }
    }

    @Test
    fun `weights 합이 1_0000 이면 생성된다(양성 대조)`() {
        TEST_PRIORITY_POLICY.weights.values.fold(BigDecimal.ZERO, BigDecimal::add) shouldBe BigDecimal("1.0000")
    }

    @Test
    fun `가중치가 0 이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_PRIORITY_POLICY.copy(
                weights = weightsOf(Component.Match to BigDecimal.ZERO, Component.Urgency to BigDecimal("0.6167")),
            )
        }
    }

    @Test
    fun `LoadPenaltyPolicy 음수 ratioWeight 는 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            LoadPenaltyPolicy(ratioWeight = BigDecimal("-0.01"), workloadWeight = BigDecimal("0.12"))
        }
    }

    @Test
    fun `LoadPenaltyPolicy 음수 workloadWeight 는 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            LoadPenaltyPolicy(ratioWeight = BigDecimal("0.18"), workloadWeight = BigDecimal("-0.01"))
        }
    }

    @Test
    fun `ComplexityPenaltyPolicy threshold 가 1 을 넘으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            ComplexityPenaltyPolicy(
                threshold = BigDecimal("1.01"),
                slope = BigDecimal("0.18"),
                cap = BigDecimal("0.12"),
            )
        }
    }

    @Test
    fun `ComplexityPenaltyPolicy 음수 slope 는 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            ComplexityPenaltyPolicy(
                threshold = BigDecimal("0.55"),
                slope = BigDecimal("-0.01"),
                cap = BigDecimal("0.12"),
            )
        }
    }

    @Test
    fun `categoryOffsetMin 이 categoryOffsetMax 보다 크면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_PRIORITY_POLICY.copy(categoryOffsetMin = BigDecimal("0.21"), categoryOffsetMax = BigDecimal("0.20"))
        }
    }

    @Test
    fun `normEpsilon 이 0 이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_PRIORITY_POLICY.copy(normEpsilon = BigDecimal.ZERO)
        }
    }

    // ---- 출하 인스턴스(설계 검토 (1) 「출하 정책을 test 가 직접 읽는다」) ----

    @Test
    fun `PRIORITY_POLICY 출하 인스턴스는 전 Component 를 덮고 합이 1 이다`() {
        val resolution = PRIORITY_POLICY.resolve(LocalDate.of(2026, 9, 10))

        val policy = resolution.shouldBeResolved()
        policy.weights.keys shouldBe Component.entries.toSet()
        policy.weights.values.fold(BigDecimal.ZERO, BigDecimal::add) shouldBe BigDecimal("1.0000")
    }

    @Test
    fun `PRIORITY_POLICY 출하 가중치는 legacy 재정규화 산식과 값까지 일치한다(F-1)`() {
        val policy = PRIORITY_POLICY.resolve(LocalDate.of(2026, 9, 10)).shouldBeResolved()
        val expected = renormalizedLegacyWeights()

        Component.entries.forEach { component ->
            withClue("$component: 출하=${policy.weights.getValue(component)} 기대=${expected.getValue(component)}") {
                policy.weights.getValue(component).compareTo(expected.getValue(component)) shouldBe 0
            }
        }
    }

    private fun Resolution<PriorityPolicyData>.shouldBeResolved(): PriorityPolicyData {
        check(this is Resolution.Resolved<PriorityPolicyData>) { "test 설정 오류 — Initial 은 항상 Resolved 다" }
        return value
    }
}
