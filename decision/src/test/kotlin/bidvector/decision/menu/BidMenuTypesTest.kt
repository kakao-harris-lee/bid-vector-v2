package bidvector.decision.menu

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * 메뉴 값 타입의 생성 불변식 — **표현 불가능한 상태를 타입으로 닫는다.**
 *
 * 모듈 밖 생성 폐쇄는 여기서 못 잰다(같은 모듈 test 는 `internal` 생성자를 그냥 부른다) —
 * 그것은 [MenuCompileFailureHarnessTest] 의 몫이다. 이 class 는 **생성자가 열려 있는 쪽에서**
 * 불변식이 실제로 던지는지를 잰다.
 */
class BidMenuTypesTest {
    /** 방향은 **공격이 가장 낮고 안전이 가장 높다**(라운드 2 HIGH-1) — 거꾸로는 만들어지지 않는다. */
    @Test
    fun `StanceRates 는 안전이 공격보다 낮은 조합을 받지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            StanceRates(safe = fraction("0.90"), recommended = fraction("0.93"), aggressive = fraction("0.96"))
        }
        shouldThrow<IllegalArgumentException> {
            StanceRates(safe = fraction("0.96"), recommended = fraction("0.97"), aggressive = fraction("0.90"))
        }
    }

    @Test
    fun `StanceRates 는 태도 전수를 대응시킨다`() {
        val rates = testRates(aggressive = "0.90", recommended = "0.93", safe = "0.96")

        rates.rateOf(BidStance.Aggressive) shouldBe fraction("0.90")
        rates.rateOf(BidStance.Recommended) shouldBe fraction("0.93")
        rates.rateOf(BidStance.Safe) shouldBe fraction("0.96")
    }

    /** 자리와 태도가 어긋난 메뉴는 「정상 3안」으로 나갈 수 없다 — 뒤섞인 메뉴의 위조를 막는다. */
    @Test
    fun `Offered 는 태도가 자리와 어긋나면 만들어지지 않는다`() {
        val offered = anyOfferedMenu()

        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.Offered(
                safe = offered.recommended,
                recommended = offered.recommended,
                aggressive = offered.aggressive,
                disclosures = BidMenuDisclosure.entries.toSet(),
                policyVersion = MENU_TEST_VERSION,
            )
        }
    }

    /** 고지 하나를 뺀 메뉴는 표현 불가능하다 — 고지 의무가 값과 함께 표면까지 이동한다. */
    @Test
    fun `Offered 는 공통 고지가 빠지면 만들어지지 않는다`() {
        val offered = anyOfferedMenu()

        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.Offered(
                safe = offered.safe,
                recommended = offered.recommended,
                aggressive = offered.aggressive,
                disclosures = setOf(BidMenuDisclosure.NotAutoSubmitted),
                policyVersion = MENU_TEST_VERSION,
            )
        }
    }

    private fun anyOfferedMenu(): BidMenuOutcome.Offered =
        composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))
            .shouldBeInstanceOf<BidMenuOutcome.Offered>()
}
