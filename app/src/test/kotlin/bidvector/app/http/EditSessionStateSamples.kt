package bidvector.app.http

import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyRevision
import bidvector.workflow.strategy.CancellationReason
import bidvector.workflow.strategy.EditSessionState
import bidvector.workflow.strategy.EditableField

/**
 * 상태 어휘의 **정의역** — 하위 타입마다 대표값 하나다(C-5a). 값 자체는 중요하지 않다:
 * 이 목록이 재는 것은 「그 갈래가 어떤 낱말로 나가는가」뿐이고, 낱말은 구현의 표
 * (`EditSessionState.token()`)가 낸다.
 *
 * 이 목록이 하위 타입 **전부**를 덮는지는 컴파일러가 보지 못한다 —
 * `EditableFieldVocabularyGateTest` 가 바이트코드에서 하위 타입 집합을 도출해 등식으로
 * 잠근다. 새 상태가 생기면 그 등식이 먼저 붉어지고, 문서 enum 등식이 뒤따라 붉어진다.
 */
internal val EDIT_SESSION_STATE_SAMPLES: List<EditSessionState> =
    listOf(
        EditSessionState.WaitingForValue(EditableField.CandidateLimit),
        EditSessionState.WaitingForConfirmation(EditableField.CandidateLimit, StrategyDraft(), StrategyRevision(1)),
        EditSessionState.Applied(StrategyRevision(2)),
        EditSessionState.Cancelled(CancellationReason.OperatorRequested),
        EditSessionState.Expired,
    )
