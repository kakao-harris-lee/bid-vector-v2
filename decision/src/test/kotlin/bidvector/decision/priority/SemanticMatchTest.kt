package bidvector.decision.priority

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val EPSILON = BigDecimal("0.0001")

private fun vector(vararg coordinates: String): UnitVector = UnitVector(coordinates.map(::BigDecimal), EPSILON)

/**
 * `SemanticMatch.of` acceptance(scope.md ④, 위협 모델 (4)(5)(e)). 판정은 sealed(4D-1
 * G-1). 코사인은 `Double` 경로를 거치므로(`BigDecimal.valueOf`) 결과 scale 이 리터럴과
 * 다를 수 있다 — 전부 `compareTo` 로 비교한다(scale-무관 수치 동등, `equals` 아님).
 */
class SemanticMatchTest {
    @Test
    fun `동일 벡터의 코사인은 1이다(offset 0)`() {
        val notice = vector("0.6", "0.8")
        val profile = vector("0.6", "0.8")

        val outcome = SemanticMatch.of(notice, profile, BigDecimal.ZERO, TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<MatchOutcome.Matched>()
        outcome.score.value.compareTo(BigDecimal.ONE) shouldBe 0
    }

    @Test
    fun `직교 벡터의 코사인은 0이다(offset 0)`() {
        val notice = vector("1.0", "0.0")
        val profile = vector("0.0", "1.0")

        val outcome = SemanticMatch.of(notice, profile, BigDecimal.ZERO, TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<MatchOutcome.Matched>()
        outcome.score.value.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    @Test
    fun `반대 방향 벡터는 코사인 -1 이 0 으로 clamp 된다(offset 0)`() {
        val notice = vector("0.6", "0.8")
        val profile = vector("-0.6", "-0.8")

        val outcome = SemanticMatch.of(notice, profile, BigDecimal.ZERO, TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<MatchOutcome.Matched>()
        outcome.score.value.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    @Test
    fun `offset 이 적용된다(동일 벡터 + offset -0_20 = 0_80)`() {
        val notice = vector("1.0", "0.0")
        val profile = vector("1.0", "0.0")

        val outcome = SemanticMatch.of(notice, profile, BigDecimal("-0.20"), TEST_PRIORITY_POLICY)

        outcome.shouldBeInstanceOf<MatchOutcome.Matched>()
        outcome.score.value.compareTo(BigDecimal("0.80")) shouldBe 0
    }

    @Test
    fun `차원이 다르면 DimensionMismatch 다(require 아님)`() {
        val notice = vector("1.0", "0.0")
        val profile = vector("1.0", "0.0", "0.0")

        val outcome = SemanticMatch.of(notice, profile, BigDecimal.ZERO, TEST_PRIORITY_POLICY)

        outcome shouldBe MatchOutcome.DimensionMismatch
    }

    @Test
    fun `offset 이 정책 범위(±0_20) 를 벗어나면 OffsetOutOfRange 다(조용한 clamp 아님, 위협 모델 e)`() {
        val notice = vector("1.0", "0.0")
        val profile = vector("1.0", "0.0")

        val outcome = SemanticMatch.of(notice, profile, BigDecimal("0.21"), TEST_PRIORITY_POLICY)

        outcome shouldBe MatchOutcome.OffsetOutOfRange
    }

    @Test
    fun `offset 하한 -0_21 도 OffsetOutOfRange 다(F-4, 하한 분기 실측)`() {
        val notice = vector("1.0", "0.0")
        val profile = vector("1.0", "0.0")

        val outcome = SemanticMatch.of(notice, profile, BigDecimal("-0.21"), TEST_PRIORITY_POLICY)

        outcome shouldBe MatchOutcome.OffsetOutOfRange
    }

    @Test
    fun `offset 경계값 ±0_20 은 범위 안이다(포함 경계)`() {
        val notice = vector("1.0", "0.0")
        val profile = vector("1.0", "0.0")

        val positive = SemanticMatch.of(notice, profile, BigDecimal("0.20"), TEST_PRIORITY_POLICY)
        val negative = SemanticMatch.of(notice, profile, BigDecimal("-0.20"), TEST_PRIORITY_POLICY)

        positive.shouldBeInstanceOf<MatchOutcome.Matched>()
        negative.shouldBeInstanceOf<MatchOutcome.Matched>()
    }
}
