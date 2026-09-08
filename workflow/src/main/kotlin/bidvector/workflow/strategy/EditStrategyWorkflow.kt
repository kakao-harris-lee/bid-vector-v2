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
 * 채널 독립 use case(scope.md ⑦, STR-11) — 4개 command 처리 + `begin`·`expire`. 어댑터
 * (Telegram/웹)는 이 클래스 밖에 산다(D-M4-1). 모든 전략 write 는 이 클래스를 지난다
 * (⑥) — [strategies]는 `public`(설계 검토 (2) #3 실측 판정, `Ports.kt` 참고,
 * `OPEN-4A-WRITE-PATH-GATE`), 우회 (3)의 차단은 타입 근거(`TransitionOutcome.Applied`만
 * 저장 인자를 낸다)에 의존한다.
 */
class EditStrategyWorkflow(
    private val sessions: EditSessionRepository,
    private val strategies: StrategyRepository,
    private val clock: Clock,
    private val events: EventSink,
    private val strategyPolicy: Resolution.Resolved<StrategyPolicyData>,
    private val sessionPolicy: EditSessionPolicyData,
) {
    fun begin(
        sessionId: EditSessionId,
        operator: OperatorId,
        field: EditableField,
    ): EditSession {
        val session = beginSession(sessionId, operator, field, clock.now(), sessionPolicy)
        sessions.save(session)
        return session
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

    private fun process(command: EditCommand): CommandResult {
        val session = sessions.load(command.sessionId) ?: return CommandResult.SessionNotFound
        val outcome = apply(session, command, clock.now(), strategies.load(), strategyPolicy)
        sessions.save(outcome.session)
        if (outcome is TransitionOutcome.Applied) {
            strategies.save(outcome.strategy)
            events.publish(outcome.event)
        }
        return CommandResult.Processed(outcome)
    }
}
