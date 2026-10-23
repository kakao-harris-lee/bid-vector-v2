package bidvector.app.http

import bidvector.strategy.WatchUndeterminableReason
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.EvaluationDropReason
import bidvector.workflow.evaluation.EvaluationStage

/**
 * **탈락 축의 어휘**(M7/7C ②) — 판정에 이르지 못한 단계와 사유를 평탄한 칸으로 옮긴다.
 * 감시 규칙 ID 는 이 파일이 다시 적지 않는다 — 편집 경로가 이미 가진 wire 어휘를 쓴다.
 */

/** 탈락 축의 평탄한 칸들 — 판정에 이르면 전부 `null`·빈 목록이다. */
internal class DropFields(
    val stage: String?,
    val reasonCode: String?,
    val failedWatchRuleIds: List<String>,
    val watchUndeterminableReason: String?,
    val scoreThreshold: String?,
    val scoreActual: String?,
)


internal fun dropFieldsOf(evaluation: CandidateEvaluation): DropFields =
    when (evaluation) {
        is CandidateEvaluation.Reached -> DropFields(null, null, emptyList(), null, null, null)
        is CandidateEvaluation.NotReached -> dropFieldsOfReason(evaluation)
    }


private fun dropFieldsOfReason(notReached: CandidateEvaluation.NotReached): DropFields {
    val reason = notReached.reason
    val scores = scorePairOf(reason)
    return DropFields(
        stage = stageCode(notReached.stage),
        reasonCode = reason.reasonCode(),
        failedWatchRuleIds =
            (reason as? EvaluationDropReason.WatchGateRejected)
                ?.rejected
                ?.failed
                ?.map { it.token() }
                ?.sorted()
                .orEmpty(),
        watchUndeterminableReason =
            (reason as? EvaluationDropReason.WatchGateUndeterminable)
                ?.undeterminable
                ?.reason
                ?.let(::watchUndeterminableCode),
        scoreThreshold = scores?.first,
        scoreActual = scores?.second,
    )
}


/**
 * 점수 미달 두 사유가 싣는 (임계, 실제) — 10진 문자열로 낸다. 부동소수로 내면 `0.1` 같은 값이
 * 응답에서 다른 수가 된다. 그 밖의 사유는 이 칸이 없다(`null`).
 */
private fun scorePairOf(reason: EvaluationDropReason): Pair<String, String>? =
    when (reason) {
        is EvaluationDropReason.BelowMinimumMatchScore ->
            reason.threshold.toPlainString() to reason.actual.toPlainString()

        is EvaluationDropReason.BelowMinimumProbabilityScore ->
            reason.threshold.toPlainString() to reason.actual.toPlainString()

        else -> null
    }


private fun EvaluationDropReason.reasonCode(): String =
    when (this) {
        is EvaluationDropReason.NoticeNotBiddable -> "NOTICE_NOT_BIDDABLE"
        is EvaluationDropReason.WatchGateNotConfigured -> "WATCH_GATE_NOT_CONFIGURED"
        is EvaluationDropReason.WatchGateRejected -> "WATCH_GATE_REJECTED"
        is EvaluationDropReason.WatchGateUndeterminable -> "WATCH_GATE_UNDETERMINABLE"
        EvaluationDropReason.WatchSubjectUnavailable -> "WATCH_SUBJECT_UNAVAILABLE"
        is EvaluationDropReason.LicenseIneligible -> "LICENSE_INELIGIBLE"
        EvaluationDropReason.ActionThresholdsNotConfigured -> "ACTION_THRESHOLDS_NOT_CONFIGURED"
        EvaluationDropReason.AnalysisBudgetExhausted -> "ANALYSIS_BUDGET_EXHAUSTED"
        EvaluationDropReason.SimilarityProjectionNotReady -> "SIMILARITY_PROJECTION_NOT_READY"
        is EvaluationDropReason.BelowMinimumMatchScore -> "BELOW_MINIMUM_MATCH_SCORE"
        is EvaluationDropReason.BelowMinimumProbabilityScore -> "BELOW_MINIMUM_PROBABILITY_SCORE"
    }


private fun stageCode(stage: EvaluationStage): String =
    when (stage) {
        EvaluationStage.NoticeLifecycle -> "NOTICE_LIFECYCLE"
        EvaluationStage.WatchGate -> "WATCH_GATE"
        EvaluationStage.LicenseGate -> "LICENSE_GATE"
        EvaluationStage.ThresholdConfiguration -> "THRESHOLD_CONFIGURATION"
        EvaluationStage.AnalysisBudget -> "ANALYSIS_BUDGET"
        EvaluationStage.MlAvailability -> "ML_AVAILABILITY"
        EvaluationStage.ScoreThreshold -> "SCORE_THRESHOLD"
    }


/**
 * 감시 판정 불가 사유 — 이것도 sealed 이고 각 갈래가 `ReasonCode` 를 안고 있다. **축 수준의
 * 갈래만 낸다**(`BASE_AMOUNT_ABSENT` ↔ `BUDGET_NOT_COMPARABLE`) — 운영자에게 유의미한 구분이
 * 그 둘이고, 안쪽 `ReasonCode`(과세 불일치·provenance 미선언 등)는 이 응답에 칸을 두지 않았다.
 * 알려진 제한이며, 필요해지면 칸 하나를 더하는 것은 가산이다.
 */
private fun watchUndeterminableCode(reason: WatchUndeterminableReason): String =
    when (reason) {
        is WatchUndeterminableReason.BaseAmountAbsent -> "BASE_AMOUNT_ABSENT"
        is WatchUndeterminableReason.BudgetNotComparable -> "BUDGET_NOT_COMPARABLE"
    }
