package bidvector.workflow.evaluation

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private fun validSlot(
    capacityHold: BigDecimal = BigDecimal("0.8"),
    forceBidProbability: BigDecimal = BigDecimal("0.8"),
    forceBidMatched: BigDecimal = BigDecimal("0.7"),
): LadderPolicySlot = LadderPolicySlot(capacityHold, forceBidProbability, forceBidMatched)

/**
 * 수정 라운드 3 L-2 — 형제 `VerdictLadderPolicyData`와 같은 `[0,1]` 범위 불변식을
 * 생성 시점에 강제한다. 강제하지 않으면 실패 지점이 조립부가 아니라 판정부(`VerdictLadder
 * .judge`)로 밀린다.
 */
class LadderPolicySlotTest {
    @Test
    fun `세 임계 중 하나라도 0 미만이면 거부된다`() {
        shouldThrow<IllegalArgumentException> { validSlot(capacityHold = BigDecimal("-0.01")) }
        shouldThrow<IllegalArgumentException> { validSlot(forceBidProbability = BigDecimal("-0.01")) }
        shouldThrow<IllegalArgumentException> { validSlot(forceBidMatched = BigDecimal("-0.01")) }
    }

    @Test
    fun `세 임계 중 하나라도 1 초과면 거부된다`() {
        shouldThrow<IllegalArgumentException> { validSlot(capacityHold = BigDecimal("1.01")) }
        shouldThrow<IllegalArgumentException> { validSlot(forceBidProbability = BigDecimal("1.01")) }
        shouldThrow<IllegalArgumentException> { validSlot(forceBidMatched = BigDecimal("1.01")) }
    }

    @Test
    fun `0 과 1 은 경계 포함으로 허용된다`() {
        validSlot(
            capacityHold = BigDecimal.ZERO,
            forceBidProbability = BigDecimal.ZERO,
            forceBidMatched = BigDecimal.ZERO,
        )
        validSlot(capacityHold = BigDecimal.ONE, forceBidProbability = BigDecimal.ONE, forceBidMatched = BigDecimal.ONE)
    }
}
