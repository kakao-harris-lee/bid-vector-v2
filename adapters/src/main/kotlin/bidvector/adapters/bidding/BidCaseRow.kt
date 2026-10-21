package bidvector.adapters.bidding

import bidvector.bidding.ActorKind
import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.PriceMenuRef
import bidvector.bidding.SkipReason
import bidvector.bidding.SubmittedBidAmount
import bidvector.sharedkernel.NoticeRound
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** 저장형 ↔ 도메인 변환. **도메인 모델과 DB record 를 같은 클래스로 재사용하지 않는다.** */
internal object BidCaseRow {
    /**
     * 상태는 **꼬리표 + 그 상태가 요구하는 열**로 복원한다. 열이 비어 있으면 그 행은 CHECK 를
     * 지나오지 못했어야 하므로, 복원 실패를 조용한 기본값으로 덮지 않고 예외로 낸다 —
     * 「리마인드 없는 나중에」가 읽기에서 되살아나는 길을 막는다.
     */
    fun toState(
        tag: BidStateTag,
        remindAt: Instant?,
        openingAt: Instant?,
        documentDueAt: Instant?,
        skipReason: String?,
    ): BidState =
        when (tag) {
            BidStateTag.WATCHING -> BidState.Watching
            BidStateTag.DEFERRED -> BidState.Deferred(required(remindAt, "remind_at"))
            BidStateTag.PRICE_RECEIVED -> BidState.PriceReceived
            BidStateTag.BID_PLANNED -> BidState.BidPlanned
            BidStateTag.BID_RECORDED -> BidState.BidRecorded
            BidStateTag.AWAITING_OPENING -> BidState.AwaitingOpening(required(openingAt, "opening_at"))
            BidStateTag.OPENED_PROVISIONAL_FIRST -> BidState.OpenedProvisionalFirst
            BidStateTag.OPENED_OUT_OF_RANK -> BidState.OpenedOutOfRank
            BidStateTag.UNDER_QUALIFICATION_REVIEW ->
                BidState.UnderQualificationReview(required(documentDueAt, "document_due_at"))

            BidStateTag.WON -> BidState.Won
            BidStateTag.LOST -> BidState.Lost
            BidStateTag.FAILED_OR_REBID -> BidState.FailedOrRebid
            BidStateTag.SKIPPED -> BidState.Skipped(SkipReason(required(skipReason, "skip_reason")))
            BidStateTag.DEADLINE_PASSED_UNRECORDED -> BidState.DeadlinePassedUnrecorded
        }

    /** 상태가 싣는 일정 축을 열로 편다 — 그 상태가 아니면 `null` 이다(잔재를 남기지 않는다). */
    fun remindAtOf(state: BidState): Instant? = (state as? BidState.Deferred)?.remindAt

    fun openingAtOf(state: BidState): Instant? = (state as? BidState.AwaitingOpening)?.openingAt

    fun documentDueAtOf(state: BidState): Instant? =
        (state as? BidState.UnderQualificationReview)?.documentDueAt

    fun skipReasonOf(state: BidState): String? = (state as? BidState.Skipped)?.reason?.value

    fun toCase(rs: ResultSet): BidCase {
        val tag = BidStateTag.valueOf(rs.getString("state"))
        return BidCase(
            id = BidCaseId(rs.getLong("id")),
            notice = NoticeKey(rs.getString("notice_number"), NoticeRound.of(rs.getString("notice_round"))),
            kind = BidKind.valueOf(rs.getString("bid_kind")),
            state =
                toState(
                    tag = tag,
                    remindAt = rs.instantOrNull("remind_at"),
                    openingAt = rs.instantOrNull("opening_at"),
                    documentDueAt = rs.instantOrNull("document_due_at"),
                    skipReason = rs.getString("skip_reason"),
                ),
            caseVersion = rs.getInt("case_version"),
            outcomeNoticeExpectedAt = rs.instantOrNull("outcome_notice_expected_at"),
        )
    }

    /** 금액 출처 ↔ 두 열. 짝이 깨진 행은 CHECK 를 지나오지 못한다. */
    fun originTokens(amount: SubmittedBidAmount): Pair<String, String?> =
        when (val origin = amount.origin) {
            BidAmountOrigin.OperatorEntered -> "OPERATOR_ENTERED" to null
            is BidAmountOrigin.PriceMenu -> "PRICE_MENU" to origin.chosenOption.value
        }

    fun toOrigin(
        provenance: String,
        chosenOptionRef: String?,
    ): BidAmountOrigin =
        when (provenance) {
            "OPERATOR_ENTERED" -> BidAmountOrigin.OperatorEntered
            "PRICE_MENU" -> BidAmountOrigin.PriceMenu(PriceMenuRef(required(chosenOptionRef, "chosen_option_ref")))
            else -> error("알 수 없는 금액 출처 어휘: $provenance")
        }

    fun actorKind(token: String): ActorKind = ActorKind.valueOf(token)

    private fun <T : Any> required(
        value: T?,
        column: String,
    ): T = requireNotNull(value) { "$column 이 비어 있다 — CHECK 를 지나오지 못한 행이다" }
}

internal fun ResultSet.instantOrNull(column: String): Instant? = getTimestamp(column)?.toInstant()

internal fun Instant?.toTimestampOrNull(): Timestamp? = this?.let(Timestamp::from)
