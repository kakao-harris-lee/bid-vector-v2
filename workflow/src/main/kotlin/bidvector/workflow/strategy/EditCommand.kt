package bidvector.workflow.strategy

import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyRevision

/**
 * 취소 사유(D-4A-4) — `Cancelled`는 종단이라 재개는 새 세션이다(같은 상태 재사용 금지).
 */
sealed interface CancellationReason {
    data object OperatorRequested : CancellationReason

    data class Other(
        val note: String,
    ) : CancellationReason
}

/**
 * 편집 command 넷(scope.md ①) — 전부 [actor]를 나른다(③). 값 동등(data class)이
 * 그대로 중복 command 판별 fingerprint 다(설계 검토 (2) #5).
 */
sealed interface EditCommand {
    val commandId: CommandId
    val sessionId: EditSessionId
    val actor: Actor

    /**
     * `ValueProvided` — [draft]는 이 필드의 새 값을 반영한 **전체** [StrategyDraft]다.
     * 필드 단위 원시 값을 도메인 draft 로 조립하는 것은 어댑터(M3/6A)의 일이다 — 이
     * command 는 그 결과만 받는다(⑦, 채널 독립).
     */
    data class ProvideValue(
        override val commandId: CommandId,
        override val sessionId: EditSessionId,
        override val actor: Actor,
        val field: EditableField,
        val draft: StrategyDraft,
    ) : EditCommand

    /** `Confirmed` — [seenRevision]은 확인 시점에 클라이언트가 본 전략 revision(우회 (2)). */
    data class Confirm(
        override val commandId: CommandId,
        override val sessionId: EditSessionId,
        override val actor: Actor,
        val seenRevision: StrategyRevision,
    ) : EditCommand

    /** `Rejected/Edit` — 확인 화면에서 (같거나 다른) 필드로 되돌아간다. */
    data class RequestEdit(
        override val commandId: CommandId,
        override val sessionId: EditSessionId,
        override val actor: Actor,
        val field: EditableField,
    ) : EditCommand

    data class Cancel(
        override val commandId: CommandId,
        override val sessionId: EditSessionId,
        override val actor: Actor,
        val reason: CancellationReason,
    ) : EditCommand
}
