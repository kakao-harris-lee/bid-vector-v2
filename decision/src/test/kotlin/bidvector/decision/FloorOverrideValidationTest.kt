package bidvector.decision

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val BAND = PlausibilityBand(BigDecimal("0.30"), BigDecimal("0.995"))

/**
 * scope.md ⑥, `OPEN-DEC-04`, 조사 §6 — 개연 밴드 밖 운영자 하한 override 는 버리지도
 * 통과시키지도 않고 사유와 함께 거부된다. 거부가 관측 가능한 결과 타입에 실린다.
 */
class FloorOverrideValidationTest {
    @Test
    fun `밴드 안 override 는 수용된다`() {
        val outcome = FloorOverrideValidation.validate(BigDecimal("0.5"), BAND)

        outcome.shouldBeInstanceOf<FloorOverrideOutcome.Accepted>()
        outcome.rate shouldBe BigDecimal("0.5")
    }

    @Test
    fun `밴드보다 낮은 override 는 버리지도 통과시키지도 않고 사유와 함께 거부된다`() {
        val outcome = FloorOverrideValidation.validate(BigDecimal("0.1"), BAND)

        outcome.shouldBeInstanceOf<FloorOverrideOutcome.Rejected>()
        outcome.rate shouldBe BigDecimal("0.1")
        outcome.band shouldBe BAND
    }

    @Test
    fun `밴드보다 높은 override(legacy 조사 §6-2 — 상한 없이 그대로 통과하던 자리, 값 1) 도 거부된다`() {
        val outcome = FloorOverrideValidation.validate(BigDecimal("1.0"), BAND)

        outcome.shouldBeInstanceOf<FloorOverrideOutcome.Rejected>()
    }

    @Test
    fun `밴드 경계값은 포함으로 수용된다`() {
        FloorOverrideValidation.validate(BAND.min, BAND).shouldBeInstanceOf<FloorOverrideOutcome.Accepted>()
        FloorOverrideValidation.validate(BAND.max, BAND).shouldBeInstanceOf<FloorOverrideOutcome.Accepted>()
    }
}
