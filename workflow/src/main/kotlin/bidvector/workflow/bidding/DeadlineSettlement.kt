package bidvector.workflow.bidding

import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommand
import bidvector.bidding.TransitionOutcome
import bidvector.bidding.applyCommand
import bidvector.bidding.tag
import bidvector.workflow.access.AccountId
import java.time.Instant

/**
 * **마감은 서버가 판정한다**(계약 D-7B-3 결정 ①, Q-11).
 *
 * 마감 전 상태의 건을 읽거나 그 건에 명령이 오면, 마감이 지났을 때 **시스템 주체**로
 * `MARK_DEADLINE_PASSED` 를 먼저 적용하고(타임라인에 시스템 주체로 남는다) 다시 읽는다. 그래서 마감 뒤의
 * 「제출함」은 소급 기록이 되고, 공고가 파기된 건도 담을 때 적은 마감 사본으로 판정된다. 사용자가 부르는
 * 「마감 지남」 operation 은 열지 않는다.
 *
 * 어느 상태가 「마감 전」인가는 여기서 적지 않는다 — **전이표가 시스템 주체의 그 명령을 허락하는가**가
 * 그 판정이다(관심 · 나중에 · 투찰가 받음 · 투찰 예정 넷). 이미 기록됐거나 종결된 건은 그대로다.
 * 경합으로 저장이 실패해도 다시 읽은 값을 쓴다 — 다른 전이가 이긴 것이다.
 */
internal class DeadlineSettlement(
    private val cases: BidCaseStorePort,
    private val deadlines: NoticeDeadlineQueryPort,
) {
    /** 건을 읽고 마감을 판정한다 — 남의 건이면 `null`. 공고의 지금 마감을 함께 돌려준다(화면 계산이 다시 쓴다). */
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
        noticeDeadline: Instant?,
        now: Instant,
    ): BidCase {
        val deadline = effectiveDeadline(case, noticeDeadline)
        val outcome =
            if (deadline != null && now.isAfter(deadline)) {
                applyCommand(case.state, BidCommand.MarkDeadlinePassed, DEADLINE_ACTOR.kind, now)
            } else {
                null
            }
        if (outcome !is TransitionOutcome.Accepted) return case
        writeTransition(cases, account, case, BidCommand.MarkDeadlinePassed, outcome, DEADLINE_ACTOR, now)
        return cases.load(account, case.id) ?: case
    }
}

/** 판정을 지난 건과 그때 읽은 공고 마감. */
internal data class SettledCase(
    val case: BidCase,
    val noticeDeadline: Instant?,
)

/** 공고의 지금 마감이 정본이고, 공고가 없으면(파기) 담을 때 적은 **사본**을 쓴다. */
internal fun effectiveDeadline(
    case: BidCase,
    noticeDeadline: Instant?,
): Instant? = noticeDeadline ?: case.deadlineAt

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
