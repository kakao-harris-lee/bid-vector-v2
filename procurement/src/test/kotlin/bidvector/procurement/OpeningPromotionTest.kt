package bidvector.procurement

import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.export
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * M7/7C⑤-0 — 원문 → canonical 사상의 **값 축**. 키별 값 · 부재 · 해석 불가 · 구조적 부재 ·
 * 확정 낙찰 축(원천이 생기면 코드 변경 0 으로 따라온다, D-7C5P-1).
 */
class OpeningPromotionTest {
    // =========================================================================
    // 사상 — 키별 값
    // =========================================================================
    @Test
    fun `예비가격 상세 원문이 공고 층 금액 슬롯과 자식 행을 채운다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf(
                            "plnprc" to "1,234,000",
                            "bssamt" to "1,200,000",
                            "rlOpengDt" to "2026-10-19 14:00:00",
                            "compnoRsrvtnPrceSno" to "3",
                            "bsisPlnprc" to "1,210,000",
                            "drwtYn" to "Y",
                        ),
                    ),
                ),
            )

        result.plannedPrice?.export()?.won shouldBe 1_234_000L
        result.baseAmount?.export()?.won shouldBe 1_200_000L
        result.actualOpeningAt shouldBe Instant.parse("2026-10-19T05:00:00Z")
        result.reservePrices shouldContainExactly
            listOf(
                OpeningReservePriceRow(
                    sequenceNumber = "3",
                    baseReservePrice = ReservePriceCandidateAmount(1_210_000L, Currency.KRW),
                    isDrawn = true,
                    observedAt = WALK_A,
                    drawCount = null,
                ),
            )
    }

    @Test
    fun `통화와 과세는 계약에서 온다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("bssamt" to "1000")),
                ),
            )
        val contract = PROMOTION_POLICY.fieldContracts.contractFor(RawKey("bssamt"))

        result.baseAmount?.currency shouldBe Currency.KRW
        result.baseAmount?.vatTreatment shouldBe contract?.vatTreatment
    }

    @Test
    fun `provenance 는 계약 템플릿이 정한다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("plnprc" to "500", "bssamt" to "400"),
                    ),
                ),
            )

        // `bssamt` 는 PUBLISHED 템플릿 — 차수를 실은 게시값이다.
        result.baseAmount?.provenance shouldBe Provenance.Published(NOTICE_UNDER_TEST.round)
        // `plnprc` 는 NOT_APPLICABLE — 출처를 지어내지 않고 「선언되지 않았다」로 나른다.
        result.plannedPrice?.provenance shouldBe Provenance.Undeclared
    }

    @Test
    fun `개찰결과 목록 원문이 진행구분과 참가업체수를 채운다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.OPENING_RESULT_LIST,
                        WALK_A,
                        mapOf("progrsDivCdNm" to "개찰완료", "prtcptCnum" to "7"),
                    ),
                ),
            )

        result.progressDivision shouldBe "개찰완료"
        result.participantCount shouldBe 7
    }

    @Test
    fun `진행구분은 개찰완료 축의 다른 키로도 온다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("opengRsltDivNm" to "유찰", "prcbdrNm" to "표본업체", "opengRank" to "1"),
                    ),
                ),
            )

        result.progressDivision shouldBe "유찰"
    }

    @Test
    fun `같은 개념을 두 축이 나르면 늦게 관측된 축이 이긴다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "3")),
                    axisRow(SourceEndpoint.OPENING_AWARD_LIST, WALK_B, mapOf("prtcptCnum" to "9")),
                ),
            )

        result.participantCount shouldBe 9
    }

    // =========================================================================
    // 부재 · 해석 불가 — 0·빈 문자열로 적재하지 않는다
    // =========================================================================
    @Test
    fun `키가 없으면 슬롯이 부재다`() {
        val result = promotedResult(groupOf(axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, emptyMap())))

        result.participantCount.shouldBeNull()
        result.progressDivision.shouldBeNull()
        result.plannedPrice.shouldBeNull()
        result.baseAmount.shouldBeNull()
        result.actualOpeningAt.shouldBeNull()
        result.reservePrices.shouldContainExactly(emptyList())
    }

    @Test
    fun `공백 값은 부재다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.OPENING_RESULT_LIST,
                        WALK_A,
                        mapOf("progrsDivCdNm" to "   ", "prtcptCnum" to ""),
                    ),
                ),
            )

        result.progressDivision.shouldBeNull()
        result.participantCount.shouldBeNull()
    }

    @Test
    fun `해석되지 않는 값은 부재이고 0 이 아니다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("plnprc" to "미정", "rlOpengDt" to "어제"),
                    ),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "일곱")),
                ),
            )

        result.participantCount.shouldBeNull()
        result.plannedPrice.shouldBeNull()
        result.actualOpeningAt.shouldBeNull()
    }

    @Test
    fun `음수 셈은 부재다`() {
        val result =
            promotedResult(
                groupOf(axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "-1"))),
            )

        result.participantCount.shouldBeNull()
    }

    @Test
    fun `소수부가 섞인 금액은 깎지 않고 부재다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf("plnprc" to "1234.5", "bssamt" to "1000.00"),
                    ),
                ),
            )

        result.plannedPrice.shouldBeNull()
        // 소수부가 0 이면 원 단위 정수다 — 값이 사라지지 않는다.
        result.baseAmount?.export()?.won shouldBe 1_000L
    }

    @Test
    fun `해석되지 않는 최신 값이 더 오래된 축의 값에 가려지지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "3")),
                    axisRow(SourceEndpoint.OPENING_AWARD_LIST, WALK_B, mapOf("prtcptCnum" to "셋")),
                ),
            )

        result.participantCount.shouldBeNull()
    }

    // =========================================================================
    // 구조적 부재 — 계약에 없는 축
    // =========================================================================
    @Test
    fun `총예가건수와 추첨횟수는 계약에 없어 어떤 원문으로도 채워지지 않는다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.RESERVE_PRICE_DETAIL,
                        WALK_A,
                        mapOf(
                            "compnoRsrvtnPrceSno" to "1",
                            "totRsrvtnPrceNum" to "15",
                            "drwtNum" to "4",
                        ),
                    ),
                ),
            )

        result.totalReservePriceCandidateCount.shouldBeNull()
        result.reservePrices
            .single()
            .drawCount
            .shouldBeNull()
        PROMOTION_POLICY.fieldContracts.contractFor(RawKey("totRsrvtnPrceNum")).shouldBeNull()
        PROMOTION_POLICY.fieldContracts.contractFor(RawKey("drwtNum")).shouldBeNull()
    }

    @Test
    fun `사업자등록번호와 대표자명은 어느 계약에도 등재되지 않았다`() {
        PROMOTION_POLICY.fieldContracts.contractFor(RawKey("bidwinnrBizno")).shouldBeNull()
        PROMOTION_POLICY.fieldContracts.contracts.none { it.rawName.name.contains("Bizno") } shouldBe true
        PROMOTION_POLICY.fieldContracts.contracts.none { it.rawName.name.contains("Ceo") } shouldBe true
    }

    @Test
    fun `추첨번호 축은 원문에 번호가 있어도 승격되지 않는다`() {
        // D-7C5P-2 — 개찰완료의 추첨번호 키는 **투찰자가 고른 번호**이지 추첨된 번호가 아니다
        // (D-6G-38). 승인 결정 둘이 어긋나 그 축을 비운다.
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.OPENING_COMPLETE,
                        WALK_A,
                        mapOf("prcbdrNm" to "표본업체", "opengRank" to "1", "drwtNo1" to "3", "drwtNo2" to "7"),
                    ),
                ),
            )

        result.drawNumbers shouldBe DrawNumberObservation.NotObserved
    }

    @Test
    fun `역산 파생 기초금액은 승격이 짓지 않는다`() {
        val result =
            promotedResult(groupOf(axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "900"))))

        result.derivedBaseAmount.shouldBeNull()
    }

    // =========================================================================
    // 확정 낙찰 축 — 오늘은 원천이 없고, 원천이 생기면 코드 변경 없이 따라온다(D-7C5P-1)
    // =========================================================================
    @Test
    fun `낙찰 목록 원문이 없으면 확정 축이 비어 있다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.OPENING_RESULT_LIST,
                        WALK_A,
                        mapOf("progrsDivCdNm" to "개찰완료", "sucsfbidAmt" to "9999", "bidwinnrNm" to "엉뚱업체"),
                    ),
                ),
            )

        // 같은 키가 **다른 축**의 원문에 섞여 와도 읽지 않는다 — 계약의 `presentIn` 이 축을 가른다.
        result.finalAwardAmount.shouldBeNull()
        result.finalAwardCompanyName.shouldBeNull()
        result.winningRate.shouldBeNull()
    }

    @Test
    fun `낙찰 목록 축의 원문이 들어오면 확정 축이 코드 변경 없이 채워진다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(
                        SourceEndpoint.OPENING_AWARD_LIST,
                        WALK_A,
                        mapOf(
                            "sucsfbidAmt" to "1,000,000",
                            "bidwinnrNm" to "낙찰업체",
                            "sucsfbidRate" to "87.995",
                        ),
                    ),
                ),
            )

        result.finalAwardAmount?.export()?.won shouldBe 1_000_000L
        result.finalAwardCompanyName shouldBe "낙찰업체"
        result.winningRate shouldBe Rate.ofPercent(BigDecimal("87.995"))
    }
}
