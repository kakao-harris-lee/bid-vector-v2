package bidvector.decision

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private fun validPolicy(
    capacityHold: BigDecimal = BigDecimal("0.8"),
    bidNow: BigDecimal = BigDecimal("0.7"),
    review: BigDecimal = BigDecimal("0.45"),
    forceBidProbability: BigDecimal = BigDecimal("0.8"),
    forceBidMatched: BigDecimal = BigDecimal("0.7"),
): VerdictLadderPolicyData = VerdictLadderPolicyData(capacityHold, bidNow, review, forceBidProbability, forceBidMatched)

/**
 * scope.md ⑦, 조사 §2.4·N-6 — legacy 의 「판정 시점 침묵 수리」(`review_threshold >
 * bid_now_threshold` 를 조용히 고쳐 쓰는 것)를 생성 불변식으로 막는다. 깨진 조합은
 * **구성되지 않는다** — 수리하지 않는다.
 */
class VerdictLadderPolicyDataTest {
    @Test
    fun `reviewThreshold 가 bidNowThreshold 를 넘으면 생성 자체가 거부된다 — 조용히 고치지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            validPolicy(bidNow = BigDecimal("0.5"), review = BigDecimal("0.6"))
        }
    }

    @Test
    fun `reviewThreshold 와 bidNowThreshold 가 같으면 허용된다 — 경계는 포함`() {
        validPolicy(bidNow = BigDecimal("0.5"), review = BigDecimal("0.5"))
    }

    @Test
    fun `다섯 임계 중 하나라도 0 미만이면 거부된다`() {
        shouldThrow<IllegalArgumentException> { validPolicy(capacityHold = BigDecimal("-0.01")) }
        shouldThrow<IllegalArgumentException> { validPolicy(bidNow = BigDecimal("-0.01")) }
        shouldThrow<IllegalArgumentException> { validPolicy(review = BigDecimal("-0.01")) }
        shouldThrow<IllegalArgumentException> { validPolicy(forceBidProbability = BigDecimal("-0.01")) }
        shouldThrow<IllegalArgumentException> { validPolicy(forceBidMatched = BigDecimal("-0.01")) }
    }

    @Test
    fun `다섯 임계 중 하나라도 1 초과면 거부된다`() {
        shouldThrow<IllegalArgumentException> { validPolicy(capacityHold = BigDecimal("1.01")) }
        shouldThrow<IllegalArgumentException> { validPolicy(bidNow = BigDecimal("1.01")) }
        shouldThrow<IllegalArgumentException> { validPolicy(review = BigDecimal("1.01")) }
        shouldThrow<IllegalArgumentException> { validPolicy(forceBidProbability = BigDecimal("1.01")) }
        shouldThrow<IllegalArgumentException> { validPolicy(forceBidMatched = BigDecimal("1.01")) }
    }
}

/** R-3 폐쇄 — legacy 의 `probability_score` 는 상한이 없었다(조사 §8 R-3). */
class UnitScoreTest {
    @Test
    fun `0 미만은 거부된다`() {
        shouldThrow<IllegalArgumentException> { UnitScore(BigDecimal("-0.0001")) }
    }

    @Test
    fun `1 초과는 거부된다 — R-3 폐쇄(legacy probability_score 는 상한이 없었다)`() {
        shouldThrow<IllegalArgumentException> { UnitScore(BigDecimal("1.0001")) }
    }

    @Test
    fun `0 과 1 은 경계 포함으로 허용된다`() {
        UnitScore(BigDecimal.ZERO)
        UnitScore(BigDecimal.ONE)
    }
}

/** scope.md ⑦ — 사다리 입력의 용량 값은 음수일 수 없다. */
class LadderInputTest {
    @Test
    fun `currentActiveBids 가 음수면 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            LadderInput(UnitScore(BigDecimal("0.5")), null, null, currentActiveBids = -1, maxActiveBids = 10)
        }
    }

    @Test
    fun `maxActiveBids 가 음수면 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            LadderInput(UnitScore(BigDecimal("0.5")), null, null, currentActiveBids = 0, maxActiveBids = -1)
        }
    }
}
