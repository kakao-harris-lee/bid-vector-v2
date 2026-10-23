package bidvector.procurement

import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * M7/7C⑤-0 — **행 단위 조립**. 개찰 1위 축(기존 순수 커널에 맡긴다, 전부 아니면 무)과 복수예비가격
 * 자식 행(순번이 정체성이다, D-3E-1b (a)), 그리고 관측 시각·저장 키의 선택.
 */
class OpeningPromotionRankOneTest {
    // =========================================================================
    // 개찰 1위 축 — 기존 순수 커널에 맡긴다
    // =========================================================================
    @Test
    fun `순위 1 이 하나면 결정이다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf(
                            "prcbdrNm" to "일위업체",
                            "opengRank" to "1",
                            "bidprcAmt" to "900,000",
                            "bidprcrt" to "90.0",
                        ),
                    ),
                    axisRow(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("prcbdrNm" to "이위업체", "opengRank" to "2"),
                    ),
                ),
            )

        val determined = result.openingRankOne.shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
        determined.bid.bidderName shouldBe "일위업체"
        determined.bid.bidAmount shouldBe ObservedBidAmount(900_000L, Currency.KRW)
        determined.bid.bidRate shouldBe Rate.ofPercent(BigDecimal("90.0"))
        determined.observedAt shouldBe WALK_A
    }

    @Test
    fun `순위 1 이 둘이면 동값을 깨지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("prcbdrNm" to "가", "opengRank" to "1")),
                    axisRow(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("prcbdrNm" to "나", "opengRank" to "1")),
                ),
            )

        result.openingRankOne shouldBe OpeningRankOneOutcome.RankDuplicated(2, WALK_A)
    }

    @Test
    fun `순위 1 이 없으면 순위 부재다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("prcbdrNm" to "가", "opengRank" to "2")),
                ),
            )

        result.openingRankOne shouldBe OpeningRankOneOutcome.RankMissing(WALK_A)
    }

    @Test
    fun `개찰완료 축이 없으면 1위 축은 미관측이다`() {
        val result = promotedResult(groupOf(axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, emptyMap())))

        result.openingRankOne shouldBe OpeningRankOneOutcome.NotObserved
    }

    @Test
    fun `상호를 얻지 못한 투찰 행이 있으면 1위 축을 비운다`() {
        // 그 행을 버리면 「순위 1이 유일한가」의 모수가 조용히 줄고, 승인된 타입에는 「순위 1을
        // 특정했으나 상호가 없다」를 표현할 자리가 없다(알려진 제한).
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("prcbdrNm" to "가", "opengRank" to "1")),
                    axisRow(SourceEndpoint.OPENING_COMPLETE, WALK_A, mapOf("opengRank" to "2")),
                ),
            )

        result.openingRankOne shouldBe OpeningRankOneOutcome.NotObserved
    }

    @Test
    fun `투찰금액의 소수부는 1위 축에서도 깎이지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("prcbdrNm" to "가", "opengRank" to "1", "bidprcAmt" to "900000.5"),
                    ),
                ),
            )

        result.openingRankOne
            .shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
            .bid.bidAmount
            .shouldBeNull()
    }

    // =========================================================================
    // 자식 행의 정체성 — 순번
    // =========================================================================
    @Test
    fun `순번이 공백인 자식 행은 승격하지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("compnoRsrvtnPrceSno" to "  ", "bsisPlnprc" to "1000"),
                    ),
                ),
            )

        result.reservePrices.shouldContainExactly(emptyList())
    }

    @Test
    fun `같은 순번이 두 번 오면 첫 행만 쓴다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("compnoRsrvtnPrceSno" to "1", "bsisPlnprc" to "1000"),
                    ),
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("compnoRsrvtnPrceSno" to "1", "bsisPlnprc" to "2000"),
                    ),
                ),
            )

        result.reservePrices.single().baseReservePrice shouldBe ReservePriceCandidateAmount(1_000L, Currency.KRW)
    }

    @Test
    fun `추첨 표시는 Y·N 만 참·거짓이다`() {
        fun drawnFor(flag: String): Boolean? =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("compnoRsrvtnPrceSno" to "1", "drwtYn" to flag),
                    ),
                ),
            ).reservePrices.single().isDrawn

        drawnFor("Y") shouldBe true
        drawnFor("N") shouldBe false
        drawnFor("예").shouldBeNull()
    }

    // =========================================================================
    // 관측 시각과 저장 키
    // =========================================================================
    @Test
    fun `부모 관측 시각은 기여한 걷기 가운데 가장 늦은 시각이다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_B, mapOf("prtcptCnum" to "4")),
                ),
            )

        result.observedAt shouldBe WALK_B
    }

    @Test
    fun `저장 키는 부모 관측 시각을 낸 걷기에서 고른다`() {
        // 가드가 시각과 키를 함께 보므로 둘은 같은 행에서 온다 — 더 작은 키라도 더 이른 걷기의
        // 것이면 고르지 않는다.
        val outcome =
            promote(
                groupOf(
                    axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100"), key = "k-1"),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_B, mapOf("prtcptCnum" to "4"), key = "k-9"),
                    axisRow(SourceEndpoint.OPENING_COMPLETE, WALK_B, mapOf("prcbdrNm" to "가"), key = "k-5"),
                ),
            )

        val promotable = outcome.shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        promotable.result.observedAt shouldBe WALK_B
        promotable.observationKey shouldBe ObservationKey("k-5")
    }

    @Test
    fun `승격은 저장된 관측보다 늦을 때만 쓴다`() {
        val older = OpeningResult(NOTICE_UNDER_TEST, null, null, WALK_A)
        val newer = OpeningResult(NOTICE_UNDER_TEST, null, null, WALK_B)

        promotionIsNew(null, older) shouldBe true
        promotionIsNew(older, newer) shouldBe true
        // 같은 입력 두 번 — 관측 시각이 같아 쓰지 않는다(행 불변).
        promotionIsNew(older, older) shouldBe false
        promotionIsNew(newer, older) shouldBe false
    }

    @Test
    fun `쓸 걷기가 있는 축이 하나도 없으면 행을 만들지 않는다`() {
        promote(groupOf()) shouldBe OpeningPromotionOutcome.NoUsableAxis
    }

    // =========================================================================
    // 축 선언의 건전성 · 던지지 않는다
    // =========================================================================
    @Test
    fun `선언한 축은 그 개념의 계약 presentIn 안이다`() {
        OPENING_PROMOTION_SLOT_AXES.forEach { (concept, axes) ->
            val declared =
                PROMOTION_POLICY.fieldContracts
                    .contractsFor(concept)
                    .flatMap { it.presentIn }
                    .toSet()
            val unreachable = axes - declared
            unreachable shouldBe emptySet()
        }
    }

    @Test
    fun `원문 값이 무엇이든 던지지 않는다`() {
        val garbage =
            listOf("", " ", "-", "+", "NaN", "1e999999", "99999999999999999999999999", "0x10", "1,,2", "\u0000")
        garbage.forEach { value ->
            val group =
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf(
                            "plnprc" to value,
                            "bssamt" to value,
                            "bsisPlnprc" to value,
                            "compnoRsrvtnPrceSno" to value,
                            "drwtYn" to value,
                            "rlOpengDt" to value,
                            "prtcptCnum" to value,
                        ),
                    ),
                    axisRow(
                        SourceEndpoint.OPENING_AWARD_LIST,
                        WALK_A,
                        mapOf("sucsfbidAmt" to value, "sucsfbidRate" to value, "bidwinnrNm" to value),
                    ),
                    axisRow(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("prcbdrNm" to value, "opengRank" to value, "bidprcAmt" to value, "bidprcrt" to value),
                    ),
                )

            promote(group).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        }
    }

    @Test
    fun `과세 어휘를 리터럴로 짓지 않는다`() {
        // 계약의 과세 값이 무엇이든 그 값이 그대로 실린다 — UNKNOWN 리터럴 회귀를 잡는다.
        val contract = requireNotNull(PROMOTION_POLICY.fieldContracts.contractFor(RawKey("bssamt")))
        val result =
            promotedResult(groupOf(axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("bssamt" to "1"))))

        result.baseAmount?.vatTreatment shouldBe contract.vatTreatment
        contract.vatTreatment shouldBe VatTreatment.UNKNOWN
    }
}
