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
        /**
         * **M6/6A-2b D-6A2b-28** — [draft] 를 뜬 전략의 revision. 어댑터가 draft 를 만든
         * **바로 그 읽기**의 값이고, 요청에서 받지 않는다(서버 값이다).
         *
         * 커널이 다시 읽은 값을 기준으로 삼으면 두 읽기 사이에 다른 커밋이 끼는 창이 남는다
         * (verifier r2 F-r2-3 실측 — 한 트랜잭션 안 두 SELECT 가 READ COMMITTED 에서 서로
         * 다른 스냅숏을 본다). 그 창에서 기준은 새 revision 이 되고 draft 는 낡은 값이라,
         * 확인이 통과하며 앞 세션의 변경이 사라졌다. 기준을 **draft 와 같은 읽기**에서
         * 실어 보내면 그 불일치가 value 시점에 드러난다.
         */
        val baseRevision: StrategyRevision,
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
