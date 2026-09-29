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

    /**
     * 확인 대기 — [draft] 는 전략 **전체**의 스냅숏이고, [baseRevision] 은 그 스냅숏을 뜬
     * 시점의 전략 revision 이다(D-6A2b-18).
     *
     * **왜 기준을 함께 담는가.** `Confirm` 의
     * `seenRevision` 은 「클라이언트가 확인 직전에 본 revision」이라 지금 저장된 값과 같기
     * 쉽다 — 그 대조만으로는 **draft 가 언제 떠졌는지**를 묻지 못한다. 서버 생성 id 아래서는
     * 세션이 여럿 열리므로(알려진 제한 ①), 앞 세션이 적용한 뒤 뒤 세션이 낡은 스냅숏을
     * 확인하면 앞의 변경이 **409 없이** 사라졌다(실측). 이 값이 그 물음의 자리다.
     *
     * `null` 은 **이 필드가 생기기 전에 저장된 행**이다 — [bidvector.workflow.strategy.apply]
     * 의 대조가 `null != current.revision` 으로 항상 참이 되어 fail-closed 로 거부된다
     * (마이그레이션을 만들지 않고 낡은 행을 안전한 쪽으로 읽는다, D-6A2b-18).
     */
    data class WaitingForConfirmation(
        val field: EditableField,
        val draft: StrategyDraft,
        val baseRevision: StrategyRevision?,
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
