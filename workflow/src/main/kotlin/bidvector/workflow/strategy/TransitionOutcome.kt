package bidvector.workflow.strategy

import bidvector.strategy.StrategyEvent

/**
 * [apply]의 결과(scope.md ④) — [AppliedStrategy](저장 인자로 쓸 수 있는 통로 타입)를 내는
 * 갈래는 [Applied] 하나뿐이다. `AppliedStrategy` 자체가 `internal constructor`라 이 값을
 * `apply()` 밖에서 만드는 것 자체가 컴파일되지 않는다(우회 (3) 차단, verifier H-1/H-2 수정).
 */
sealed interface TransitionOutcome {
    val session: EditSession

    data class Rejected(
        override val session: EditSession,
        val command: EditCommand,
        val reason: RejectionReason,
    ) : TransitionOutcome

    data class Accepted(
        override val session: EditSession,
    ) : TransitionOutcome

    data class Applied(
        override val session: EditSession,
        val applied: AppliedStrategy,
        val event: StrategyEvent.StrategyUpdated,
    ) : TransitionOutcome
}
