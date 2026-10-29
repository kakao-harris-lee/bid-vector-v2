package bidvector.decision.menu

import bidvector.decision.PlausibilityBand
import bidvector.decision.PublishedFloorTrust
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.Rate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val BAND = PlausibilityBand(BigDecimal("0.30"), BigDecimal("0.995"))

private fun statutoryFloorRate(value: String): FloorRate =
    FloorRate(Rate.ofFraction(BigDecimal(value)), FloorRateOrigin.StatutoryTable(EffectiveFrom.Initial))

/**
 * DEC-10 ① — 메뉴 커널이 받는 것은 **게이트 결과**다(D-DEC10-3). 개연 불가는 부재와 **같은
 * 가지**(`SourceAbsent`)로 떨어지되 **사유는 별개**다(DEC-10 acceptance 첫째·넷째). 어느
 * 사유로 떨어졌는지가 사라지면 그것이 침묵 스킵이다.
 */
class BidMenuFloorTrustTest {
    @Test
    fun `개연 불가 게시값은 하한 미보고와 같은 가지로 떨어지고 사유가 별개로 남는다`() {
        val outcome =
            composeBidMenu(
                testInput(floorTrust = PublishedFloorTrust.Implausible(testFloorRate("1.00000"), BAND)),
                testMenuPolicy(reading = atOrAbove()),
            )

        outcome.shouldBeInstanceOf<BidMenuOutcome.SourceAbsent>()
        outcome.reason shouldBe MenuSourceAbsence.PublishedFloorRateImplausible
        outcome.reason shouldNotBe MenuSourceAbsence.PublishedFloorRateAbsent
    }

    @Test
    fun `법정표 출처는 또 다른 사유로 떨어진다 — 게이트가 심판이 아닌 값이다`() {
        val outcome =
            composeBidMenu(
                testInput(floorTrust = PublishedFloorTrust.OriginNotJudged(statutoryFloorRate("0.88"))),
                testMenuPolicy(reading = atOrAbove()),
            )

        outcome.shouldBeInstanceOf<BidMenuOutcome.SourceAbsent>()
        outcome.reason shouldBe MenuSourceAbsence.PublishedFloorRateOriginNotJudged
    }

    @Test
    fun `게시값 부재는 그대로 부재 사유다`() {
        val outcome = composeBidMenu(testInput(publishedFloorRate = null), testMenuPolicy(reading = atOrAbove()))

        outcome.shouldBeInstanceOf<BidMenuOutcome.SourceAbsent>()
        outcome.reason shouldBe MenuSourceAbsence.PublishedFloorRateAbsent
    }

    /**
     * r1 v M-2 — 앞 판의 실패 가지 test 는 판정을 **직접 주입**해서 게이트 변이로는 RED 가 되지
     * 않았다. 이 test 는 **원값에서 시작**해 게이트를 지나므로 밴드 술어가 무너지면 여기서 깨진다.
     */
    @Test
    fun `밴드 밖 원값이 게이트를 지나 개연 불가 사유로 떨어진다 — 종단`() {
        val outcome =
            composeBidMenu(
                testInput(publishedFloorRate = testFloorRate("1.00000")),
                testMenuPolicy(reading = atOrAbove()),
            )

        outcome.shouldBeInstanceOf<BidMenuOutcome.SourceAbsent>()
        outcome.reason shouldBe MenuSourceAbsence.PublishedFloorRateImplausible
    }

    @Test
    fun `밴드 아래 원값도 종단으로 같은 사유다`() {
        val outcome =
            composeBidMenu(
                testInput(publishedFloorRate = testFloorRate("0.2999")),
                testMenuPolicy(reading = atOrAbove()),
            )

        outcome.shouldBeInstanceOf<BidMenuOutcome.SourceAbsent>()
        outcome.reason shouldBe MenuSourceAbsence.PublishedFloorRateImplausible
    }

    @Test
    fun `밴드 안 게시값은 메뉴를 낸다 — 게이트가 정상 경로를 막지 않는다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))

        outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
    }

    /**
     * 착수 census 가 찾은 자리 — 메뉴의 **근거 칸**이 원값을 내보내고 있었다. 가격이 어느 값에서
     * 나왔는지를 말하는 칸이므로 게이트 통과 값을 실어야 한다(계약 갱신 in_scope 추가).
     */
    @Test
    fun `근거 칸은 게이트 통과 값을 싣는다 — 원값이 결과로 새지 않는다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))

        outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        outcome.safe.grounds.publishedFloorRate.rate shouldBe Rate.ofFraction(BigDecimal("0.88"))
    }
}
