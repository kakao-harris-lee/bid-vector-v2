package bidvector.decision.priority

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** `UnitVector` 생성 불변식(scope.md ④, D-4B4-3 — require 는 값 타입 init 에만). */
class UnitVectorTest {
    @Test
    fun `이미 정규화된 다차원 벡터는 그대로 생성된다(3-4-5 삼각비, norm 정확히 1)`() {
        UnitVector(values = listOf(BigDecimal("0.6"), BigDecimal("0.8")), normEpsilon = BigDecimal("0.0001"))
    }

    @Test
    fun `차원이 0 이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            UnitVector(values = emptyList(), normEpsilon = BigDecimal("0.0001"))
        }
    }

    @Test
    fun `normEpsilon 이 0 이하면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            UnitVector(values = listOf(BigDecimal("1.0")), normEpsilon = BigDecimal.ZERO)
        }
    }

    @Test
    fun `norm 이 ε 범위 안이면 생성된다(경계 포함, 1차원 norm=1_5, ε=0_5 — 이진 부동소수로 정확한 값)`() {
        // 1.05 - 1.0 은 이진 부동소수로 정확히 0.05 가 아니라 경계 포함 단언이 뜬다(round-off).
        // 1.5·1.0·0.5 는 이진으로 정확히 표현되는 값이라 경계 비교가 흔들리지 않는다.
        UnitVector(values = listOf(BigDecimal("1.5")), normEpsilon = BigDecimal("0.5"))
    }

    @Test
    fun `norm 이 ε 범위를 벗어나면 생성 실패(1차원 norm=1_05, ε=0_0499)`() {
        shouldThrow<IllegalArgumentException> {
            UnitVector(values = listOf(BigDecimal("1.05")), normEpsilon = BigDecimal("0.0499"))
        }
    }

    @Test
    fun `norm 이 1보다 많이 작으면 생성 실패(1차원 norm=0_5, ε=0_0001)`() {
        shouldThrow<IllegalArgumentException> {
            UnitVector(values = listOf(BigDecimal("0.5")), normEpsilon = BigDecimal("0.0001"))
        }
    }
}
