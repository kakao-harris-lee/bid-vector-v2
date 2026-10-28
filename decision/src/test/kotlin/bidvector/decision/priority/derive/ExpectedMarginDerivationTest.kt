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
 * (D-4B5-6). 가중치는 `TEST_DERIVATION_POLICY.marginWeights`.
 *
 * **경계 셋(`0.0` · `1.0` · `1.2`)은 여기 남는다**(D-DEC10-24) — DEC-10 게이트가 개연 밴드를
 * 세운 뒤 그 값들은 **가격 경로**에 도달하지 못하지만, 이 함수는 `decision` 모듈 안에서 그
 * 값으로 호출될 수 있다(`marginInputsFrom` 의 `floorRate` 가 통과 값이고 `MarginInputs` 의
 * 불변식은 `≤ 1` 까지만 좁힌다). 그래서 세 test 는 **거동 주장이 아니라 모듈 안 방어층**을
 * 재는 것으로 이름과 문면이 바뀌었다 — 일반식 등식 · 분모 0 가드 · `init` 최후 방어선.
 * 같은 세 값이 **게이트에서 거부되고 margin 에서 떨어진다**는 종단 주장은
 * `bidvector.workflow.evaluation.FloorBoundaryAtGateTest` 가 따로 잰다(이 셋을 옮긴 것이
 * 아니라 더한 것이다).
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

    /**
     * **일반식 등식**(D-DEC10-24) — `floor = 0` 에 특례 분기가 없다. 앞 판은 그 자리에
     * `floor.signum() == 0` 분기를 두었는데 일반식이 같은 값을 낸다(`(rec − 0) ÷ (1 − 0) = rec`).
     * 분기를 지운 것이 거동을 바꾸지 않음을 **이 test 가 계속 잰다** — 옛 이름(「손계산 3 —
     * legacy else 분기」)은 특례 분기의 거동을 주장했고, 지금 재는 것은 `floor = 0` 에서
     * **일반식이 `rec` 를 낸다**는 등식이다. 그래서 분모에 엡실론을 넣거나 clamp 를 바꾸는
     * 변경이 여기서 걸린다(verifier r5 K1 실측: `floor = 0` 에 `ZERO` 특례를 심으면 RED).
     *
     * **이 test 는 분기의 부재를 재지 않는다**(CR r5 L-4) — 누가 `floor.signum() == 0 ->
     * recommended` 를 **되살려도** 같은 값이라 초록이다. 이름이 「특례 분기 없이」라고 말하던
     * 것을 그래서 좁혔다. 분기 부재 자체를 재려면 바이트코드 축으로 가야 하고 그것은 과잉이다 —
     * 되살아난 특례는 중복일 뿐 거동을 바꾸지 않는다.
     */
    @Test
    fun `일반식 등식 — floor 0 은 일반식으로 rec 를 낸다`() {
        val inputs =
            MarginInputs(
                recommendedRate = rateOf("0.70"),
                floorRate = rateOf("0.0"),
                predictedRate = rateOf("0.70"),
                priceFitness = UnitScore(BigDecimal("1.00")),
                capacity = UnitScore(BigDecimal("1.00")),
            )
        // floorHeadroom = (0.70-0)/(1-0) = 0.70 · alignment = 1.0(완전 정렬)
        // score = .70*.35 + .70*.20 + 1.0*.20 + 1.00*.15 + 1.00*.10 = 0.7*.55 + .20 + .15 + .10 = 0.385+0.45=0.835
        val score = deriveExpectedMargin(inputs, policy)
        score.value.compareTo(BigDecimal("0.835")) shouldBe 0
    }

    /**
     * **분모 0 가드**(D-DEC10-24). 옛 이름은 「우회 6 — floor 1 이면 …」이었고 게시값 `1.0` 의
     * **거동**을 주장했다 — 그 주장은 DEC-10 게이트 뒤로 production 에서 일어날 수 없어
     * `FloorBoundaryAtGateTest` 가 게이트 수준에서 잰다. 여기 남는 것은 **모듈 안 생성 경로**를
     * 위한 가드다 — `MarginInputs.init` 이 `floor ≤ 1` 까지만 좁히므로 `floor = 1` 을 지을 수
     * 있고, 그때 분모가 0 이 되면 산술 예외가 난다(business control flow 에 예외 금지).
     */
    @Test
    fun `분모 0 가드 — 모듈 안에서 floor 1 을 지어도 예외 대신 rec 를 낸다`() {
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

    // ---- Rate 상한(≤1) 생성 불변식 ----

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

    /**
     * **최후 방어선**(D-DEC10-9 · 이름 정정 D-DEC10-24). 게시값 `1.2` 가 가격 경로로 들어오는
     * 일 자체는 DEC-10 게이트가 막고, 그 **거동**(성분 드롭 + 계수)은
     * `FloorBoundaryAtGateTest` 가 잰다. 여기 남는 것은 `init` 의 `floorRate` 술어가
     * **모듈 안 생성 경로**에서 여전히 사는지다 — 지우면 그 술어가 test 0 이 된다.
     */
    @Test
    fun `최후 방어선 — 모듈 안에서 floorRate 가 1 을 넘으면 생성 실패`() {
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
        // score = .7*.35 + 0*.20 + 1.0*.20 + 0*.15 + 0*.10 = .245+.20 = .445
        val score = deriveExpectedMargin(inputs, policy)
        score.value.compareTo(BigDecimal("0.445")) shouldBe 0
    }
}
