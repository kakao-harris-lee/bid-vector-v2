package bidvector.bidding

import java.time.Instant

/** 전이표의 키 — `(출발 상태, 명령)` 쌍. */
data class TransitionKey(
    val from: BidStateTag,
    val command: BidCommandTag,
)

/**
 * 표 한 행이 허락하는 것 — **도착 상태의 집합**과 **주체 종류의 집합**.
 *
 * 도착이 값 하나가 아니라 집합인 이유: `RECORD_OUTCOME` 하나가 낙찰 · 패찰 · 유찰 셋으로
 * 갈리고, 어느 쪽인지는 명령의 payload 가 정한다. 표는 **허락 범위**를 들고, payload 가 그
 * 안에서 고른다 — 범위 밖을 고르면 거부다. 셋을 명령 셋으로 쪼개 표를 「값 하나」로 만들 수도
 * 있었지만, 그러면 사용자에게 한 동작인 것이 어휘 셋이 된다.
 */
data class TransitionRule(
    val allowedTargets: Set<BidStateTag>,
    val actors: Set<ActorKind>,
)

/** 거부 사유 — 갈래를 나눈다(호출부가 401/409 를 가르고 타임라인에 남길 수 있게). */
sealed interface RejectionReason {
    /** 표에 그 `(상태, 명령)` 행이 없다 — **금지 전이의 기본 모양**이다. */
    data object InvalidTransition : RejectionReason

    /** 행은 있으나 이 주체 종류에게 열려 있지 않다. */
    data object ActorNotPermitted : RejectionReason

    /** 행은 있으나 payload 가 고른 도착이 허락 범위 밖이다. */
    data object TargetNotAllowed : RejectionReason
}

/** 전이가 남기는 부수 산출 — 초안 한 벌 또는 기록 한 벌, 아니면 없음. */
sealed interface TransitionEffect {
    data object None : TransitionEffect

    data class DraftWritten(
        val draft: BidDraft,
    ) : TransitionEffect

    data class RecordWritten(
        val record: BidRecordEntry,
    ) : TransitionEffect
}

/** [applyCommand] 의 결과. */
sealed interface TransitionOutcome {
    data class Rejected(
        val reason: RejectionReason,
    ) : TransitionOutcome

    data class Accepted(
        val next: BidState,
        val effect: TransitionEffect,
        /** 소급 확인(Q-11)인가 — **출발 상태가 정한다**(호출부가 주장할 수 없다). */
        val retroactive: Boolean,
        /** 타임라인에 남길 사유. 건너뜀 · 정정만 값을 갖는다. */
        val reason: TransitionReason?,
    ) : TransitionOutcome
}

private val USER_ONLY = setOf(ActorKind.USER)
private val USER_OR_SYSTEM = setOf(ActorKind.USER, ActorKind.SYSTEM)
private val SYSTEM_ONLY = setOf(ActorKind.SYSTEM)

/** 「담은 뒤 아직 투찰 예정이 아닌」 네 상태 — 건너뜀 · 마감 지남 · 초안이 공통으로 열린다. */
private val PRE_SUBMISSION =
    listOf(
        BidStateTag.WATCHING,
        BidStateTag.DEFERRED,
        BidStateTag.PRICE_RECEIVED,
        BidStateTag.BID_PLANNED,
    )

private val OUTCOME_TARGETS =
    setOf(BidStateTag.WON, BidStateTag.LOST, BidStateTag.FAILED_OR_REBID)

private fun rows(
    from: List<BidStateTag>,
    command: BidCommandTag,
    rule: TransitionRule,
): List<Pair<TransitionKey, TransitionRule>> = from.map { TransitionKey(it, command) to rule }

/**
 * **전이표 — 이 slice 의 정본.** 금지 전이는 「표에 없는 것」이라 구조로 거부된다
 * (`InvalidTransition`), 금지 목록을 따로 들지 않는다.
 *
 * 「보류」 상태가 없는 것과 마찬가지로, 여기 없는 `(상태, 명령)` 쌍 **182** 는 전부 거부다
 * (14 상태 × 13 명령 = 182 쌍 중 표가 든 것만 허용 — `BidTransitionTableTest` 가 전수로 잰다).
 *
 * **종단은 [BidStateTag.SKIPPED] 하나뿐이다.** `DEADLINE_PASSED_UNRECORDED` 는 여정의 종료
 * 가지이지만 Q-11(소급 무기한)이 `RECORD_BID` 한 간선을 남긴다. 낙찰 · 패찰 · 유찰 셋은
 * `CORRECT_OUTCOME` 으로 서로 오간다(§6.1 「사용자 정정」).
 *
 * **`SYSTEM` 행에 오늘 호출자가 없다**(SET-01 · SET-02 · DEC 메뉴는 범위 밖, 알려진 제한 ②).
 * 사용자 입력 경로는 열려 있다 — §6.1 이 *"「몇 위였나」는 사용자가 나라장터에서 보고 오거나
 * 입력해야 한다"* 라 적는다.
 */
val BID_TRANSITIONS: Map<TransitionKey, TransitionRule> =
    buildMap {
        // 나중에 ↔ 관심
        putAll(
            rows(
                listOf(BidStateTag.WATCHING, BidStateTag.PRICE_RECEIVED),
                BidCommandTag.DEFER,
                TransitionRule(setOf(BidStateTag.DEFERRED), USER_ONLY),
            ),
        )
        put(
            TransitionKey(BidStateTag.DEFERRED, BidCommandTag.RESUME),
            TransitionRule(setOf(BidStateTag.WATCHING), USER_OR_SYSTEM),
        )

        // 투찰가 메뉴 수령 — 시스템만(운영자가 「받았다」를 지어낼 수 없다)
        putAll(
            rows(
                listOf(BidStateTag.WATCHING, BidStateTag.DEFERRED),
                BidCommandTag.RECEIVE_PRICE_MENU,
                TransitionRule(setOf(BidStateTag.PRICE_RECEIVED), SYSTEM_ONLY),
            ),
        )

        // 초안 만들기 · 수정
        putAll(
            rows(
                PRE_SUBMISSION,
                BidCommandTag.DRAFT_BID,
                TransitionRule(setOf(BidStateTag.BID_PLANNED), USER_ONLY),
            ),
        )

        // 「제출함」 · 기록 갱신 · 소급 확인 — 셋이 한 명령이고 출발 상태가 가른다
        putAll(
            rows(
                listOf(
                    BidStateTag.BID_PLANNED,
                    BidStateTag.BID_RECORDED,
                    BidStateTag.DEADLINE_PASSED_UNRECORDED,
                ),
                BidCommandTag.RECORD_BID,
                TransitionRule(setOf(BidStateTag.BID_RECORDED), USER_ONLY),
            ),
        )

        put(
            TransitionKey(BidStateTag.BID_RECORDED, BidCommandTag.AWAIT_OPENING),
            TransitionRule(setOf(BidStateTag.AWAITING_OPENING), USER_OR_SYSTEM),
        )
        put(
            TransitionKey(BidStateTag.AWAITING_OPENING, BidCommandTag.RECORD_OPENING_FIRST),
            TransitionRule(setOf(BidStateTag.OPENED_PROVISIONAL_FIRST), USER_OR_SYSTEM),
        )
        put(
            TransitionKey(BidStateTag.AWAITING_OPENING, BidCommandTag.RECORD_OPENING_OUT_OF_RANK),
            TransitionRule(setOf(BidStateTag.OPENED_OUT_OF_RANK), USER_OR_SYSTEM),
        )
        put(
            TransitionKey(
                BidStateTag.OPENED_PROVISIONAL_FIRST,
                BidCommandTag.ENTER_QUALIFICATION_REVIEW,
            ),
            TransitionRule(setOf(BidStateTag.UNDER_QUALIFICATION_REVIEW), USER_ONLY),
        )

        // 결과 기록 — 셋 중 하나를 payload 가 고른다
        putAll(
            rows(
                listOf(
                    BidStateTag.OPENED_PROVISIONAL_FIRST,
                    BidStateTag.OPENED_OUT_OF_RANK,
                    BidStateTag.UNDER_QUALIFICATION_REVIEW,
                ),
                BidCommandTag.RECORD_OUTCOME,
                TransitionRule(OUTCOME_TARGETS, USER_OR_SYSTEM),
            ),
        )
        // 결과 정정 — 사용자만, 사유 필수(타입이 진다)
        putAll(
            rows(
                OUTCOME_TARGETS.toList(),
                BidCommandTag.CORRECT_OUTCOME,
                TransitionRule(OUTCOME_TARGETS, USER_ONLY),
            ),
        )

        // 종료 가지 둘
        putAll(
            rows(
                PRE_SUBMISSION,
                BidCommandTag.SKIP,
                TransitionRule(setOf(BidStateTag.SKIPPED), USER_ONLY),
            ),
        )
        putAll(
            rows(
                PRE_SUBMISSION,
                BidCommandTag.MARK_DEADLINE_PASSED,
                TransitionRule(setOf(BidStateTag.DEADLINE_PASSED_UNRECORDED), USER_OR_SYSTEM),
            ),
        )
    }

/** payload 가 고르는 도착 상태 — `when` 전수라 명령을 더하면 답 없이는 컴파일되지 않는다. */
private fun targetOf(
    command: BidCommand,
    now: Instant,
    retroactive: Boolean,
): Pair<BidState, TransitionEffect> =
    when (command) {
        is BidCommand.Defer -> BidState.Deferred(command.remindAt) to TransitionEffect.None
        BidCommand.Resume -> BidState.Watching to TransitionEffect.None
        is BidCommand.ReceivePriceMenu -> BidState.PriceReceived to TransitionEffect.None
        is BidCommand.DraftBid -> BidState.BidPlanned to TransitionEffect.DraftWritten(command.draft)
        is BidCommand.RecordBid ->
            BidState.BidRecorded to
                TransitionEffect.RecordWritten(
                    BidRecordEntry(
                        submission = command.submission,
                        confirmedAt = now,
                        retroactive = retroactive,
                        evidenceRetainUntil = command.evidenceRetainUntil,
                    ),
                )

        is BidCommand.AwaitOpening -> BidState.AwaitingOpening(command.openingAt) to TransitionEffect.None
        BidCommand.RecordOpeningFirst -> BidState.OpenedProvisionalFirst to TransitionEffect.None
        BidCommand.RecordOpeningOutOfRank -> BidState.OpenedOutOfRank to TransitionEffect.None
        is BidCommand.EnterQualificationReview ->
            BidState.UnderQualificationReview(command.documentDueAt) to TransitionEffect.None

        is BidCommand.RecordOutcome -> command.outcome.toState() to TransitionEffect.None
        is BidCommand.CorrectOutcome -> command.outcome.toState() to TransitionEffect.None
        is BidCommand.Skip -> BidState.Skipped(command.reason) to TransitionEffect.None
        BidCommand.MarkDeadlinePassed -> BidState.DeadlinePassedUnrecorded to TransitionEffect.None
    }

/** 타임라인에 남길 사유 — 건너뜀 · 정정만 값을 갖는다(나머지는 사유 열이 비는 것이 정상이다). */
private fun reasonOf(command: BidCommand): TransitionReason? =
    when (command) {
        is BidCommand.Skip -> TransitionReason(command.reason.value)
        is BidCommand.CorrectOutcome -> command.reason
        else -> null
    }

/**
 * **건의 유일한 전이 문.** 판정 순서는 ① 표에 행이 있는가 ② 주체 종류가 허락되는가
 * ③ payload 가 고른 도착이 허락 범위 안인가 — 셋 다 표의 값만 본다(중첩 분기 0).
 *
 * [now] 는 확인 시각이다(호출부의 시계 — 전역 시계를 부르지 않는다, §4.7 주입).
 *
 * **소급 판정은 여기서만 일어난다**: 출발이 `DEADLINE_PASSED_UNRECORDED` 인 `RECORD_BID` 가
 * 소급이다. 명령에 그 자리가 없으므로 호출부가 뒤집을 수 없다(Q-11 「소급 표시만」).
 */
fun applyCommand(
    current: BidState,
    command: BidCommand,
    actor: ActorKind,
    now: Instant,
): TransitionOutcome {
    val rule =
        BID_TRANSITIONS[TransitionKey(current.tag, command.tag)]
            ?: return TransitionOutcome.Rejected(RejectionReason.InvalidTransition)
    if (actor !in rule.actors) {
        return TransitionOutcome.Rejected(RejectionReason.ActorNotPermitted)
    }
    val retroactive =
        current.tag == BidStateTag.DEADLINE_PASSED_UNRECORDED &&
            command.tag == BidCommandTag.RECORD_BID
    val (next, effect) = targetOf(command, now, retroactive)
    if (next.tag !in rule.allowedTargets) {
        return TransitionOutcome.Rejected(RejectionReason.TargetNotAllowed)
    }
    return TransitionOutcome.Accepted(next, effect, retroactive, reasonOf(command))
}

/** 나가는 간선이 0 인 상태 — 표에서 **도출한다**(별도 목록을 두면 표와 갈린다). */
fun isTerminal(state: BidStateTag): Boolean =
    BID_TRANSITIONS.keys.none { it.from == state }
