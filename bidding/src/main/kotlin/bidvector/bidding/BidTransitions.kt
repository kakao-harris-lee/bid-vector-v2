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
    targets: Set<BidStateTag>,
    actors: Set<ActorKind>,
): Map<TransitionKey, TransitionRule> = from.associate { TransitionKey(it, command) to TransitionRule(targets, actors) }

/** 나중에 ↔ 관심. 되돌리기는 리마인드 도달(시스템)로도 일어날 수 있다. */
private val DEFER_ROWS =
    rows(
        listOf(BidStateTag.WATCHING, BidStateTag.PRICE_RECEIVED),
        BidCommandTag.DEFER,
        setOf(BidStateTag.DEFERRED),
        USER_ONLY,
    ) +
        rows(
            listOf(BidStateTag.DEFERRED),
            BidCommandTag.RESUME,
            setOf(BidStateTag.WATCHING),
            USER_OR_SYSTEM,
        )

/** 투찰가 메뉴 수령 — **시스템만**(운영자가 「받았다」를 지어낼 수 없다). 오늘 호출자는 없다. */
private val PRICE_MENU_ROWS =
    rows(
        listOf(BidStateTag.WATCHING, BidStateTag.DEFERRED),
        BidCommandTag.RECEIVE_PRICE_MENU,
        setOf(BidStateTag.PRICE_RECEIVED),
        SYSTEM_ONLY,
    )

/** 초안 만들기·수정 — 수정도 같은 명령이고 출발 상태가 `BID_PLANNED` 인 것이 다를 뿐이다. */
private val DRAFT_ROWS =
    rows(PRE_SUBMISSION, BidCommandTag.DRAFT_BID, setOf(BidStateTag.BID_PLANNED), USER_ONLY)

/** 「제출함」·기록 갱신·소급 확인 — 셋이 한 명령이고 **출발 상태가 가른다**. */
private val RECORD_ROWS =
    rows(
        listOf(
            BidStateTag.BID_PLANNED,
            BidStateTag.BID_RECORDED,
            BidStateTag.DEADLINE_PASSED_UNRECORDED,
        ),
        BidCommandTag.RECORD_BID,
        setOf(BidStateTag.BID_RECORDED),
        USER_ONLY,
    )

/** 개찰 축 — 오늘은 운영자가 입력하고, SET-02 가 서면 같은 행을 시스템이 쓴다. */
private val OPENING_ROWS =
    rows(
        listOf(BidStateTag.BID_RECORDED),
        BidCommandTag.AWAIT_OPENING,
        setOf(BidStateTag.AWAITING_OPENING),
        USER_OR_SYSTEM,
    ) +
        rows(
            listOf(BidStateTag.AWAITING_OPENING),
            BidCommandTag.RECORD_OPENING_FIRST,
            setOf(BidStateTag.OPENED_PROVISIONAL_FIRST),
            USER_OR_SYSTEM,
        ) +
        rows(
            listOf(BidStateTag.AWAITING_OPENING),
            BidCommandTag.RECORD_OPENING_OUT_OF_RANK,
            setOf(BidStateTag.OPENED_OUT_OF_RANK),
            USER_OR_SYSTEM,
        ) +
        rows(
            listOf(BidStateTag.OPENED_PROVISIONAL_FIRST),
            BidCommandTag.ENTER_QUALIFICATION_REVIEW,
            setOf(BidStateTag.UNDER_QUALIFICATION_REVIEW),
            USER_ONLY,
        )

/** 결과 기록과 **정정**(§6.1 「사용자 정정」) — 정정은 사용자만이고 사유가 필수다. */
private val OUTCOME_ROWS =
    rows(
        listOf(
            BidStateTag.OPENED_PROVISIONAL_FIRST,
            BidStateTag.OPENED_OUT_OF_RANK,
            BidStateTag.UNDER_QUALIFICATION_REVIEW,
        ),
        BidCommandTag.RECORD_OUTCOME,
        OUTCOME_TARGETS,
        USER_OR_SYSTEM,
    ) +
        rows(
            OUTCOME_TARGETS.toList(),
            BidCommandTag.CORRECT_OUTCOME,
            OUTCOME_TARGETS,
            USER_ONLY,
        )

/**
 * 종료 가지 둘 — 건너뜀(종단)과 마감 지남(소급 입구 하나가 남는다). 마감 지남에는 **시스템 되돌림** 한 간선이 더
 * 있다(계약 D-7B-4 결정 ⑤): 마감을 미룬 정정 공고 뒤에 시스템 판정을 거두는 길이고, 도착은 마감 전 넷이다.
 *
 * 마감 지남에 **들어가는** 간선은 시스템 전용이다(D-7B-5 C3-L6): §6.1 은 「확인 없이 마감이 지나면 『마감 지남(미확인)』이
 * 되고」라 적을 뿐 사용자가 그 상태를 적는 길을 요구하지 않는다. 사용자가 적을 수 있으면 그 판정은 되돌림의 근거(마지막 행이
 * 시스템 판정)를 갖지 못해 영구히 남는다.
 */
private val EXIT_ROWS =
    rows(PRE_SUBMISSION, BidCommandTag.SKIP, setOf(BidStateTag.SKIPPED), USER_ONLY) +
        rows(
            PRE_SUBMISSION,
            BidCommandTag.MARK_DEADLINE_PASSED,
            setOf(BidStateTag.DEADLINE_PASSED_UNRECORDED),
            SYSTEM_ONLY,
        ) +
        rows(
            listOf(BidStateTag.DEADLINE_PASSED_UNRECORDED),
            BidCommandTag.REVOKE_DEADLINE_PASSED,
            PRE_SUBMISSION.toSet(),
            SYSTEM_ONLY,
        )

/**
 * **전이표 — 이 slice 의 정본.** 금지 전이는 「표에 없는 것」이라 구조로 거부된다
 * (`InvalidTransition`), 금지 목록을 따로 들지 않는다.
 *
 * 묶음을 `+` 로 잇는다 — `buildMap {}` 람다 하나로 몰면 그 람다가 **함수 50줄 한도**에 걸린다
 * (`CleanMigrationColumnTest` 가 같은 이유로 테이블별 리스트를 `+` 로 잇는다).
 *
 * **종단은 [BidStateTag.SKIPPED] 하나뿐이다.** `DEADLINE_PASSED_UNRECORDED` 는 여정의 종료
 * 가지이지만 Q-11(소급 무기한)이 `RECORD_BID` 한 간선을 남기고, 시스템 되돌림(`REVOKE_DEADLINE_PASSED`)이
 * 마감 전 넷으로 돌아가는 간선을 하나 더 둔다. 낙찰·패찰·유찰 셋은
 * `CORRECT_OUTCOME` 으로 서로 오간다.
 *
 * **`SYSTEM` 행의 오늘 호출자는 `DeadlineSettlement` 하나이고, 그 자리가 쓰는 행은 둘이다**(`MARK_DEADLINE_PASSED` ·
 * `REVOKE_DEADLINE_PASSED` — R5-4).
 * 나머지(SET-01·SET-02·DEC 메뉴)는 범위 밖이다(알려진 제한 「자동 전이의 시스템 호출자는 마감 판정 하나뿐이다」 — R4-7).
 * 사용자 입력 경로는 열려 있다 — §6.1 이 *"「몇 위였나」는 사용자가 나라장터에서 보고 오거나
 * 입력해야 한다"* 라 적는다.
 *
 * 이 표가 §6.1 과 같은지는 `BidTransitionTableTest` 의 **골든 목록**이 집합 등식으로 잰다 —
 * 표에서 도출한 전수만으로는 표의 변화를 잡지 못한다(변이 ③ 실측).
 */
val BID_TRANSITIONS: Map<TransitionKey, TransitionRule> =
    DEFER_ROWS + PRICE_MENU_ROWS + DRAFT_ROWS + RECORD_ROWS + OPENING_ROWS + OUTCOME_ROWS + EXIT_ROWS

/** 부수 산출이 없는 전이 — 상태만 바뀌고 초안·기록은 쓰이지 않는다. */
private fun BidState.alone(): Pair<BidState, TransitionEffect> = this to TransitionEffect.None

/**
 * payload 가 **없는** 명령의 도착 — 룩업표다(§4.5 2: 값 기반 분기는 표로, `BidCaseRow.PAYLOAD_FREE` 와 같은 모양).
 * 아래 [targetOf] 의 `when` 이 payload 를 읽는 명령만 다루게 해 분기를 줄인다. 표와 `when` 이 함께 덮어야 하는 것은
 * **전이표에 행이 있는 명령에 한해서**다 — 행이 없는 명령은 전이표가 먼저 거부해 [targetOf] 에 닿지 않는다(D-7B-5 C3-L5).
 * 그 덮음은 `BidTransitionTableTest` 「표에 있는 쌍은 … 허용된다」가 명령 전수(`ALL_COMMANDS`)로 잰다.
 */
private val PAYLOAD_FREE_TARGETS: Map<BidCommand, BidState> =
    mapOf(
        BidCommand.Resume to BidState.Watching,
        BidCommand.RecordOpeningFirst to BidState.OpenedProvisionalFirst,
        BidCommand.RecordOpeningOutOfRank to BidState.OpenedOutOfRank,
        BidCommand.MarkDeadlinePassed to BidState.DeadlinePassedUnrecorded,
    )

/** payload 가 고르는 도착 상태 — payload 없는 넷은 [PAYLOAD_FREE_TARGETS] 가 든다. */
private fun targetOf(
    command: BidCommand,
    now: Instant,
    retroactive: Boolean,
): Pair<BidState, TransitionEffect> =
    when (command) {
        is BidCommand.Defer -> BidState.Deferred(command.remindAt).alone()
        is BidCommand.ReceivePriceMenu -> BidState.PriceReceived.alone()
        is BidCommand.DraftBid -> BidState.BidPlanned to TransitionEffect.DraftWritten(command.draft)
        is BidCommand.RecordBid -> recordTarget(command, now, retroactive)
        is BidCommand.AwaitOpening -> BidState.AwaitingOpening(command.openingAt).alone()
        is BidCommand.EnterQualificationReview -> BidState.UnderQualificationReview(command.documentDueAt).alone()
        is BidCommand.RecordOutcome -> command.outcome.toState().alone()
        is BidCommand.CorrectOutcome -> command.outcome.toState().alone()
        is BidCommand.Skip -> BidState.Skipped(command.reason).alone()
        is BidCommand.RevokeDeadlinePassed -> command.restore.alone()
        else -> requireNotNull(PAYLOAD_FREE_TARGETS[command]) { "도착이 정의되지 않은 명령이다: ${command.tag}" }.alone()
    }

/** 기록 전이의 도착과 산출 — 서버가 정하는 둘(확인 시각 · 소급)을 여기서 싣는다. */
private fun recordTarget(
    command: BidCommand.RecordBid,
    now: Instant,
    retroactive: Boolean,
): Pair<BidState, TransitionEffect> =
    BidState.BidRecorded to
        TransitionEffect.RecordWritten(
            BidRecordEntry(
                submission = command.submission,
                confirmedAt = now,
                retroactive = retroactive,
                evidenceRetainUntil = command.evidenceRetainUntil,
            ),
        )

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
    val retroactive = isRetroactive(current, command)
    // payload 가 고르는 도착은 **순수 계산**이라 주체 판정보다 먼저 구해도 부수효과가 없다.
    val (next, effect) = targetOf(command, now, retroactive)
    return actorRejection(rule, actor)
        ?: targetRejection(rule, next)
        ?: TransitionOutcome.Accepted(next, effect, retroactive, reasonOf(command))
}

/** 소급 판정 — 출발이 「마감 지남(미확인)」인 기록만 소급이다(Q-11). */
private fun isRetroactive(
    current: BidState,
    command: BidCommand,
): Boolean =
    current.tag == BidStateTag.DEADLINE_PASSED_UNRECORDED &&
        command.tag == BidCommandTag.RECORD_BID

private fun actorRejection(
    rule: TransitionRule,
    actor: ActorKind,
): TransitionOutcome.Rejected? =
    if (actor in rule.actors) null else TransitionOutcome.Rejected(RejectionReason.ActorNotPermitted)

private fun targetRejection(
    rule: TransitionRule,
    next: BidState,
): TransitionOutcome.Rejected? =
    if (next.tag in rule.allowedTargets) null else TransitionOutcome.Rejected(RejectionReason.TargetNotAllowed)

/** 나가는 간선이 0 인 상태 — 표에서 **도출한다**(별도 목록을 두면 표와 갈린다). */
fun isTerminal(state: BidStateTag): Boolean = BID_TRANSITIONS.keys.none { it.from == state }
