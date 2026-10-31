package bidvector.workflow.result

import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidStateTag
import bidvector.bidding.SubmittedBidAmount
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * 집계 둘(운영자 기록 축 · 서버 대사 축)과 차이(M7/7C⑤-b, D-7C5B-9).
 *
 * 여기서 잠기는 것: **분모 0 이 0% 가 되지 않는다**는 것 · 판정 불가·유찰이 분모에 들지
 * 않는다는 것(우회 ⑤) · 「표본 0」과 「전부 제외」가 다른 사유라는 것 · 차이가 **잠정 1위로
 * 채워지지 않는다**는 것(우회 ⑥ — 그 함수의 시그니처에 잠정 금액 자리가 없다).
 */
class MyBidResultTallyTest {
    private fun award(won: Long) = AwardAmount(won, Currency.KRW, Provenance.DerivedFromOpening)

    private fun mine(
        won: Long,
        currency: Currency = Currency.KRW,
    ) = SubmittedBidAmount(won, currency, VatTreatment.INCLUSIVE, BidAmountOrigin.OperatorEntered)

    /** 두 축의 표본 수가 같은 집계 하나 — 등식 단언이 통과하는 쪽의 조립. */
    private fun aggregateOf(
        states: List<BidStateTag>,
        verdicts: List<ReconciliationVerdict>,
    ) = MyBidResultAggregate(
        sampleSize = states.size,
        recorded = recordedAxisTally(states),
        reconciled = reconciledAxisTally(verdicts),
        blockersObserved = verdicts.filterIsInstance<ReconciliationVerdict.Undecidable>().map { it.blocker }.toSet(),
    )

    // ---------------------------------------------------------------- 집계 축

    @Test
    fun `분모는 낙찰 더하기 패찰이고 표본은 제외까지 더한 값이다`() {
        val tally =
            reconciledAxisTally(
                listOf(
                    ReconciliationVerdict.Won,
                    ReconciliationVerdict.Lost,
                    ReconciliationVerdict.Lost,
                    ReconciliationVerdict.FailedBid,
                    ReconciliationVerdict.Undecidable(ReconciliationBlocker.NOT_OPENED_YET),
                ),
            )
        tally.wonCount shouldBe 1
        tally.lostCount shouldBe 2
        tally.failedOrRebidCount shouldBe 1
        tally.undecidedCount shouldBe 1
        tally.denominator shouldBe 3
        tally.sampleSize shouldBe 5
    }

    /** 재입찰도 유찰과 같은 제외 칸이다 — 분모 밖이고 판정 불가가 아니다. */
    @Test
    fun `재입찰은 유찰과 같은 제외 칸에 든다`() {
        reconciledAxisTally(listOf(ReconciliationVerdict.Rebid)).failedOrRebidCount shouldBe 1
        reconciledAxisTally(listOf(ReconciliationVerdict.Rebid)).undecidedCount shouldBe 0
    }

    @Test
    fun `분모가 0 이면 비율이 아니라 부재다`() {
        val allUndecided =
            reconciledAxisTally(
                listOf(ReconciliationVerdict.Undecidable(ReconciliationBlocker.WINNER_NAME_NOT_PUBLISHED)),
            )
        allUndecided.denominator shouldBe 0
        allUndecided.rate shouldBe AwardRateState.Absent(AwardRateAbsence.NO_DECIDED_CASES)
    }

    /** 「투찰 기록이 없다」와 「전부 분모 밖이다」는 다른 사실이다 — 접으면 화면이 둘을 구별 못 한다. */
    @Test
    fun `표본 0 과 전부 제외는 다른 사유다`() {
        reconciledAxisTally(emptyList()).rate shouldBe AwardRateState.Absent(AwardRateAbsence.NO_RECORDED_BIDS)
        reconciledAxisTally(listOf(ReconciliationVerdict.FailedBid)).rate shouldBe
            AwardRateState.Absent(AwardRateAbsence.NO_DECIDED_CASES)
    }

    @Test
    fun `분모가 있으면 비율이 분자 나누기 분모다`() {
        val tally = reconciledAxisTally(listOf(ReconciliationVerdict.Won, ReconciliationVerdict.Lost))
        val measured = tally.rate.shouldBeInstanceOf<AwardRateState.Measured>()
        measured.rate.fraction.compareTo(BigDecimal("0.5")) shouldBe 0
    }

    /** 무한소수가 나오는 분모에서도 값이 선다(반올림 자리가 선언돼 있다). */
    @Test
    fun `표본 셋 중 하나 낙찰도 비율이 선다`() {
        val tally =
            reconciledAxisTally(
                listOf(ReconciliationVerdict.Won, ReconciliationVerdict.Lost, ReconciliationVerdict.Lost),
            )
        val measured = tally.rate.shouldBeInstanceOf<AwardRateState.Measured>()
        measured.rate.fraction.compareTo(BigDecimal.ZERO) shouldBe 1
        measured.rate.fraction.compareTo(BigDecimal("0.5")) shouldBe -1
    }

    // ------------------------------------------------------- 운영자 기록 축

    @Test
    fun `운영자 기록 축은 확정 상태 셋만 세고 나머지는 미확정이다`() {
        val tally =
            recordedAxisTally(
                listOf(
                    BidStateTag.WON,
                    BidStateTag.LOST,
                    BidStateTag.FAILED_OR_REBID,
                    BidStateTag.BID_RECORDED,
                    BidStateTag.AWAITING_OPENING,
                ),
            )
        tally.wonCount shouldBe 1
        tally.lostCount shouldBe 1
        tally.failedOrRebidCount shouldBe 1
        tally.undecidedCount shouldBe 2
        tally.denominator shouldBe 2
    }

    // ------------------------------------------------------------ 두 축 합

    @Test
    fun `두 축의 표본 수가 어긋나면 집계가 서지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            MyBidResultAggregate(
                sampleSize = 2,
                recorded = recordedAxisTally(listOf(BidStateTag.WON)),
                reconciled = reconciledAxisTally(listOf(ReconciliationVerdict.Won)),
                blockersObserved = emptySet(),
            )
        }
    }

    @Test
    fun `표본에 나타난 판정 불가 사유 전수를 싣는다`() {
        val aggregate =
            aggregateOf(
                listOf(BidStateTag.BID_RECORDED, BidStateTag.BID_RECORDED),
                listOf(
                    ReconciliationVerdict.Undecidable(ReconciliationBlocker.WINNER_NAME_NOT_PUBLISHED),
                    ReconciliationVerdict.Undecidable(ReconciliationBlocker.OBSERVED_NOT_PROMOTED),
                ),
            )
        aggregate.blockersObserved shouldBe
            setOf(
                ReconciliationBlocker.WINNER_NAME_NOT_PUBLISHED,
                ReconciliationBlocker.OBSERVED_NOT_PROMOTED,
            )
        aggregate.reconciled.rate shouldBe AwardRateState.Absent(AwardRateAbsence.NO_DECIDED_CASES)
    }

    // ------------------------------------------------------------------ 차이

    @Test
    fun `차이는 내 금액 빼기 확정 낙찰가다`() {
        awardGapOf(mine(1_200_000L), award(1_000_000L)) shouldBe AwardGap.Computed(200_000L, Currency.KRW)
    }

    @Test
    fun `내가 더 낮으면 차이가 음수다`() {
        awardGapOf(mine(900_000L), award(1_000_000L)) shouldBe AwardGap.Computed(-100_000L, Currency.KRW)
    }

    /** 확정 낙찰가가 없으면 **부재**다 — 0 으로도 잠정 금액으로도 채우지 않는다(D-7C5B-2). */
    @Test
    fun `확정 낙찰가가 없으면 차이는 부재다`() {
        awardGapOf(mine(1_200_000L), null) shouldBe AwardGap.AwardAbsent
    }

    /**
     * **통화 축 래칫** — [awardGapOf] 는 두 금액의 통화가 같다고 보고 뺀다. 그 전제가 서는
     * 근거는 어휘가 하나라는 것뿐이므로, 어휘가 늘면 여기가 먼저 붉어 그 자리를 가리킨다
     * (닿지 않는 불일치 갈래를 두는 대신 살아 있는 단언으로 지킨다).
     */
    @Test
    fun `통화 어휘가 하나라서 차이에 불일치 갈래가 없다`() {
        Currency.entries shouldBe listOf(Currency.KRW)
    }

    // ------------------------------------------------- 운영자 기록 ↔ 대사

    @Test
    fun `둘 다 확정이고 같으면 일치다`() {
        outcomeAgreementOf(BidStateTag.WON, ReconciliationVerdict.Won) shouldBe OutcomeAgreement.AGREE
        outcomeAgreementOf(BidStateTag.LOST, ReconciliationVerdict.Lost) shouldBe OutcomeAgreement.AGREE
        outcomeAgreementOf(BidStateTag.FAILED_OR_REBID, ReconciliationVerdict.FailedBid) shouldBe
            OutcomeAgreement.AGREE
        outcomeAgreementOf(BidStateTag.FAILED_OR_REBID, ReconciliationVerdict.Rebid) shouldBe OutcomeAgreement.AGREE
    }

    @Test
    fun `둘 다 확정인데 다르면 어긋남이다`() {
        outcomeAgreementOf(BidStateTag.WON, ReconciliationVerdict.Lost) shouldBe OutcomeAgreement.DISAGREE
        outcomeAgreementOf(BidStateTag.LOST, ReconciliationVerdict.Won) shouldBe OutcomeAgreement.DISAGREE
    }

    /** 한쪽이 확정이 아니면 견줄 수 없다 — 어긋남이 아니다(없는 어긋남을 만들지 않는다). */
    @Test
    fun `한쪽이 확정이 아니면 견줄 수 없다`() {
        outcomeAgreementOf(BidStateTag.BID_RECORDED, ReconciliationVerdict.Won) shouldBe
            OutcomeAgreement.NOT_COMPARABLE
        outcomeAgreementOf(
            BidStateTag.WON,
            ReconciliationVerdict.Undecidable(ReconciliationBlocker.NOT_COLLECTED),
        ) shouldBe OutcomeAgreement.NOT_COMPARABLE
    }
}
