package bidvector.workflow.strategy

import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId

/**
 * 편집 세션이 지금 기다리는 필드(scope.md ① `WaitingForValue(field)`) — 1E 가 이미 닫은
 * 어휘(`WatchRuleId`·`ThresholdField`)를 그대로 재사용한다(중복 금지, v2-지침서.md §5).
 * `candidateLimit`만 그 두 어휘에 없어 이 sealed 가 자리를 더한다.
 */
sealed interface EditableField {
    data class Watch(
        val id: WatchRuleId,
    ) : EditableField

    data class Threshold(
        val field: ThresholdField,
    ) : EditableField

    data object CandidateLimit : EditableField
}
