package bidvector.decision.priority.derive

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate

/** [DerivationPolicyData] 불변식(scope.md ⑧, 위협 모델 (c)) + 출하 값 대조. */
class DerivationPolicyDataTest {
    @Test
    fun `marginWeights 가 MarginComponent 전 값을 덮지 않으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_DERIVATION_POLICY.copy(marginWeights = TEST_DERIVATION_POLICY.marginWeights - MarginComponent.Capacity)
        }
    }

    @Test
    fun `marginWeights 합이 1 이 아니면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_DERIVATION_POLICY.copy(
                marginWeights = TEST_DERIVATION_POLICY.marginWeights + (MarginComponent.Capacity to BigDecimal("0.11")),
            )
        }
    }

    @Test
    fun `complexityWeights 가 ComplexitySignal 전 값을 덮지 않으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_DERIVATION_POLICY.copy(
                complexityWeights = TEST_DERIVATION_POLICY.complexityWeights - ComplexitySignal.Capacity,
            )
        }
    }

    @Test
    fun `complexityWeights 합이 1 이 아니면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_DERIVATION_POLICY.copy(
                complexityWeights =
                    TEST_DERIVATION_POLICY.complexityWeights + (ComplexitySignal.Capacity to BigDecimal("0.11")),
            )
        }
    }

    @Test
    fun `가중치가 0 이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            TEST_DERIVATION_POLICY.copy(
                marginWeights =
                    TEST_DERIVATION_POLICY.marginWeights +
                        mapOf(
                            MarginComponent.Capacity to BigDecimal.ZERO,
                            MarginComponent.RecommendedRate to BigDecimal("0.45"),
                        ),
            )
        }
    }

    @Test
    fun `noDeadlineUrgency 가 1 을 넘으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> { TEST_DERIVATION_POLICY.copy(noDeadlineUrgency = BigDecimal("1.01")) }
    }

    @Test
    fun `noDeadlineUrgency 가 음수면 생성 실패`() {
        shouldThrow<IllegalArgumentException> { TEST_DERIVATION_POLICY.copy(noDeadlineUrgency = BigDecimal("-0.01")) }
    }

    @Test
    fun `noDeadlineComplexity 가 1 을 넘으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> { TEST_DERIVATION_POLICY.copy(noDeadlineComplexity = BigDecimal("1.01")) }
    }

    @Test
    fun `alignmentTolerance 가 0 이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> { TEST_DERIVATION_POLICY.copy(alignmentTolerance = BigDecimal.ZERO) }
    }

    @Test
    fun `keywordBase 가 1 을 넘으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> { TEST_DERIVATION_POLICY.copy(keywordBase = BigDecimal("1.01")) }
    }

    @Test
    fun `keywordStep 이 음수면 생성 실패`() {
        shouldThrow<IllegalArgumentException> { TEST_DERIVATION_POLICY.copy(keywordStep = BigDecimal("-0.01")) }
    }

    @Test
    fun `TEST_DERIVATION_POLICY 는 양성 대조로 생성된다`() {
        val marginSum = TEST_DERIVATION_POLICY.marginWeights.values.fold(BigDecimal.ZERO, BigDecimal::add)
        val complexitySum = TEST_DERIVATION_POLICY.complexityWeights.values.fold(BigDecimal.ZERO, BigDecimal::add)
        marginSum.setScale(4, RoundingMode.HALF_UP) shouldBe BigDecimal("1.0000")
        complexitySum.setScale(4, RoundingMode.HALF_UP) shouldBe BigDecimal("1.0000")
    }

    // ---- 출하 인스턴스(설계 검토 (1) 「출하 정책을 test 가 직접 읽는다」) ----
    // legacy 값 그대로(D-4B5-2 예외 외)

    @Test
    fun `DERIVATION_POLICY 출하 urgency 밴드는 legacy allocation_py 값과 같다`() {
        val policy = DERIVATION_POLICY.resolve(LocalDate.of(2026, 9, 10)).shouldBeResolved()

        resolveBand(Duration.ofHours(6), policy.urgencyLadder) shouldBe BigDecimal("1.0")
        resolveBand(Duration.ofHours(24), policy.urgencyLadder) shouldBe BigDecimal("0.8")
        resolveBand(Duration.ofHours(72), policy.urgencyLadder) shouldBe BigDecimal("0.55")
        policy.urgencyLadder.beyondBandsScore shouldBe BigDecimal("0.25")
        policy.noDeadlineUrgency shouldBe BigDecimal("0.3")
    }

    @Test
    fun `DERIVATION_POLICY 출하 complexity 밴드·가중치는 legacy score_tables_py 값과 같다`() {
        val policy = DERIVATION_POLICY.resolve(LocalDate.of(2026, 9, 10)).shouldBeResolved()

        resolveBand(500_000_000L, policy.complexityBudgetLadder) shouldBe BigDecimal("0.92")
        resolveBand(200_000_000L, policy.complexityBudgetLadder) shouldBe BigDecimal("0.78")
        resolveBand(100_000_000L, policy.complexityBudgetLadder) shouldBe BigDecimal("0.62")
        policy.complexityBudgetLadder.beyondBandsScore shouldBe BigDecimal("0.38")

        resolveBand(Duration.ofHours(6), policy.complexityDeadlineLadder) shouldBe BigDecimal("1.0")
        resolveBand(Duration.ofHours(24), policy.complexityDeadlineLadder) shouldBe BigDecimal("0.78")
        resolveBand(Duration.ofHours(72), policy.complexityDeadlineLadder) shouldBe BigDecimal("0.52")
        policy.complexityDeadlineLadder.beyondBandsScore shouldBe BigDecimal("0.24")
        policy.noDeadlineComplexity shouldBe BigDecimal("0.3")

        policy.complexityWeights[ComplexitySignal.Budget] shouldBe BigDecimal("0.30")
        policy.complexityWeights[ComplexitySignal.Keyword] shouldBe BigDecimal("0.25")
        policy.complexityWeights[ComplexitySignal.Deadline] shouldBe BigDecimal("0.15")
        policy.complexityWeights[ComplexitySignal.LoadRatio] shouldBe BigDecimal("0.10")
        policy.complexityWeights[ComplexitySignal.Match] shouldBe BigDecimal("0.10")
        policy.complexityWeights[ComplexitySignal.Capacity] shouldBe BigDecimal("0.10")
        policy.keywordBase shouldBe BigDecimal("0.24")
        policy.keywordStep shouldBe BigDecimal("0.08")
    }

    @Test
    fun `DERIVATION_POLICY 출하 margin 가중치·alignmentTolerance 는 legacy scoring_py 값과 같다`() {
        val policy = DERIVATION_POLICY.resolve(LocalDate.of(2026, 9, 10)).shouldBeResolved()

        policy.marginWeights[MarginComponent.RecommendedRate] shouldBe BigDecimal("0.35")
        policy.marginWeights[MarginComponent.FloorHeadroom] shouldBe BigDecimal("0.20")
        policy.marginWeights[MarginComponent.PredictionAlignment] shouldBe BigDecimal("0.20")
        policy.marginWeights[MarginComponent.PriceFitness] shouldBe BigDecimal("0.15")
        policy.marginWeights[MarginComponent.Capacity] shouldBe BigDecimal("0.10")
        policy.alignmentTolerance shouldBe BigDecimal("0.12")
    }

    @Test
    fun `DERIVATION_POLICY 출하 budgetCaptureRounding 은 scaleDigits 6·HALF_UP 이다(verifier r1 F-2)`() {
        val policy = DERIVATION_POLICY.resolve(LocalDate.of(2026, 9, 10)).shouldBeResolved()

        policy.budgetCaptureRounding.scaleDigits shouldBe 6
        policy.budgetCaptureRounding.mode shouldBe RoundingMode.HALF_UP
    }

    private fun Resolution<DerivationPolicyData>.shouldBeResolved(): DerivationPolicyData {
        check(this is Resolution.Resolved<DerivationPolicyData>) {
            "test 설정 오류 — Initial 은 항상 Resolved 다"
        }
        return value
    }
}
