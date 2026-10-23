package bidvector.bidding

import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.VatTreatment
import java.time.Instant
import java.time.LocalDate

/**
 * 전수 test 의 모집단 — **꼬리표마다 대표값 하나**다. 전이표 test 가 14 × 13 쌍을 돌려면 모든
 * 상태와 모든 명령의 인스턴스가 필요하고, 그 목록이 꼬리표 enum 과 **같은 집합**임을 각
 * test 가 먼저 단언한다(대표값을 빠뜨리면 전수가 조용히 줄어든다 — 그것이 전수 test 의
 * 가장 흔한 실패 모양이다).
 */
internal val T0: Instant = Instant.parse("2026-10-21T04:00:00Z")

internal val ALL_STATES: List<BidState> =
    listOf(
        BidState.Watching,
        BidState.Deferred(T0.plusSeconds(3600)),
        BidState.PriceReceived,
        BidState.BidPlanned,
        BidState.BidRecorded,
        BidState.AwaitingOpening(T0.plusSeconds(7200)),
        BidState.OpenedProvisionalFirst,
        BidState.OpenedOutOfRank,
        BidState.UnderQualificationReview(T0.plusSeconds(86_400)),
        BidState.Won,
        BidState.Lost,
        BidState.FailedOrRebid,
        BidState.Skipped(SkipReason("예산 초과")),
        BidState.DeadlinePassedUnrecorded,
    )

internal fun submittedAmount(origin: BidAmountOrigin = BidAmountOrigin.OperatorEntered) =
    SubmittedBidAmount(
        won = 1_234_000_000L,
        currency = Currency.KRW,
        vatTreatment = VatTreatment.INCLUSIVE,
        origin = origin,
    )

internal fun snapshot() =
    NoticeSnapshot(
        noticeRevisionNumber = 1L,
        takenAt = T0,
        deadlineAt = T0.plusSeconds(1800),
        floorRate = null,
        baseAmount = null,
    )

internal fun draft() = BidDraft(submittedAmount(), T0, snapshot())

internal fun submission(evidence: EvidenceRef? = null) =
    BidSubmission(
        amount = submittedAmount(),
        submittedAt = T0,
        drawNumbers = listOf(3, 11),
        evidence = evidence,
        memo = null,
    )

internal val ALL_COMMANDS: List<BidCommand> =
    listOf(
        BidCommand.Defer(T0.plusSeconds(3600)),
        BidCommand.Resume,
        BidCommand.ReceivePriceMenu(PriceMenuRef("menu-1")),
        BidCommand.DraftBid(draft()),
        BidCommand.RecordBid(submission(), null),
        BidCommand.AwaitOpening(T0.plusSeconds(7200)),
        BidCommand.RecordOpeningFirst,
        BidCommand.RecordOpeningOutOfRank,
        BidCommand.EnterQualificationReview(T0.plusSeconds(86_400)),
        BidCommand.RecordOutcome(BidOutcome.WON),
        BidCommand.CorrectOutcome(BidOutcome.LOST, TransitionReason("개찰 공고 정정")),
        BidCommand.Skip(SkipReason("면허 불일치")),
        BidCommand.MarkDeadlinePassed,
    )

internal fun case(
    state: BidState,
    kind: BidKind = BidKind.REAL,
) = BidCase(
    id = BidCaseId(1L),
    notice = NoticeKey("R00TEST0000001", NoticeRound.of("000")),
    kind = kind,
    state = state,
    caseVersion = 0,
    outcomeNoticeExpectedAt = null,
    deadlineAt = null,
)

internal val RETAIN_UNTIL: LocalDate = LocalDate.of(2029, 10, 21)
