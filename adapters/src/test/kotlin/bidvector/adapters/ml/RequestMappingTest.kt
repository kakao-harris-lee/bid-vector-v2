package bidvector.adapters.ml

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import bidvector.workflow.prediction.CompetitionSample
import bidvector.workflow.prediction.ReserveDrawObservation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * M4/4B-7(D-4B7-5) — `reserve_draw`(features.proto 7번 필드) 왕복. `mapRequest`가 조립하는
 * `CompetitionSample` 하나에 예비가격 15·번호 4를 싣고 wire 값을 직접 대조한다(2B-계열
 * consumer test 관례 — `MlTestFixtures.testBidPredictionRequest`를 재사용).
 */
class RequestMappingTest {
    private fun reserveDraw(
        priceCount: Int = 15,
        numbers: Set<Int> = setOf(1, 5, 9, 14),
    ): ReserveDrawObservation =
        ReserveDrawObservation(
            reservePrices =
                (1..priceCount).map { n ->
                    BaseAmount(
                        900_000_000L + n,
                        Currency.KRW,
                        VatTreatment.UNKNOWN,
                        Provenance.Published(NoticeRound.of("000")),
                    )
                },
            selectedNumbers = numbers,
        )

    private fun sampleWith(reserveDraw: ReserveDrawObservation?): CompetitionSample =
        CompetitionSample(
            observedBidRate = Rate.ofFraction(BigDecimal("0.9200")),
            baseAmount = BaseAmount(1_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Undeclared),
            baseAmountProvenanceLabel = BaseAmountProvenance.Clean,
            openedOn = LocalDate.of(2026, 1, 15),
            reserveDraw = reserveDraw,
        )

    private fun mapped(sample: CompetitionSample) =
        mapRequest(
            request = testBidPredictionRequest().copy(competitionSamples = listOf(sample)),
            requestId = "req-1",
            policy = testMlCallPolicy(),
            deadlinePolicyVersion = "deadline-test",
        )

    @Test
    fun `reserveDraw 있으면 예비가격 15 번호 4 가 그대로 왕복한다`() {
        val draw = reserveDraw(priceCount = 15, numbers = setOf(1, 5, 9, 14))

        val proto = mapped(sampleWith(draw)).getCompetitionSamples(0)

        proto.hasReserveDraw() shouldBe true
        proto.reserveDraw.reservePricesCount shouldBe 15
        proto.reserveDraw.reservePricesList.map { it.amountWon } shouldBe draw.reservePrices.map { it.export().won }
        proto.reserveDraw.selectedNumbersList.toSet() shouldBe setOf(1, 5, 9, 14)
    }

    @Test
    fun `reserveDraw null 이면 필드를 채우지 않는다 — 엔진이 NO_RESERVE_DRAW 로 계수`() {
        val proto = mapped(sampleWith(null)).getCompetitionSamples(0)

        proto.hasReserveDraw() shouldBe false
    }

    @Test
    fun `selectedNumbers 빈 집합도 그대로 왕복한다 — NotObserved`() {
        val draw = reserveDraw(priceCount = 1, numbers = emptySet())

        val proto = mapped(sampleWith(draw)).getCompetitionSamples(0)

        proto.reserveDraw.selectedNumbersList shouldBe emptyList()
        proto.reserveDraw.reservePricesCount shouldBe 1
    }
}
