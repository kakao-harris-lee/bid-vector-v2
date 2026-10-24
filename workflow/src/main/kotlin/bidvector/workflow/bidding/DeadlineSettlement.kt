package bidvector.workflow.bidding

import bidvector.bidding.ActorKind
import bidvector.bidding.BID_TRANSITIONS
import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommand
import bidvector.bidding.BidCommandTag
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.TransitionOutcome
import bidvector.bidding.applyCommand
import bidvector.bidding.tag
import bidvector.workflow.access.AccountId
import java.time.Instant

/**
 * **마감은 서버가 판정하고, 그 판정은 되돌릴 수 있다**(계약 D-7B-3 결정 ① · D-7B-4 결정 ⑤ ⑥, Q-11).
 *
 * 건을 읽거나 그 건에 명령이 오면 먼저 이 판정을 지난다. 두 방향이다.
 * - **세운다**: 유효 마감이 지났고 전이표가 시스템 주체의 `MARK_DEADLINE_PASSED` 를 그 상태에서 허락하면(마감 전 넷)
 *   적용한다. 그래서 마감 뒤의 「제출함」은 소급 기록이 된다.
 * - **거둔다**: 건이 「마감 지남」에 있고 **그 상태에 시스템 판정으로 들어왔는데**(타임라인 마지막 행이 시스템
 *   `MARK_DEADLINE_PASSED`) 유효 마감이 다시 미래이거나 미정이면, 시스템 주체로 `REVOKE_DEADLINE_PASSED` 를
 *   적용해 그 판정 직전 상태로 돌린다. 마감을 미룬 정정 공고 뒤에 건이 입찰 생애에서 영구히 빠지지 않게 한다.
 *   기록(`RECORD_BID`)으로 이미 그 상태를 떠난 건은 대상이 아니다(마지막 행이 판정이 아니다).
 *
 * 유효 마감은 **공고 행이 있으면 그 행의 값**이고(값이 없으면 미정 — 세우지 않는다), **행이 없을 때만**(6B-3 파기)
 * 담을 때 적은 사본이다. 파기된 공고는 다시 정정되지 않는다.
 *
 * 판정 전이는 요청의 성패와 무관한 **사실**이다 — 요청 하나가 판정 전이(트랜잭션 하나)와 요청 전이(트랜잭션 하나)를
 * 따로 쓴다. 판정 저장이 경합(`VersionConflict`)으로 지면 다시 읽어 **한 번 더** 판정한다 — 다른 전이가 이긴 뒤의
 * 상태로 다시 묻는 것이다(그 뒤에도 지면 다시 읽은 값을 그대로 쓴다).
 */
internal class DeadlineSettlement(
    private val cases: BidCaseStorePort,
    private val deadlines: NoticeDeadlineQueryPort,
) {
    /** 건을 읽고 판정한다 — 남의 건이면 `null`. 공고 마감 조회 결과를 함께 돌려준다(화면 계산이 다시 쓴다). */
    fun load(
        account: AccountId,
        caseId: BidCaseId,
        now: Instant,
    ): SettledCase? {
        val loaded = cases.load(account, caseId) ?: return null
        val noticeDeadline = deadlines.deadlines(setOf(loaded.notice))[loaded.notice]
        return SettledCase(settle(account, loaded, noticeDeadline, now), noticeDeadline)
    }

    fun settle(
        account: AccountId,
        case: BidCase,
        noticeDeadline: NoticeDeadline?,
        now: Instant,
    ): BidCase {
        val first = attempt(account, case, noticeDeadline, now)
        return if (first == TransitionWrite.VersionConflict) {
            retry(account, case, noticeDeadline, now)
        } else {
            reloadIfWritten(account, case, first)
        }
    }

    /** 경합으로 진 판정 — 다시 읽은 상태로 한 번 더 묻는다. */
    private fun retry(
        account: AccountId,
        case: BidCase,
        noticeDeadline: NoticeDeadline?,
        now: Instant,
    ): BidCase {
        val reloaded = cases.load(account, case.id) ?: return case
        return reloadIfWritten(account, reloaded, attempt(account, reloaded, noticeDeadline, now))
    }

    /** 판정 하나를 시도한다 — 쓸 것이 없으면 `null`. */
    private fun attempt(
        account: AccountId,
        case: BidCase,
        noticeDeadline: NoticeDeadline?,
        now: Instant,
    ): TransitionWrite? {
        val command = judgment(account, case, noticeDeadline, now) ?: return null
        val outcome = applyCommand(case.state, command, DEADLINE_ACTOR.kind, now)
        return (outcome as? TransitionOutcome.Accepted)?.let {
            writeTransition(cases, account, case, command, it, DEADLINE_ACTOR, now)
        }
    }

    private fun reloadIfWritten(
        account: AccountId,
        case: BidCase,
        write: TransitionWrite?,
    ): BidCase = if (write == null) case else cases.load(account, case.id) ?: case

    /**
     * 지금 무엇을 판정해야 하는가 — 세움 · 거둠 · 없음. 허락 여부는 전이표가 가린다(여기는 시각만 본다).
     *
     * 거두는 근거는 둘이다: 유효 마감이 **미래**이거나, 공고 **행이 있는데** 마감이 **미정**이다. 공고 행이 없고 사본도
     * 없으면 아무것도 모르는 것이라 거두지 않는다(판정을 세운 근거를 뒤집을 사실이 없다).
     */
    private fun judgment(
        account: AccountId,
        case: BidCase,
        noticeDeadline: NoticeDeadline?,
        now: Instant,
    ): BidCommand? {
        val deadline = effectiveDeadline(case, noticeDeadline)
        val passed = deadline != null && now.isAfter(deadline)
        val undetermined = noticeDeadline != null && noticeDeadline.at == null
        val future = deadline != null && !now.isAfter(deadline)
        return if (case.state.tag != BidStateTag.DEADLINE_PASSED_UNRECORDED) {
            if (passed) BidCommand.MarkDeadlinePassed else null
        } else {
            if (future || undetermined) revocation(account, case) else null
        }
    }

    /** 되돌림 — 그 상태에 시스템 판정으로 들어왔을 때만, 그 판정 직전 상태로. */
    private fun revocation(
        account: AccountId,
        case: BidCase,
    ): BidCommand? {
        val last = cases.timeline(account, case.id).lastOrNull() ?: return null
        val systemMark =
            last.actorKind == ActorKind.SYSTEM && last.command == BidCommandTag.MARK_DEADLINE_PASSED.name
        val from = last.fromState
        return if (systemMark && from != null) BidCommand.RevokeDeadlinePassed(restoreOf(from)) else null
    }
}

/**
 * 판정 직전 상태를 복원한다. payload 가 없는 셋은 그대로이고, **나중에(DEFERRED)** 는 리마인드 시각을 판정이 지웠으므로
 * (마감 지남은 그 값을 싣지 않는다) 관심(WATCHING)으로 돌린다 — 옛 리마인드는 마감이 연장된 뒤 대개 의미를 잃는다.
 * 그 밖의 꼬리표는 전이표가 거부한다(마감 전 넷만 도착 집합이다).
 */
private fun restoreOf(from: BidStateTag): BidState =
    when (from) {
        BidStateTag.PRICE_RECEIVED -> BidState.PriceReceived
        BidStateTag.BID_PLANNED -> BidState.BidPlanned
        else -> BidState.Watching
    }

/** 판정을 지난 건과 그때 읽은 공고 마감(키 부재 = 공고 행 없음). */
internal data class SettledCase(
    val case: BidCase,
    val noticeDeadline: NoticeDeadline?,
)

/**
 * 유효 마감 — 공고 행이 있으면 **그 행의 값**(없으면 미정 `null`)이고, 행이 없을 때만 담을 때 적은 **사본**이다
 * (계약 D-7B-4 결정 ⑥: 「값 없음」과 「행 없음」을 가른다).
 */
internal fun effectiveDeadline(
    case: BidCase,
    noticeDeadline: NoticeDeadline?,
): Instant? = if (noticeDeadline != null) noticeDeadline.at else case.deadlineAt

/**
 * 마감 판정을 **세우는** 상태 — 전이표에서 시스템 주체의 `MARK_DEADLINE_PASSED` 가 열린 출발 상태다(목록을 다시 적지
 * 않는다 — 표가 바뀌면 이 집합도 따라 바뀐다). 목록의 판정 후보 질의가 이 집합을 쓴다.
 */
internal val DEADLINE_JUDGED_STATES: Set<BidStateTag> =
    BID_TRANSITIONS
        .filter { (key, rule) -> key.command == BidCommandTag.MARK_DEADLINE_PASSED && ActorKind.SYSTEM in rule.actors }
        .keys
        .map { it.from }
        .toSet()

/** 마감 판정의 시스템 주체 — 타임라인의 `actor_detail` 로 남는 **경로 이름**이다. */
internal val DEADLINE_ACTOR: BidActor.System = BidActor.System("deadline-check")

/**
 * 받아들여진 전이 하나를 저장한다 — 사용자 명령과 마감 판정이 **같은 길**로 쓴다(저장 경로를 둘로 두면
 * 한쪽에만 소유 확인이나 잠금이 빠지는 자리가 생긴다).
 */
@Suppress("LongParameterList")
internal fun writeTransition(
    cases: BidCaseStorePort,
    account: AccountId,
    case: BidCase,
    command: BidCommand,
    accepted: TransitionOutcome.Accepted,
    actor: BidActor,
    now: Instant,
): TransitionWrite =
    cases.applyTransition(
        account,
        AppliedTransition(
            caseId = case.id,
            expectedVersion = case.caseVersion,
            command = command.tag,
            fromState = case.state,
            next = accepted.next,
            effect = accepted.effect,
            retroactive = accepted.retroactive,
            reason = accepted.reason,
            actorKind = actor.kind,
            actorDetail = (actor as? BidActor.System)?.detail,
            occurredAt = now,
        ),
    )
