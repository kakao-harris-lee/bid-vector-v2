package bidvector.app.http

import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId
import bidvector.workflow.strategy.EditableField

/**
 * 편집 필드의 **wire 어휘**(D-6A2b-1) — 도메인 sealed 타입([EditableField])을
 * HTTP 가 부를 수 있는 낱말 하나로 옮긴다. 저장 어휘(`edit_session.state_payload` 의
 * kind+detail 쌍, `EditSessionSnapshot`)와 **다른 축**이라 그 값을 재사용하지 않는다 —
 * 저장 형태를 wire 형태로 쓰면 저장 codec 을 바꿀 때 공개 API 가 함께 흔들린다.
 *
 * 표는 전수 `when`(`else` 없음)이 낸다 — 새 [EditableField] 가 생기면 컴파일이 여기서
 * 막는다. 역방향(토큰 → 필드)은 그 표를 **뒤집어** 만든다: 손으로 두 번 적지 않으므로
 * 두 방향이 어긋날 자리가 없다.
 */
internal fun EditableField.token(): String =
    when (this) {
        is EditableField.Watch -> id.token()
        is EditableField.Threshold -> field.token()
        EditableField.CandidateLimit -> "CANDIDATE_LIMIT"
        EditableField.MaxActiveBids -> "MAX_ACTIVE_BIDS"
    }

private fun WatchRuleId.token(): String =
    when (this) {
        WatchRuleId.FocusCategory -> "FOCUS_CATEGORY"
        WatchRuleId.FocusRegion -> "FOCUS_REGION"
        WatchRuleId.ExcludeRegion -> "EXCLUDE_REGION"
        WatchRuleId.RequiredKeyword -> "REQUIRED_KEYWORD"
        WatchRuleId.ExcludeKeyword -> "EXCLUDE_KEYWORD"
        WatchRuleId.MinBudget -> "MIN_BUDGET"
        WatchRuleId.MaxBudget -> "MAX_BUDGET"
    }

private fun ThresholdField.token(): String =
    when (this) {
        ThresholdField.MinimumMatchScore -> "MINIMUM_MATCH_SCORE"
        ThresholdField.MinimumProbabilityScore -> "MINIMUM_PROBABILITY_SCORE"
        ThresholdField.BidNowThreshold -> "BID_NOW_THRESHOLD"
        ThresholdField.ReviewThreshold -> "REVIEW_THRESHOLD"
    }

/**
 * 편집 가능한 필드 전수 — 어휘의 **정의역**이다. 두 sealed 타입의 값 객체를 그대로 열거해
 * 만들고, 이 목록이 실제로 어휘 전체를 덮는지는 게이트가 바이트코드에서 도출한 하위 타입
 * 집합과 대조해 잰다(`EditableFieldVocabularyGateTest`) — 목록에서 하나가 빠지면 그
 * 필드는 HTTP 로 부를 수 없게 되는데, 컴파일러는 그 부재를 보지 못한다.
 */
internal val EDITABLE_FIELDS: List<EditableField> =
    listOf(
        EditableField.Watch(WatchRuleId.FocusCategory),
        EditableField.Watch(WatchRuleId.FocusRegion),
        EditableField.Watch(WatchRuleId.ExcludeRegion),
        EditableField.Watch(WatchRuleId.RequiredKeyword),
        EditableField.Watch(WatchRuleId.ExcludeKeyword),
        EditableField.Watch(WatchRuleId.MinBudget),
        EditableField.Watch(WatchRuleId.MaxBudget),
        EditableField.Threshold(ThresholdField.MinimumMatchScore),
        EditableField.Threshold(ThresholdField.MinimumProbabilityScore),
        EditableField.Threshold(ThresholdField.BidNowThreshold),
        EditableField.Threshold(ThresholdField.ReviewThreshold),
        EditableField.CandidateLimit,
        EditableField.MaxActiveBids,
    )

private val FIELDS_BY_TOKEN: Map<String, EditableField> = EDITABLE_FIELDS.associateBy(EditableField::token)

/** 알 수 없는 토큰은 지어내지 않고 `null` 이다 — 호출부가 형식 오류(400)로 옮긴다. */
internal fun editableFieldOfToken(token: String): EditableField? = FIELDS_BY_TOKEN[token]

internal val EDITABLE_FIELD_TOKENS: Set<String> = FIELDS_BY_TOKEN.keys
