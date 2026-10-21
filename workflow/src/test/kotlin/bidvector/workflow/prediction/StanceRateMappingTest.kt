package bidvector.workflow.prediction

import bidvector.decision.menu.BidStance
import bidvector.sharedkernel.Rate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private fun fraction(value: String): Rate = Rate.ofFraction(BigDecimal(value))

/**
 * ML 후보 셋 ↔ 메뉴 태도 셋의 **라벨 대응**(M6/G1-DEC05 산출물 1).
 *
 * 왜 test 가 필요한가: 두 체계가 같은 순서 축에 다른 이름을 쓰므로(`conservative` ↔ `safe`,
 * `base` ↔ `recommended`) 대응을 뒤집어도 **타입이 같아 컴파일이 통과한다**. 순서 불변식도
 * 뒤집힌 대응을 못 잡는다 — 뒤집어도 여전히 오름차순일 수 있기 때문이 아니라, `safe` 와
 * `aggressive` 를 맞바꾸면 `init` 이 던지지만 `base` ↔ `conservative` 를 맞바꾸는 변이는
 * 값에 따라 조용히 통과하기 때문이다. 그래서 세 자리를 값으로 못박는다.
 */
class StanceRateMappingTest {
    @Test
    fun `후보 셋의 세 라벨이 태도 셋의 세 자리에 하나씩 대응한다`() {
        val candidates =
            BidRateCandidates(
                conservative = fraction("0.90"),
                base = fraction("0.93"),
                aggressive = fraction("0.96"),
            )

        val stances = candidates.toStanceRates()

        stances.rateOf(BidStance.Safe) shouldBe fraction("0.90")
        stances.rateOf(BidStance.Recommended) shouldBe fraction("0.93")
        stances.rateOf(BidStance.Aggressive) shouldBe fraction("0.96")
    }

    /** 양쪽이 같은 부등식을 독립으로 재므로, 대응이 순서를 깨뜨리면 생성 시점에 드러난다. */
    @Test
    fun `역순 후보 셋은 애초에 만들어지지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            BidRateCandidates(
                conservative = fraction("0.96"),
                base = fraction("0.93"),
                aggressive = fraction("0.90"),
            )
        }
    }
}
