package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

/** ① [deriveUrgency](scope.md, legacy `allocation.py:47-54`). 항상 Present(위협 모델 방어 (a)). */
class UrgencyDerivationTest {
    private val policy = resolvedTestPolicy()

    @Test
    fun `마감 없음은 정책 상수 0_3`() {
        val outcome = deriveUrgency(null, policy)
        outcome shouldBe DerivationOutcome.Present(UnitScore(BigDecimal("0.3")))
    }

    @Test
    fun `6h 는 1_0`() {
        deriveUrgency(Duration.ofHours(6), policy) shouldBe DerivationOutcome.Present(UnitScore(BigDecimal("1.0")))
    }

    @Test
    fun `6h+1ns 는 0_8`() {
        deriveUrgency(Duration.ofHours(6).plusNanos(1), policy) shouldBe
            DerivationOutcome.Present(UnitScore(BigDecimal("0.8")))
    }

    @Test
    fun `24h 는 0_8`() {
        deriveUrgency(Duration.ofHours(24), policy) shouldBe DerivationOutcome.Present(UnitScore(BigDecimal("0.8")))
    }

    @Test
    fun `24h+1ns 는 0_55`() {
        deriveUrgency(Duration.ofHours(24).plusNanos(1), policy) shouldBe
            DerivationOutcome.Present(UnitScore(BigDecimal("0.55")))
    }

    @Test
    fun `72h 는 0_55`() {
        deriveUrgency(Duration.ofHours(72), policy) shouldBe DerivationOutcome.Present(UnitScore(BigDecimal("0.55")))
    }

    @Test
    fun `72h+1ns 는 0_25`() {
        deriveUrgency(Duration.ofHours(72).plusNanos(1), policy) shouldBe
            DerivationOutcome.Present(UnitScore(BigDecimal("0.25")))
    }

    @Test
    fun `0h 는 1_0`() {
        deriveUrgency(Duration.ZERO, policy) shouldBe DerivationOutcome.Present(UnitScore(BigDecimal("1.0")))
    }
}
