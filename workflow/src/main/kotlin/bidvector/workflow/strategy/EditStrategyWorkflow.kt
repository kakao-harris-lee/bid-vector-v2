package bidvector.workflow.strategy

import bidvector.sharedkernel.Resolution
import bidvector.strategy.StrategyPolicyData

/**
 * [EditStrategyWorkflow]의 command 처리 결과 — 세션 부재는 [TransitionOutcome] 밖의
 * 사유다(세션 객체가 없어 그 타입에 담을 수 없다).
 */
sealed interface CommandResult {
    data class Processed(
        val outcome: TransitionOutcome,
    ) : CommandResult

    data object SessionNotFound : CommandResult
}

/**
 * [EditStrategyWorkflow.begin]의 결과(verifier N-1 수정) — `apply()`의 판정 순서 밖이라
 * [TransitionOutcome] 을 재사용하지 않는다(그 타입은 [EditCommand] 를 요구하는데 `begin`
 * 에는 command 가 없다).
 */
sealed interface BeginOutcome {
    data class Started(
        val session: EditSession,
    ) : BeginOutcome

    /** 같은 [EditSessionId]에 이미 비종단 세션이 있어 거부됐다 — [existing]은 그 세션 그대로다. */
    data class Rejected(
        val existing: EditSession,
        val reason: RejectionReason,
    ) : BeginOutcome
}

/**
 * 채널 독립 use case(scope.md ⑦, STR-11) — 4개 command 처리 + `begin`·`expire`. 어댑터
 * (Telegram/웹)는 이 클래스 밖에 산다(D-M4-1). 모든 전략 write 는 이 클래스를 지난다(⑥) —
 * [strategies]는 `public`(설계 검토 (2) #3 실측 판정, `Ports.kt` 참고)이지만, 우회 (3)의
 * 차단은 [AppliedStrategy](`internal constructor`, `workflow` 밖에서 생성 불가)라는
 * **인자 타입** 근거에 의존한다 — `OperatorStrategy` 자체는 `validate()`가 public 이라
 * 누구나 얻을 수 있어 그 타입 근거는 서지 않았었다(verifier H-1 실측, `Ports.kt` KDoc 참고).
 */
class EditStrategyWorkflow(
    private val sessions: EditSessionRepository,
    private val strategies: StrategyRepository,
    private val clock: Clock,
    private val events: EventSink,
    private val strategyPolicy: Resolution.Resolved<StrategyPolicyData>,
    private val sessionPolicy: EditSessionPolicyData,
) {
    /**
     * 같은 [EditSessionId]에 이미 비종단 세션이 있으면 덮어쓰지 않고 거부한다(verifier N-1 —
     * `sessions.load` 가드가 없으면 `WaitingForConfirmation` 을 `apply()` 밖에서 조용히
     * `WaitingForValue` 로 되돌릴 수 있었다). 종단 세션(`Applied`/`Cancelled`/`Expired`)이
     * 있는 id 는 새로 열 수 있다(D-4A-4 — 재개는 새 세션).
     */
    fun begin(
        sessionId: EditSessionId,
        operator: OperatorId,
        field: EditableField,
    ): BeginOutcome {
        val existing = sessions.load(sessionId)
        if (existing != null && !isTerminal(existing.state)) {
            return BeginOutcome.Rejected(existing, RejectionReason.SessionAlreadyActive)
        }
        val session = beginSession(sessionId, operator, field, clock.now(), sessionPolicy)
        sessions.save(session)
        return BeginOutcome.Started(session)
    }

    fun provideValue(command: EditCommand.ProvideValue): CommandResult = process(command)

    fun confirm(command: EditCommand.Confirm): CommandResult = process(command)

    fun requestEdit(command: EditCommand.RequestEdit): CommandResult = process(command)

    fun cancel(command: EditCommand.Cancel): CommandResult = process(command)

    /** 주기 sweep 배선은 이 slice 밖(트리거는 4B/후속) — 순수 만료 판정만 여기서 노출한다. */
    fun expire(sessionId: EditSessionId): EditSessionState? {
        val session = sessions.load(sessionId) ?: return null
        val expired = expireIfDue(session, clock.now())
        if (expired !== session) sessions.save(expired)
        return expired.state
    }

    /**
     * `Applied` 는 `strategies.save` → `events.publish` → `sessions.save` 순이다(verifier
     * M-3 수정) — 세션을 먼저 저장하면 전략 저장 실패 뒤에도 세션이 이미 `Applied` 로
     * 굳어, 복구 뒤 같은 `Confirm` 재전달이 `Accepted`(중복)로 조용히 통과하며 전략은
     * revision 이 오르지 않고 발행도 없이 **편집이 영구 소실**됐다. 이 순서라면 전략 저장
     * 실패 시 세션이 전진하지 않아 재전달이 정상 재시도가 되고, 발행 실패(전략 저장은
     * 성공한 뒤)는 다음 재전달의 `seenRevision` 대조가 잡아 `StaleRevision` 거부로 정직하게
     * 드러난다(이중 적용이 아니다). 남는 잔여 창(발행 실패 뒤 세션 미전진)은 알려진
     * 제한 — 원자적 저장+발행+세션전진은 4C 트랜잭션 outbox 소관.
     */
    private fun process(command: EditCommand): CommandResult {
        val session = sessions.load(command.sessionId) ?: return CommandResult.SessionNotFound
        val outcome = apply(session, command, clock.now(), strategies.load(), strategyPolicy)
        if (outcome is TransitionOutcome.Applied) {
            strategies.save(outcome.applied)
            events.publish(outcome.event)
        }
        sessions.save(outcome.session)
        return CommandResult.Processed(outcome)
    }
}
