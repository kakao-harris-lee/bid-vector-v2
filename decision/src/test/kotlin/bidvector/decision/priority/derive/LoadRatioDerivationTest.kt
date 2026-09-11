package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** ⑥ [deriveLoadRatio](scope.md, 4B-2 `CapacitySnapshot` 상당 값). 항상 Present. */
class LoadRatioDerivationTest {
    @Test
    fun `current 0, max 0 이면 0(max 하한 1)`() {
        deriveLoadRatio(0, 0) shouldBe DerivationOutcome.Present(UnitScore(BigDecimal.ZERO))
    }

    @Test
    fun `current 5, max 10 이면 0_5`() {
        val outcome = deriveLoadRatio(5, 10) as DerivationOutcome.Present<UnitScore>
        outcome.value.value.compareTo(BigDecimal("0.5")) shouldBe 0
    }

    @Test
    fun `current 이 max 를 넘으면 1_0 으로 clamp`() {
        val outcome = deriveLoadRatio(15, 10) as DerivationOutcome.Present<UnitScore>
        outcome.value.value.compareTo(BigDecimal.ONE) shouldBe 0
    }

    @Test
    fun `current 이 max 와 같으면 1_0`() {
        val outcome = deriveLoadRatio(10, 10) as DerivationOutcome.Present<UnitScore>
        outcome.value.value.compareTo(BigDecimal.ONE) shouldBe 0
    }

    @Test
    fun `음수 current 는 생성 실패`() {
        shouldThrow<IllegalArgumentException> { deriveLoadRatio(-1, 10) }
    }

    @Test
    fun `음수 max 는 생성 실패`() {
        shouldThrow<IllegalArgumentException> { deriveLoadRatio(1, -1) }
    }
}
