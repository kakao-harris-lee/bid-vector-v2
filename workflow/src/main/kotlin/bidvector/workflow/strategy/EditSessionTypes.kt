package bidvector.workflow.strategy

import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyRevision
import java.time.Instant

data class EditSessionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "EditSessionId는 빈 문자열일 수 없다" }
    }
}

data class CommandId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CommandId는 빈 문자열일 수 없다" }
    }
}

/**
 * 편집 흐름 상태 기계(scope.md ①) — 허용 쌍만 갖는 전수 `when`(설계 검토 (1))이 유일한
 * 소비 방식이다. 표에 없는 (state, command) 쌍은 [apply] 안에서 거부로 떨어지는 것이
 * 아니라 그 자리에 명시적으로 적힌다.
 */
sealed interface EditSessionState {
    data class WaitingForValue(
        val field: EditableField,
    ) : EditSessionState

    data class WaitingForConfirmation(
        val field: EditableField,
        val draft: StrategyDraft,
    ) : EditSessionState

    data class Applied(
        val revision: StrategyRevision,
    ) : EditSessionState

    data class Cancelled(
        val reason: CancellationReason,
    ) : EditSessionState

    data object Expired : EditSessionState
}

/**
 * 전략 편집 세션 aggregate(scope.md ⑤) — 유일한 전이 경로는 [apply]다
 * (`@ConsistentCopyVisibility` + `internal constructor`, 1E `OperatorStrategy` 관례 —
 * 설계 검토 (2) #1). [lastCommand]는 직전 처리 command 전체를 그대로 들고 있다 — 값
 * 동등 비교(data class)가 id·fingerprint 대조를 겸한다(과잉 판정 (i)의 반대 선택 —
 * 처리한 command id 전체 집합이 아니라 한 건만).
 */
@ConsistentCopyVisibility
data class EditSession internal constructor(
    val id: EditSessionId,
    val actor: Actor.Operator,
    val state: EditSessionState,
    val expiresAt: Instant,
    val sessionVersion: Int,
    val lastCommand: EditCommand?,
)
