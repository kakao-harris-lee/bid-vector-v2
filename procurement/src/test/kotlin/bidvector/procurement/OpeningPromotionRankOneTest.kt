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
    fun `잠정 1위가 실려도 확정 낙찰 세 칸은 부재다`() {
        // 위협 모델 첫 항목(잠정 → 확정 오염). 구조는 이중으로 닫혀 있다 — 확정 축의 슬롯 선언이
        // 낙찰 목록 하나이고, 계약 `presentIn` 이 다시 거른다. 그 성질을 값으로 잰다.
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
                ),
            )

        result.openingRankOne.shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
        result.finalAwardAmount.shouldBeNull()
        result.finalAwardCompanyName.shouldBeNull()
        result.winningRate.shouldBeNull()
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
    fun `일시 축의 DATETIME 계약은 하나다`() {
        // code-review r1 low 2 — 일시 해석만 `presentIn` 으로 거르지 않고 첫 계약을 집는다(기존
        // 진입점의 규칙). 그 개념의 DATETIME 계약이 둘이 되는 날 이 단언이 먼저 붉어진다.
        val dateTimeContracts =
            PROMOTION_POLICY.fieldContracts
                .contractsFor(FieldConcept.ACTUAL_OPENING_AT)
                .filter { it.scale == FieldScale.DATETIME_NO_ZONE }

        dateTimeContracts.size shouldBe 1
    }

    @Test
    fun `과세를 버리는 두 금액 축의 계약은 UNKNOWN 이다`() {
        // code-review r1 low 3 — 낙찰금액·예정가격 값 객체는 과세를 UNKNOWN 으로 고정하므로
        // 사상이 읽은 계약 값이 버려진다. 오늘은 둘 다 UNKNOWN 이라 무손실이고, 그 등식을 잰다.
        fun vatOf(concept: FieldConcept) =
            PROMOTION_POLICY.fieldContracts
                .contractsFor(concept)
                .single { it.scale == FieldScale.WON_INTEGER }
                .vatTreatment

        vatOf(FieldConcept.AWARD_AMOUNT) shouldBe VatTreatment.UNKNOWN
        vatOf(FieldConcept.RESERVE_PRICE) shouldBe VatTreatment.UNKNOWN
    }

    @Test
    fun `같은 시각이면 축 열거 순서가 앞선 축이 이긴다`() {
        // code-review r1 low 4 — 비교자의 2차 키. 참가업체수는 목록 축 **둘**에서 오므로 실제로
        // 갈리는 조합이다. 이 단언이 잠그는 것은 **방향**이다: 시각이 같으면 열거가 앞선 축이 이긴다.
        // 기대값을 열거에서 읽으므로 열거 순서가 뒤집히면 이 단언은 코드와 함께 움직인다 — 그래서
        // 지금의 순서 자체를 아래 한 줄로 따로 못박는다(CR r2 MEDIUM-2: 앞 판 주석은 이 test 가
        // 순서 변경에 붉어진다고 거꾸로 적었다). 두 단언이 함께 있어야 방향과 현재 순서가 다 잠긴다.
        (SourceEndpoint.OPENING_AWARD_LIST.ordinal < SourceEndpoint.OPENING_RESULT_LIST.ordinal) shouldBe true
        val earlier =
            listOf(SourceEndpoint.OPENING_AWARD_LIST, SourceEndpoint.OPENING_RESULT_LIST).minBy { it.ordinal }
        val counts = mapOf(SourceEndpoint.OPENING_AWARD_LIST to "9", SourceEndpoint.OPENING_RESULT_LIST to "3")
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.OPENING_AWARD_LIST, WALK_A, mapOf("prtcptCnum" to "9")),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "3")),
                ),
            )

        result.participantCount shouldBe counts.getValue(earlier).toInt()
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
