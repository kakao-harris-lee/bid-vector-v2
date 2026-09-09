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

    /**
     * `@ConsistentCopyVisibility` + `internal constructor`(verifier L-6) — `AppliedStrategy`·
     * `EditSession`과 같은 관례로 정렬한다. 이미 정당한 값 셋을 가진 자가 이 타입 자체를
     * 조립·`copy`하는 것은(위조가 아니다) 지금은 무해하지만(외부에서 받은 `Applied`를
     * 소비하는 자리가 없다), 자리가 생기면 이 폐쇄가 미리 막는다.
     */
    @ConsistentCopyVisibility
    data class Applied internal constructor(
        override val session: EditSession,
        internal val applied: AppliedStrategy,
        val event: StrategyEvent.StrategyUpdated,
    ) : TransitionOutcome
}
