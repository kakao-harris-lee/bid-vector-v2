package bidvector.workflow.prediction

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

/**
 * scope.md ①·⑦ — 이 패키지가 여는 값 타입의 생성 불변식. 닫힌 타입(`internal constructor`)의
 * 위조 차단은 이 test 가 아니라 임시 clone 컴파일 거부 실측(commands.md)이 진다 — 이 test
 * 는 같은 모듈 안에서 여전히 서는 하한·형태 불변식만 잰다.
 */
class PredictionValueTest {
    @Test
    fun `CallBudget remaining 은 0보다 커야 한다`() {
        shouldThrow<IllegalArgumentException> { CallBudget(Duration.ZERO) }
        shouldThrow<IllegalArgumentException> { CallBudget(Duration.ofMillis(-1)) }
    }

    @Test
    fun `CallBudget 은 양수 Duration 으로 정상 생성된다`() {
        CallBudget(Duration.ofSeconds(1)).remaining shouldBe Duration.ofSeconds(1)
    }

    @Test
    fun `AgencyId 는 빈 문자열을 거부한다`() {
        shouldThrow<IllegalArgumentException> { AgencyId("") }
        shouldThrow<IllegalArgumentException> { AgencyId("   ") }
    }

    @Test
    fun `ModelReleaseSelector Exact 는 빈 releaseId·artifactChecksum 을 거부한다`() {
        shouldThrow<IllegalArgumentException> { ModelReleaseSelector.Exact("", "checksum") }
        shouldThrow<IllegalArgumentException> { ModelReleaseSelector.Exact("release", "") }
    }

    @Test
    fun `Uncertainty sampleSize 는 1 미만을 거부한다(계약 불변식의 방어적 재확인)`() {
        shouldThrow<IllegalArgumentException> {
            Uncertainty(
                sampleSize = 0,
                dispersion = BigDecimal("0.10"),
                estimateMargin = BigDecimal("0.05"),
                intervalSource = IntervalSource.CrossValidationResidual,
            )
        }
    }

    @Test
    fun `Uncertainty sampleSize 1 이상은 정상 생성된다`() {
        Uncertainty(
            sampleSize = 1,
            dispersion = BigDecimal("0.10"),
            estimateMargin = BigDecimal("0.05"),
            intervalSource = IntervalSource.CrossValidationResidual,
        )
    }

    // ---- verifier r1 F-5(low) — 결과 값 타입 불변식 공백(probe P11) ----

    @Test
    fun `BidRateCandidates 는 conservative 대비 base 대비 aggressive 순서를 강제한다`() {
        shouldThrow<IllegalArgumentException> {
            BidRateCandidates(
                conservative = bidvector.sharedkernel.Rate.ofFraction(BigDecimal("0.95")),
                base = bidvector.sharedkernel.Rate.ofFraction(BigDecimal("0.90")),
                aggressive = bidvector.sharedkernel.Rate.ofFraction(BigDecimal("0.85")),
            )
        }
    }

    @Test
    fun `BidRateCandidates 오름차순은 정상 생성된다`() {
        BidRateCandidates(
            conservative = bidvector.sharedkernel.Rate.ofFraction(BigDecimal("0.85")),
            base = bidvector.sharedkernel.Rate.ofFraction(BigDecimal("0.90")),
            aggressive = bidvector.sharedkernel.Rate.ofFraction(BigDecimal("0.95")),
        )
    }

    @Test
    fun `PriceFitness 는 계약에 부호 근거가 없어 음수도 정상 생성된다(verifier r2 G-2 — r1 F-5 되돌림)`() {
        PriceFitness(BigDecimal("-9999")).score shouldBe BigDecimal("-9999")
    }
}
