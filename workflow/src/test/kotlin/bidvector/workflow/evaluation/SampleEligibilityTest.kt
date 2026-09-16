package bidvector.workflow.evaluation

import bidvector.decision.ProvenancePolicyData
import bidvector.procurement.BusinessCategory
import bidvector.procurement.CategoryCode
import bidvector.procurement.CategoryLabel
import bidvector.procurement.DrawNumberObservation
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.OpeningRankOneBid
import bidvector.procurement.OpeningRankOneOutcome
import bidvector.procurement.OpeningReservePriceRow
import bidvector.procurement.OpeningResult
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ReservePriceCandidateAmount
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.ResolvedEstimatedAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val OBSERVED_AT = Instant.parse("2026-09-16T00:00:00Z")

// 2026-09-16T15:30:00Z = Asia/Seoul 2026-09-17 00:30 — 시간대 경계를 실측하는 값(D-4B7-7).
private val ACTUAL_OPENING_AT = Instant.parse("2026-09-16T15:30:00Z")

private val PROVENANCE_POLICY: Resolution.Resolved<ProvenancePolicyData> =
    SAMPLE_PROVENANCE_POLICY.resolve(LocalDate.of(2026, 9, 16)) as Resolution.Resolved<ProvenancePolicyData>
private val ELIGIBILITY_POLICY = SampleEligibilityPolicyData(expectedReservePriceCount = 15)

/**
 * `judgeEligibility`(M4/4B-7, D-4B7-2) 규칙표 — 사유별 실격(②~⑦) + 정상 변환 전수 +
 * `NotObserved` → 빈 집합 + `Published(round)` 회차 대조(우회 (18)). port를 읽지 않는 순수
 * 함수이므로 fake port 없이 값만으로 잰다.
 */
class SampleEligibilityTest {
    private fun reserveRow(
        sequence: String,
        won: Long = 900_000_000L,
        hasPrice: Boolean = true,
    ): OpeningReservePriceRow =
        OpeningReservePriceRow(
            sequenceNumber = sequence,
            baseReservePrice = if (hasPrice) ReservePriceCandidateAmount(won, Currency.KRW) else null,
            isDrawn = false,
            observedAt = OBSERVED_AT,
        )

    private fun fifteenRows(hasPrice: Boolean = true): List<OpeningReservePriceRow> =
        (1..15).map { n -> reserveRow(n.toString().padStart(3, '0'), 900_000_000L + n, hasPrice) }

    /**
     * verifier r2 N-1 — `reserve_price_sequence`는 zero-pad 없는 원문 그대로 저장된다
     * (`OpeningReservePriceRow.init`은 공백만 거부, `KonepsOpeningResultSourceTest`의
     * `sno.toString()`이 실제 수집 형태다) — 실 DB `ORDER BY reserve_price_sequence`(TEXT)는
     * 사전순이라 `"1","10","11",…,"15","2",…,"9"` 순으로 행을 돌려준다. `fifteenRows()`는
     * zero-pad라 이미 정렬된 입력이라 정렬 로직 자체를 재지 못한다 — 이 헬퍼가 그 사전순을
     * 그대로 재현한다.
     */
    private fun lexicographicOrderRows(): List<OpeningReservePriceRow> {
        val lexicographicSequence = (1..15).map(Int::toString).sorted()
        return lexicographicSequence.map { sequence -> reserveRow(sequence, 900_000_000L + sequence.toInt()) }
    }

    private fun testNotice(
        number: String = "20260916001",
        round: String = "000",
        categoryCode: String? = "A01",
        baseAmountWon: Long? = 1_000_000_000L,
        estimatedAmountWon: Long? = null,
    ): Notice {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of(round))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to round),
                SourceEndpoint.NOTICE_LIST,
                OBSERVED_AT,
            )
        val baseAmount =
            baseAmountWon?.let {
                ResolvedBaseAmount.Direct.of(it, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Published(id.round))
            }
        val estimatedAmount =
            estimatedAmountWon?.let {
                ResolvedEstimatedAmount(
                    RawKey("presmptPrce"),
                    EstimatedAmount(it, Currency.KRW, VatTreatment.EXCLUSIVE, Provenance.Published(id.round)),
                )
            }
        return Notice.collected(
            NoticeCollected(
                id = id,
                businessCategory = categoryCode?.let { BusinessCategory(CategoryCode.of(it), CategoryLabel("공사")) },
                baseAmount = baseAmount,
                estimatedAmount = estimatedAmount,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            ),
        )
    }

    private fun testOpening(
        noticeId: NoticeId,
        rankOne: OpeningRankOneOutcome = determinedRankOne(),
        reservePrices: List<OpeningReservePriceRow> = fifteenRows(),
        drawNumbers: DrawNumberObservation = DrawNumberObservation.Verified(setOf(1, 5, 9, 14), OBSERVED_AT),
        actualOpeningAt: Instant? = ACTUAL_OPENING_AT,
        finalAwardAmount: AwardAmount? = null,
        winningRate: Rate? = Rate.ofFraction(BigDecimal("0.9200")),
    ): OpeningResult =
        OpeningResult(
            noticeId = noticeId,
            winningRate = winningRate,
            derivedBaseAmount = null,
            observedAt = OBSERVED_AT,
            finalAwardAmount = finalAwardAmount,
            actualOpeningAt = actualOpeningAt,
            reservePrices = reservePrices,
            openingRankOne = rankOne,
            drawNumbers = drawNumbers,
        )

    private fun determinedRankOne(bidRate: Rate? = Rate.ofFraction(BigDecimal("0.9200"))): OpeningRankOneOutcome =
        OpeningRankOneOutcome.Determined(
            OpeningRankOneBid(bidderName = "표본업체", bidAmount = null, bidRate = bidRate),
            OBSERVED_AT,
        )

    private fun judge(
        notice: Notice,
        opening: OpeningResult,
    ): SampleEligibilityOutcome = judgeEligibility(notice, opening, ELIGIBILITY_POLICY, PROVENANCE_POLICY)

    // ---- 정상 — 변환 결과 필드 전수 ----

    @Test
    fun `정상 — 자격을 모두 만족하면 Eligible, 필드가 전수 변환된다`() {
        val notice = testNotice(round = "001")
        val opening = testOpening(notice.id)

        val outcome = judge(notice, opening) as SampleEligibilityOutcome.Eligible

        outcome.sample.observedBidRate shouldBe Rate.ofFraction(BigDecimal("0.9200"))
        outcome.sample.baseAmount shouldBe notice.baseAmount?.amount
        outcome.sample.openedOn shouldBe LocalDate.of(2026, 9, 17) // D-4B7-7 — Asia/Seoul 로 접은 날짜
        outcome.sample.categoryCode shouldBe CategoryCode.of("A01")
        outcome.sample.agencyId shouldBe null
        outcome.sample.awardRate shouldBe Rate.ofFraction(BigDecimal("0.9200"))
        val reserveDraw = requireNotNull(outcome.sample.reserveDraw)
        reserveDraw.reservePrices.size shouldBe 15
        reserveDraw.selectedNumbers shouldBe setOf(1, 5, 9, 14)
        // verifier r1 F-7 — Money 4성분 전수(basis·currency·vat·provenance), amountWon 하나만이 아니다.
        val firstPrice = reserveDraw.reservePrices.first()
        firstPrice.basis shouldBe Basis.BASE_AMOUNT
        firstPrice.currency shouldBe Currency.KRW
        firstPrice.vatTreatment shouldBe VatTreatment.UNKNOWN
        // verifier r1 F-4 — 순번 "001"이 위치 0(=selected_numbers 의 번호 1)과 일치한다.
        firstPrice shouldBe reserveDraw.reservePrices[0]
        (firstPrice.export().won) shouldBe 900_000_001L
        // 우회 (18) — Published 의 회차는 표본 공고 자기 회차("001")다. 대상 공고 회차를 빌리지 않는다.
        firstPrice.provenance shouldBe Provenance.Published(NoticeRound.of("001"))
    }

    /**
     * verifier r2 N-1(HIGH) — `reservePriceAmounts`의 정렬(`RESERVE_PRICE_SEQUENCE_ORDER`)이
     * 실제로 측정되는 유일한 자리. 입력을 DB 사전순(`lexicographicOrderRows()`)으로 주고
     * 출력 `reserveDraw.reservePrices`가 **순번 1..15 순**(사전순이 아니라)인지 잰다 —
     * 정렬을 지우는 편집(`rows.sortedWith(...)` → `rows`)이 이 test에서만 붉어진다
     * (`fifteenRows()`는 zero-pad라 이미 정렬돼 있어 이 결함을 못 잡는다).
     */
    @Test
    fun `예비가격은 DB 사전순 입력이어도 순번 1 부터 15 순으로 정렬돼 나간다`() {
        val notice = testNotice()
        val opening = testOpening(notice.id, reservePrices = lexicographicOrderRows())

        val outcome = judge(notice, opening) as SampleEligibilityOutcome.Eligible

        val wonInOutputOrder =
            outcome.sample.reserveDraw
                ?.reservePrices
                ?.map { it.export().won }
        wonInOutputOrder shouldBe (1..15).map { n -> 900_000_000L + n }
    }

    @Test
    fun `NotObserved 추첨 번호는 빈 집합으로 통과한다`() {
        val notice = testNotice()
        val opening = testOpening(notice.id, drawNumbers = DrawNumberObservation.NotObserved)

        val outcome = judge(notice, opening) as SampleEligibilityOutcome.Eligible

        outcome.sample.reserveDraw?.selectedNumbers shouldBe emptySet()
    }

    @Test
    fun `RangeCheckUnavailable 추첨 번호는 그 번호 집합으로 통과한다`() {
        val notice = testNotice()
        val opening =
            testOpening(notice.id, drawNumbers = DrawNumberObservation.RangeCheckUnavailable(setOf(2, 4), OBSERVED_AT))

        val outcome = judge(notice, opening) as SampleEligibilityOutcome.Eligible

        outcome.sample.reserveDraw?.selectedNumbers shouldBe setOf(2, 4)
    }

    // ---- 사유별 실격(D-4B7-2 ②~⑦) ----

    @Test
    fun `RANK_ONE_RATE_MISSING — NotObserved`() {
        val notice = testNotice()
        val opening = testOpening(notice.id, rankOne = OpeningRankOneOutcome.NotObserved)

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RANK_ONE_RATE_MISSING)
    }

    @Test
    fun `RANK_ONE_RATE_MISSING — RankMissing RankDuplicated`() {
        val notice = testNotice()

        judge(notice, testOpening(notice.id, rankOne = OpeningRankOneOutcome.RankMissing(OBSERVED_AT))) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RANK_ONE_RATE_MISSING)
        judge(
            notice,
            testOpening(notice.id, rankOne = OpeningRankOneOutcome.RankDuplicated(2, OBSERVED_AT)),
        ) shouldBe SampleEligibilityOutcome.Excluded(SampleExclusionReason.RANK_ONE_RATE_MISSING)
    }

    @Test
    fun `RANK_ONE_RATE_MISSING — Determined 이지만 bidRate 없음(협상 계약)`() {
        val notice = testNotice()
        val opening = testOpening(notice.id, rankOne = determinedRankOne(bidRate = null))

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RANK_ONE_RATE_MISSING)
    }

    @Test
    fun `RESERVE_PRICE_COUNT_MISMATCH — 14행(미만)`() {
        val notice = testNotice()
        val opening = testOpening(notice.id, reservePrices = fifteenRows().dropLast(1))

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RESERVE_PRICE_COUNT_MISMATCH)
    }

    @Test
    fun `RESERVE_PRICE_COUNT_MISMATCH — 16행(초과)`() {
        val notice = testNotice()
        val sixteenRows = fifteenRows() + reserveRow("016", won = 900_000_016L)
        val opening = testOpening(notice.id, reservePrices = sixteenRows)

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RESERVE_PRICE_COUNT_MISMATCH)
    }

    @Test
    fun `RESERVE_PRICE_SEQUENCE_INVALID — 건수는 15 지만 순번이 002 부터 016 까지다`() {
        val notice = testNotice()
        val shiftedRows = (2..16).map { n -> reserveRow(n.toString().padStart(3, '0'), 900_000_000L + n) }
        val opening = testOpening(notice.id, reservePrices = shiftedRows)

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RESERVE_PRICE_SEQUENCE_INVALID)
    }

    @Test
    fun `RESERVE_PRICE_SEQUENCE_INVALID — 순번이 정수로 파싱되지 않는다`() {
        val notice = testNotice()
        val rows = fifteenRows().toMutableList()
        rows[0] = reserveRow("XYZ")
        val opening = testOpening(notice.id, reservePrices = rows)

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RESERVE_PRICE_SEQUENCE_INVALID)
    }

    @Test
    fun `RESERVE_PRICE_MISSING — 15행 중 하나라도 baseReservePrice 없음`() {
        val notice = testNotice()
        val rows = fifteenRows().toMutableList()
        rows[3] = reserveRow("004", hasPrice = false)
        val opening = testOpening(notice.id, reservePrices = rows)

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RESERVE_PRICE_MISSING)
    }

    @Test
    fun `DRAW_NUMBERS_OUT_OF_RANGE`() {
        val notice = testNotice()
        val opening =
            testOpening(
                notice.id,
                drawNumbers = DrawNumberObservation.OutOfRange(setOf(16), 1..15, OBSERVED_AT),
            )

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.DRAW_NUMBERS_OUT_OF_RANGE)
    }

    @Test
    fun `BASE_AMOUNT_MISSING`() {
        val notice = testNotice(baseAmountWon = null)
        val opening = testOpening(notice.id)

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.BASE_AMOUNT_MISSING)
    }

    @Test
    fun `OPENING_DATE_MISSING`() {
        val notice = testNotice()
        val opening = testOpening(notice.id, actualOpeningAt = null)

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.OPENING_DATE_MISSING)
    }

    // ---- D-4B7-8 — provenance 라벨(첫 production 호출부) ----

    @Test
    fun `라벨 — 기초금액이 정수·낙찰가 파생과 무관하면 Clean`() {
        val notice = testNotice(baseAmountWon = 1_000_000_000L, estimatedAmountWon = 1_000_000_000L)
        val opening =
            testOpening(
                notice.id,
                finalAwardAmount = AwardAmount(500_000_000L, Currency.KRW, Provenance.DerivedFromOpening),
                winningRate = Rate.ofFraction(BigDecimal("0.5")),
            )

        val outcome = judge(notice, opening) as SampleEligibilityOutcome.Eligible

        outcome.sample.baseAmountProvenanceLabel shouldBe BaseAmountProvenance.Clean
    }

    @Test
    fun `라벨 — 기초금액이 추정가격의 신뢰 상한(1점15배)을 넘으면 SuspectRatio`() {
        val notice = testNotice(baseAmountWon = 1_200_000_001L, estimatedAmountWon = 1_000_000_000L)
        val opening = testOpening(notice.id)

        val outcome = judge(notice, opening) as SampleEligibilityOutcome.Eligible

        outcome.sample.baseAmountProvenanceLabel shouldBe BaseAmountProvenance.SuspectRatio
    }
}
