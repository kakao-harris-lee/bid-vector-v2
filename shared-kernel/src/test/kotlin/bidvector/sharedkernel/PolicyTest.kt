package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.RoundingMode
import java.time.LocalDate

private const val EPOCH_DAY_MIN = -20_000L
private const val EPOCH_DAY_MAX = 20_000L

class PolicyTest {
    @Test
    fun `P-2c resolve 는 기준일 이하 최대 엔트리를 고르고, 없으면 NotApplicable`() {
        runBlocking {
            checkAll(
                Arb.long(1L, 3_000L),
                Arb.long(3_001L, 6_000L),
                Arb.long(EPOCH_DAY_MIN, EPOCH_DAY_MAX),
            ) { firstEpoch, secondEpoch, referenceEpoch ->
                val first = LocalDate.ofEpochDay(firstEpoch)
                val second = LocalDate.ofEpochDay(secondEpoch)
                val referenceDate = LocalDate.ofEpochDay(referenceEpoch)

                val policy =
                    EffectiveDatedPolicy(
                        source = "test",
                        entries = listOf(EffectiveFrom.On(second) to "second", EffectiveFrom.On(first) to "first"),
                    )

                val resolution = policy.resolve(referenceDate)

                when {
                    referenceDate.isBefore(first) -> resolution.shouldBeInstanceOf<Resolution.NotApplicable>()
                    referenceDate.isBefore(second) -> (resolution as Resolution.Resolved).value shouldBe "first"
                    else -> (resolution as Resolution.Resolved).value shouldBe "second"
                }
            }
        }
    }

    @Test
    fun `Initial 은 sentinel 날짜가 아니라 어떤 기준일에도 적용된다`() {
        runBlocking {
            checkAll(Arb.long(EPOCH_DAY_MIN, EPOCH_DAY_MAX)) { referenceEpoch ->
                val policy =
                    EffectiveDatedPolicy(
                        source = "test",
                        entries = listOf(EffectiveFrom.Initial to "base"),
                    )

                val resolution = policy.resolve(LocalDate.ofEpochDay(referenceEpoch))

                (resolution as Resolution.Resolved).value shouldBe "base"
            }
        }
    }

    @Test
    fun `엔트리가 없으면 소급 적용하지 않고 NotApplicable 을 낸다`() {
        val policy = EffectiveDatedPolicy<String>(source = "test", entries = emptyList())

        policy.resolve(LocalDate.of(2026, 1, 1)).shouldBeInstanceOf<Resolution.NotApplicable>()
    }

    @Test
    fun `부가세율 정책 표는 authoritative 0-10 을 낸다`() {
        val resolution = VAT_RATE_POLICY.resolve(LocalDate.of(2026, 1, 1))

        (resolution as Resolution.Resolved).value.compareTo(java.math.BigDecimal("0.10")) shouldBe 0
    }

    /**
     * verifier r2 M-6 — `RoundingPolicy(scaleDigits, mode)` 가 정의역 검사 없이 아무 값이나
     * 받아, 음수 `scaleDigits` 가 예외도 사유 있는 실패도 아니라 **조용한 성공**으로 흘러
     * 백 원 단위 반올림 같은 값 오염을 만든다. `data-dictionary.md` §1.1 정의 ①이 금액 축
     * 자리수를 0(원 단위 정수)으로 닫았으므로 음수는 그 자체로 불법 값이다. 상한(축 밖
     * `scaleDigits`)은 `OPEN-DIC-10` 미결이라 여기서 정하지 않는다 — 하한(0 이상)만 건다.
     * `Rate.init`·`BaseAmount.init` 이 이미 쓰는 관례(construction-time invariant 는
     * `require` 로 던진다, `Measurement`/`Fact` 로 감싸지 않는다)를 그대로 따른다.
     */
    @Test
    fun `M-6 RoundingPolicy 는 음수 scaleDigits 를 구성상 거부한다`() {
        assertThrows<IllegalArgumentException> {
            RoundingPolicy(-2, RoundingMode.HALF_UP)
        }
    }

    @Test
    fun `M-6 임의 음수 scaleDigits 는 mode 와 무관하게 항상 거부된다`() {
        runBlocking {
            checkAll(Arb.int(Int.MIN_VALUE, -1), Arb.element(RoundingMode.entries)) { scaleDigits, mode ->
                assertThrows<IllegalArgumentException> {
                    RoundingPolicy(scaleDigits, mode)
                }
            }
        }
    }

    @Test
    fun `M-6 0 이상 scaleDigits 는 계속 허용된다`() {
        runBlocking {
            checkAll(Arb.int(0, 10), Arb.element(RoundingMode.entries)) { scaleDigits, mode ->
                val policy = RoundingPolicy(scaleDigits, mode)

                policy.scaleDigits shouldBe scaleDigits
            }
        }
    }
}
