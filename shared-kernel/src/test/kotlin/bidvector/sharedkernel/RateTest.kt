package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class RateTest {
    @Test
    fun `P-1a percent-fraction 왕복이 정보를 잃지 않는다`() {
        runBlocking {
            checkAll(Arb.int(0, 6), Arb.long(0L, 100_000_000L)) { scale, unscaled ->
                val percent = BigDecimal.valueOf(unscaled, scale)

                val rate = Rate.ofPercent(percent)
                val roundTripped = rate.fraction.multiply(BigDecimal(100))

                roundTripped.compareTo(percent) shouldBe 0
            }
        }
    }

    @Test
    fun `P-1b 축 뉴타입에 감싸도 fraction 이 바뀌지 않는다`() {
        runBlocking {
            checkAll(Arb.long(0L, 100_000_000L)) { unscaled ->
                val rate = Rate.ofFraction(BigDecimal.valueOf(unscaled, 4))

                AssessmentRate(rate).rate.fraction.compareTo(rate.fraction) shouldBe 0
                BidRate(rate, BidRateOrigin.Recommended).rate.fraction.compareTo(rate.fraction) shouldBe 0
            }
        }
    }

    @Test
    fun `P-1c 공개 경로로 만든 값은 전부 정규 형태이고, 비정규 값의 재구성은 실패한다`() {
        runBlocking {
            checkAll(Arb.int(0, 6), Arb.long(0L, 100_000_000L)) { scale, unscaled ->
                val fraction = Rate.ofFraction(BigDecimal.valueOf(unscaled, scale)).fraction

                fraction shouldBe fraction.stripTrailingZeros().let { if (it.signum() == 0) BigDecimal.ZERO else it }
            }
        }

        val nonNormalized = BigDecimal("1.500")
        assertThrows<IllegalArgumentException> {
            Rate(nonNormalized)
        }
    }

    @Test
    fun `P-3a 단위 선언 없이는 Rate 가 만들어지지 않는다 — 공개 경로가 이름을 요구하는 둘뿐이다`() {
        val fromFraction = Rate.ofFraction(BigDecimal("0.5"))
        val fromPercent = Rate.ofPercent(BigDecimal("50"))

        fromFraction.fraction.compareTo(fromPercent.fraction) shouldBe 0
    }

    @Test
    fun `Rate 는 하한만 두고 상한 밴드를 두지 않는다 — D-4, L-10`() {
        // 87.5 를 fraction 으로 잘못 선언하는 것을 이 타입이 막지 않는다(값의 의미적 정확성은
        // 위협 모델 밖) — 그러나 음수는 하한 위반으로 거부한다.
        Rate.ofFraction(BigDecimal("87.5"))

        assertThrows<IllegalArgumentException> {
            Rate.ofFraction(BigDecimal("-0.01"))
        }
    }

    @Test
    fun `P-3d origin 이 다른 같은 축 값은 합쳐지지 않는다`() {
        val rate = Rate.ofFraction(BigDecimal("0.9"))

        val observed = BidRate(rate, BidRateOrigin.ObservedFromSamples)
        val recommended = BidRate(rate, BidRateOrigin.Recommended)

        observed shouldNotBe recommended
    }
}
