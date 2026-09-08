package bidvector.workflow.strategy

import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyEvent

/**
 * [apply]의 결과(scope.md ④) — 저장할 [OperatorStrategy]를 내는 갈래는 [Applied] 하나뿐이다
 * (우회 (3) 차단의 타입 근거 — `StrategyRepository.save` 인자를 만드는 유일한 자리).
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
        val strategy: OperatorStrategy,
        val event: StrategyEvent.StrategyUpdated,
    ) : TransitionOutcome
}
