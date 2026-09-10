package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import bidvector.decision.priority.closeTo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * ③ [deriveExpectedMargin](scope.md, legacy `scoring.py:255-304`
 * `_estimate_expected_margin_score`). 다섯 축 전부 non-null 이라 재정규화가 필요 없다
 * (D-4B5-6). 가중치는 `TEST_DERIVATION_POLICY.marginWeights` — 손계산 넷 + floor 0 + floor
 * 1 + alignment 경계(설계 검토 (5) 1).
 */
class ExpectedMarginDerivationTest {
    private val policy = resolvedTestPolicy()

    @Test
    fun `손계산 1 — floor 없음, 완전 정렬(alignment 1_0)`() {
        val inputs =
            MarginInputs(
                recommendedRate = rateOf("0.90"),
                floorRate = null,
                predictedRate = rateOf("0.90"),
                priceFitness = UnitScore(BigDecimal("0.80")),
                capacity = UnitScore(BigDecimal("0.60")),
            )
        // floorHeadroom = rec(0.90, floor 없음) · alignment = 1 - |0.90-0.90|/0.12 = 1.0
        // score = .90*.35 + .90*.20 + 1.0*.20 + .80*.15 + .60*.10 = 0.875
        val score = deriveExpectedMargin(inputs, policy)
        score.value.compareTo(BigDecimal("0.875")) shouldBe 0
    }

    @Test
    fun `손계산 2 — floor 0_5 이면 headroom 을 계산한다`() {
        val inputs =
            MarginInputs(
                recommendedRate = rateOf("0.90"),
                floorRate = rateOf("0.50"),
                predictedRate = rateOf("0.80"),
                priceFitness = UnitScore(BigDecimal("0.70")),
                capacity = UnitScore(BigDecimal("0.50")),
            )
        // floorHeadroom = clamp01((0.90-0.50)/(1-0.50)) = 0.8
        // alignment = 1 - |0.90-0.80|/0.12 = 1 - 0.833333... = 0.166667
        // score = .90*.35 + .80*.20 + .166667*.20 + .70*.15 + .50*.10 = 0.663333...
        val score = deriveExpectedMargin(inputs, policy)
        score.value.closeTo(BigDecimal("0.66333333")) shouldBe true
    }

    @Test
    fun `손계산 3 — floor 0 이면 headroom 은 rec 그대로(legacy else 분기)`() {
        val inputs =
            MarginInputs(
                recommendedRate = rateOf("0.70"),
                floorRate = rateOf("0.0"),
                predictedRate = rateOf("0.70"),
                priceFitness = UnitScore(BigDecimal("1.00")),
                capacity = UnitScore(BigDecimal("1.00")),
            )
        // floorHeadroom = rec(0.70, floor=0) · alignment = 1.0(완전 정렬)
        // score = .70*.35 + .70*.20 + 1.0*.20 + 1.00*.15 + 1.00*.10 = 0.7*.55 + .20 + .15 + .10 = 0.385+0.45=0.835
        val score = deriveExpectedMargin(inputs, policy)
        score.value.compareTo(BigDecimal("0.835")) shouldBe 0
    }

    @Test
    fun `우회 6 — floor 1 이면 분모 0 이라 headroom 은 rec 로 되돌린다`() {
        val inputs =
            MarginInputs(
                recommendedRate = rateOf("0.95"),
                floorRate = rateOf("1.0"),
                predictedRate = rateOf("0.95"),
                priceFitness = UnitScore(BigDecimal("0.50")),
                capacity = UnitScore(BigDecimal("0.50")),
            )
        // floorHeadroom = rec(0.95, floor=1 — 분모 0 회피) · alignment = 1.0
        // score = .95*.35 + .95*.20 + 1.0*.20 + .50*.15 + .50*.10 = 0.3325+0.19+0.20+0.075+0.05=0.8475
        val score = deriveExpectedMargin(inputs, policy)
        score.value.compareTo(BigDecimal("0.8475")) shouldBe 0
    }

    @Test
    fun `alignment 경계 — rec-pred 차이가 tolerance(0_12) 와 같으면 alignment 0`() {
        val inputs =
            MarginInputs(
                recommendedRate = rateOf("0.62"),
                floorRate = null,
                predictedRate = rateOf("0.50"),
                priceFitness = UnitScore(BigDecimal("0.0")),
                capacity = UnitScore(BigDecimal("0.0")),
            )
        // alignment = 1 - |0.62-0.50|/0.12 = 1 - 1.0 = 0
        // recommendedRate=.62, floorHeadroom=.62(floor 없음), priceFitness=0, capacity=0
        // score = .62*.35 + .62*.20 + 0*.20 + 0*.15 + 0*.10 = .217+.124 = .341
        val score = deriveExpectedMargin(inputs, policy)
        score.value.compareTo(BigDecimal("0.341")) shouldBe 0
    }

    @Test
    fun `alignment 경계 — 차이가 tolerance 를 넘으면 clamp01 로 0 미만이 되지 않는다`() {
        val inputs =
            MarginInputs(
                recommendedRate = rateOf("0.99"),
                floorRate = null,
                predictedRate = rateOf("0.10"),
                priceFitness = UnitScore(BigDecimal.ZERO),
                capacity = UnitScore(BigDecimal.ZERO),
            )
        // |0.99-0.10|=0.89 >> 0.12 → alignment 이 음수로 계산될 값이지만 clamp01 이 0 으로 자른다
        // score = .99*.35 + .99*.20 + 0*.20 + 0*.15 + 0*.10 = .3465+.198 = .5445
        val score = deriveExpectedMargin(inputs, policy)
        score.value.compareTo(BigDecimal("0.5445")) shouldBe 0
    }

    // ---- verifier r1 F-1 — Rate 상한(≤1) 생성 불변식 ----

    @Test
    fun `F-1 — recommendedRate 가 1 을 넘으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            MarginInputs(
                recommendedRate = rateOf("1.0000001"),
                floorRate = null,
                predictedRate = rateOf("0.90"),
                priceFitness = UnitScore(BigDecimal.ZERO),
                capacity = UnitScore(BigDecimal.ZERO),
            )
        }
    }

    @Test
    fun `F-1 — recommendedRate 가 정확히 1 이면 생성된다(경계 양성 대조)`() {
        MarginInputs(
            recommendedRate = rateOf("1.0"),
            floorRate = null,
            predictedRate = rateOf("1.0"),
            priceFitness = UnitScore(BigDecimal.ZERO),
            capacity = UnitScore(BigDecimal.ZERO),
        )
    }

    @Test
    fun `F-1 — floorRate 가 1 을 넘으면 생성 실패(우회 재현 — rec 0_9 floor 1_2)`() {
        shouldThrow<IllegalArgumentException> {
            MarginInputs(
                recommendedRate = rateOf("0.9"),
                floorRate = rateOf("1.2"),
                predictedRate = rateOf("0.9"),
                priceFitness = UnitScore(BigDecimal.ZERO),
                capacity = UnitScore(BigDecimal.ZERO),
            )
        }
    }

    @Test
    fun `F-1 — predictedRate 가 1 을 넘으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            MarginInputs(
                recommendedRate = rateOf("0.9"),
                floorRate = null,
                predictedRate = rateOf("1.5"),
                priceFitness = UnitScore(BigDecimal.ZERO),
                capacity = UnitScore(BigDecimal.ZERO),
            )
        }
    }

    @Test
    fun `F-1 회귀 — 정상 범위(rec 0_7, floor 0_9)는 legacy 와 일치한다`() {
        val inputs =
            MarginInputs(
                recommendedRate = rateOf("0.7"),
                floorRate = rateOf("0.9"),
                predictedRate = rateOf("0.7"),
                priceFitness = UnitScore(BigDecimal.ZERO),
                capacity = UnitScore(BigDecimal.ZERO),
            )
        // floorHeadroom = clamp01((0.7-0.9)/(1-0.9)) = clamp01(-2) = 0 · alignment = 1.0(완전 정렬)
        // score = .7*.35 + 0*.20 + 1.0*.20 + 0*.15 + 0*.10 = .245+.20 = .445(verifier r1 F-1 표와 일치)
        val score = deriveExpectedMargin(inputs, policy)
        score.value.compareTo(BigDecimal("0.445")) shouldBe 0
    }
}
