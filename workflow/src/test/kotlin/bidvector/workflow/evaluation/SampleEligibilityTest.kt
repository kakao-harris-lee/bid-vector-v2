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
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
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
                businessCategory = categoryCode?.let { BusinessCategory(CategoryCode(it), CategoryLabel("공사")) },
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
        outcome.sample.categoryCode shouldBe CategoryCode("A01")
        outcome.sample.agencyId shouldBe null
        outcome.sample.awardRate shouldBe Rate.ofFraction(BigDecimal("0.9200"))
        val reserveDraw = requireNotNull(outcome.sample.reserveDraw)
        reserveDraw.reservePrices.size shouldBe 15
        reserveDraw.selectedNumbers shouldBe setOf(1, 5, 9, 14)
        // 우회 (18) — Published 의 회차는 표본 공고 자기 회차("001")다. 대상 공고 회차를 빌리지 않는다.
        reserveDraw.reservePrices.first().provenance shouldBe Provenance.Published(NoticeRound.of("001"))
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
    fun `RESERVE_PRICE_COUNT_MISMATCH — 14행`() {
        val notice = testNotice()
        val opening = testOpening(notice.id, reservePrices = fifteenRows().dropLast(1))

        judge(notice, opening) shouldBe
            SampleEligibilityOutcome.Excluded(SampleExclusionReason.RESERVE_PRICE_COUNT_MISMATCH)
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
