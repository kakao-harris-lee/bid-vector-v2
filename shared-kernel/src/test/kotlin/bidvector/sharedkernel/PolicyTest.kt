package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
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
}
