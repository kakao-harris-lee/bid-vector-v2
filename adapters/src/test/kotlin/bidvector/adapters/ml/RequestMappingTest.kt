package bidvector.adapters.ml

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import bidvector.workflow.prediction.CompetitionSample
import bidvector.workflow.prediction.ReserveDrawObservation
import contract.bidvector.ml.v1.AmountProvenanceKind
import contract.bidvector.ml.v1.Basis
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import contract.bidvector.ml.v1.Currency as ProtoCurrency
import contract.bidvector.ml.v1.VatTreatment as ProtoVatTreatment

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

    /**
     * verifier r1 F-7 — 예비가 `Money`의 basis·currency·provenance·vat 넷이 전부 왕복한다.
     * 기존 test는 `amountWon`만 대조했다 — basis를 `YEGA`로, vat을 `INCLUSIVE`로 바꿔도
     * 전부 초록이었다(D-4B7-1 문면이 못 박은 `BASE_AMOUNT`·`UNKNOWN`을 무측정으로 방치).
     */
    @Test
    fun `reserveDraw 예비가 Money 는 basis currency provenance vat 넷이 왕복한다`() {
        val draw = reserveDraw(priceCount = 1, numbers = setOf(1))

        val price = mapped(sampleWith(draw)).getCompetitionSamples(0).reserveDraw.getReservePrices(0)

        price.basis shouldBe Basis.BASIS_BASE_AMOUNT
        price.currency shouldBe ProtoCurrency.CURRENCY_KRW
        price.vatTreatment shouldBe ProtoVatTreatment.VAT_TREATMENT_UNKNOWN
        price.provenance shouldBe AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_PUBLISHED
    }

    /**
     * verifier r2 N-1 관련 — `mapRequest`/`toProtoReserveDraw`는 받은 `List<BaseAmount>`
     * 순서를 그대로 옮긴다(재정렬하지 않는다). 순번 정렬 자체는 `SampleConversion.
     * reservePriceAmounts`(workflow, `SampleEligibilityTest`의 N-1 test) 소관 — 이 test는
     * 그 인접 위험(매핑 층이 이미 정렬된 리스트를 다시 섞지 않는가)을 잰다. 같은 픽스처
     * 정신으로 DB 사전순에 대응하는 뒤섞인 입력을 그대로 주고 wire 순서가 입력 순서와
     * 바이트 단위로 같은지 대조한다.
     */
    @Test
    fun `reserveDraw 는 입력 List 순서를 그대로 옮긴다 — 재정렬하지 않는다`() {
        val lexicographicOrder = (1..15).map(Int::toString).sorted().map { it.toInt() }
        val scrambledDraw =
            ReserveDrawObservation(
                reservePrices =
                    lexicographicOrder.map { n ->
                        BaseAmount(
                            900_000_000L + n,
                            Currency.KRW,
                            VatTreatment.UNKNOWN,
                            Provenance.Published(NoticeRound.of("000")),
                        )
                    },
                selectedNumbers = setOf(1),
            )

        val proto = mapped(sampleWith(scrambledDraw)).getCompetitionSamples(0)

        proto.reserveDraw.reservePricesList.map { it.amountWon } shouldBe lexicographicOrder.map { 900_000_000L + it }
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

    /**
     * D-5F2-4(scope.md 계약 갱신 (2)) — `ML_CALL_POLICY`(운영 정책, `MlCallPolicyData.kt`)를
     * `mapRequest`에 그대로 흘려 실제 `CalculateOptimalBidRequest.envelope.featureSchemaVersion`
     * 이 그 정책 값과 같은지 잰다. `testMlCallPolicy()`(다른 test 전부가 쓰는 좁은 값)를 쓰지
     * 않는 것이 핵심이다 — 그래야 `PredictionEnvelopeMapping.buildPredictionEnvelope`가 그
     * 값을 조용히 다른 문자열로 바꿔도(verifier r1 변이 B) 이 test 가 잡는다. 저장소에 이
     * 경로(정책 값 → 요청 proto)를 재는 test 가 이전까지 없었다(verifier r1 L-1).
     */
    @Test
    fun `feature_schema_version 은 실 정책 ML_CALL_POLICY 값을 그대로 요청 envelope 에 싣는다`() {
        val resolution = ML_CALL_POLICY.resolve(LocalDate.now())
        resolution.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()
        val policy = resolution.value

        val proto =
            mapRequest(
                request = testBidPredictionRequest(),
                requestId = "req-1",
                policy = policy,
                deadlinePolicyVersion = "deadline-test",
            )

        proto.envelope.featureSchemaVersion shouldBe policy.featureSchemaVersion
    }
}
