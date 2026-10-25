package bidvector.bidding

import java.time.Instant

/**
 * 입찰 건의 상태(`ux-journey-research.md` §6.1 어휘 그대로) — **「보류」는 없다.**
 * 「나중에(리마인드 시각)」와 「건너뜀(사유)」로 갈린다.
 *
 * **「후보(제안됨/검색됨)」는 이 집합에 없다** — pull 모델(§0.7 「내 입찰함은 사용자가 담은
 * 것만 담는다」)에서 건은 사용자가 담는 순간 태어난다. 그래서 첫 상태가 [Watching] 이다.
 *
 * **필수 인자를 상태가 싣는다.** 「건너뜀 ⇒ 사유」·「나중에 ⇒ 리마인드 시각」·「개찰 대기 ⇒
 * 개찰일시」·「적격심사 중 ⇒ 서류 기한」이 CHECK 이전에 **타입으로** 성립한다 — 빠진 조합을
 * 만들 수 없다. DB CHECK(`V21`)는 그 같은 불변식의 둘째 겹이다.
 */
sealed interface BidState {
    /** 관심 — 입찰함에 담았다. */
    data object Watching : BidState

    /** 나중에 — 리마인드 시각이 **반드시** 있다. */
    data class Deferred(
        val remindAt: Instant,
    ) : BidState

    /**
     * 투찰가 받음 — 메뉴(STR-16 · DEC-05)를 받아 둔 상태.
     * **오늘 도달 불가**(진입 명령의 호출자가 출하 경로에 없다, 알려진 제한 ①).
     */
    data object PriceReceived : BidState

    /** 투찰 예정 — 초안이 있다(고른 안 · 금액 · 제시 시각 · 공고 스냅샷). */
    data object BidPlanned : BidState

    /** 투찰 기록됨 — 개찰 대사(SET-02)의 **후보가 되는 유일한 상태**. */
    data object BidRecorded : BidState

    /** 개찰 대기 — 개찰일시가 **반드시** 있다. */
    data class AwaitingOpening(
        val openingAt: Instant,
    ) : BidState

    /** 개찰됨 · 잠정 1위. */
    data object OpenedProvisionalFirst : BidState

    /** 개찰됨 · 순위 밖. */
    data object OpenedOutOfRank : BidState

    /** 적격심사 중 — 서류 기한이 **반드시** 있다. */
    data class UnderQualificationReview(
        val documentDueAt: Instant,
    ) : BidState

    /** 낙찰 — 정정(`CorrectOutcome`)만 나간다. */
    data object Won : BidState

    /** 패찰 — 정정만 나간다. */
    data object Lost : BidState

    /** 유찰 · 재공고 — 정정만 나간다(재공고는 새 공고라 새 건이 된다). */
    data object FailedOrRebid : BidState

    /** 건너뜀 — 사유가 **반드시** 있다. 나가는 간선이 0 인 **유일한** 상태다. */
    data class Skipped(
        val reason: SkipReason,
    ) : BidState

    /**
     * 마감 지남(미확인) — 여정의 종료 가지이지만 **전이표의 종단이 아니다.**
     * Q-11(소급 확인 **무기한**)이 나가는 간선 하나(`RECORD_BID`)를 남긴다. 그 간선으로 간
     * 기록에는 「소급」 표시가 붙고, 그 표시는 **서버가 출발 상태로 판정**한다(클라이언트가
     * 주장할 수 없다 — `BidTransitions.apply`).
     */
    data object DeadlinePassedUnrecorded : BidState
}

/**
 * 상태의 표 키. 전이표가 `(상태, 명령)` 쌍으로 색인되므로 payload 를 벗긴 꼬리표가 필요하다 —
 * 어휘는 `V21` 의 `bid_case.state` CHECK 와 **같은 열 네 개**다(`'ON_HOLD'`(보류)는 없다).
 */
enum class BidStateTag {
    WATCHING,
    DEFERRED,
    PRICE_RECEIVED,
    BID_PLANNED,
    BID_RECORDED,
    AWAITING_OPENING,
    OPENED_PROVISIONAL_FIRST,
    OPENED_OUT_OF_RANK,
    UNDER_QUALIFICATION_REVIEW,
    WON,
    LOST,
    FAILED_OR_REBID,
    SKIPPED,
    DEADLINE_PASSED_UNRECORDED,
}

/** `when` 전수 — 상태를 더하면 답을 주지 않고는 컴파일되지 않는다. */
val BidState.tag: BidStateTag
    get() =
        when (this) {
            BidState.Watching -> BidStateTag.WATCHING
            is BidState.Deferred -> BidStateTag.DEFERRED
            BidState.PriceReceived -> BidStateTag.PRICE_RECEIVED
            BidState.BidPlanned -> BidStateTag.BID_PLANNED
            BidState.BidRecorded -> BidStateTag.BID_RECORDED
            is BidState.AwaitingOpening -> BidStateTag.AWAITING_OPENING
            BidState.OpenedProvisionalFirst -> BidStateTag.OPENED_PROVISIONAL_FIRST
            BidState.OpenedOutOfRank -> BidStateTag.OPENED_OUT_OF_RANK
            is BidState.UnderQualificationReview -> BidStateTag.UNDER_QUALIFICATION_REVIEW
            BidState.Won -> BidStateTag.WON
            BidState.Lost -> BidStateTag.LOST
            BidState.FailedOrRebid -> BidStateTag.FAILED_OR_REBID
            is BidState.Skipped -> BidStateTag.SKIPPED
            BidState.DeadlinePassedUnrecorded -> BidStateTag.DEADLINE_PASSED_UNRECORDED
        }

/**
 * 개찰 뒤의 결과 — `RECORD_OUTCOME` · `CORRECT_OUTCOME` 이 고르는 세 갈래다.
 * 셋을 따로 명령으로 쪼개지 않는 이유: 사용자에게는 「결과를 적는다」 한 동작이고,
 * 전이표는 허용 **집합**으로 셋을 함께 연다.
 */
enum class BidOutcome {
    WON,
    LOST,
    FAILED_OR_REBID,
}

/** 결과 → 상태. 세 값뿐이라 표가 아니라 `when` 전수로 충분하다. */
fun BidOutcome.toState(): BidState =
    when (this) {
        BidOutcome.WON -> BidState.Won
        BidOutcome.LOST -> BidState.Lost
        BidOutcome.FAILED_OR_REBID -> BidState.FailedOrRebid
    }
