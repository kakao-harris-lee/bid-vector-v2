package bidvector.app.http

import bidvector.procurement.Notice
import bidvector.strategy.WatchUndeterminableReason
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.EvaluationDropReason
import bidvector.workflow.evaluation.EvaluationStage

/**
 * **평가 상세 축의 어휘**(M7/7C ②) — 판정에 이르렀는지, 이르지 못했다면 어느 단계에서 왜
 * 멈췄는지를 평탄한 칸으로 옮겨 [EvaluationDetailFields] 를 짓는다. 판정 자체의 낱말은 같은
 * 패키지의 판정 축 파일이 갖는다.
 *
 * 감시 규칙 ID 는 이 파일이 다시 적지 않는다 — 편집 경로가 이미 가진 wire 어휘를 쓴다(같은
 * 도메인 타입이 endpoint 마다 다른 낱말로 보이면 화면이 두 표를 든다).
 *
 * **중간 보유 타입을 두지 않는다** — 값을 담을 작은 class 를 top-level 로 두면 파일 이름 규칙이
 * 그 class 이름을 파일명으로 요구하고, 그 이름은 이 파일이 하는 일을 말하지 못한다(실측으로
 * 걸렸다). 그래서 `when` 의 두 갈래가 각자 DTO 를 바로 짓는다.
 */
internal fun evaluationDetailFieldsOf(
    evaluation: CandidateEvaluation,
    notice: Notice,
): EvaluationDetailFields =
    when (evaluation) {
        is CandidateEvaluation.Reached -> {
            EvaluationDetailFields(
                outcome = outcomeLabelOf(evaluation).name,
                verdict = verdictLabelOf(evaluation),
                verdictReasonCodes = verdictReasonCodesOf(evaluation),
                dropStage = null,
                dropReasonCode = null,
                noticeStatus = notice.status.name,
                failedWatchRuleIds = emptyList(),
                watchUndeterminableReason = null,
                scoreThreshold = null,
                scoreActual = null,
            )
        }

        is CandidateEvaluation.NotReached -> {
            notReachedFieldsOf(evaluation, notice)
        }
    }

private fun notReachedFieldsOf(
    notReached: CandidateEvaluation.NotReached,
    notice: Notice,
): EvaluationDetailFields {
    val reason = notReached.reason
    val scores = scorePairOf(reason)
    return EvaluationDetailFields(
        outcome = outcomeLabelOf(notReached).name,
        verdict = null,
        verdictReasonCodes = emptyList(),
        dropStage = stageCode(notReached.stage),
        dropReasonCode = reason.reasonCode(),
        noticeStatus = notice.status.name,
        failedWatchRuleIds = failedWatchRuleCodesOf(reason),
        watchUndeterminableReason = watchUndeterminableCodeOf(reason),
        scoreThreshold = scores?.first,
        scoreActual = scores?.second,
    )
}

private fun failedWatchRuleCodesOf(reason: EvaluationDropReason): List<String> =
    (reason as? EvaluationDropReason.WatchGateRejected)
        ?.rejected
        ?.failed
        ?.map { it.token() }
        ?.sorted()
        .orEmpty()

/**
 * 감시 판정 불가 사유 — sealed 이고 각 갈래가 `ReasonCode` 를 안고 있다. **축 수준의 갈래만
 * 낸다**(`BASE_AMOUNT_ABSENT` ↔ `BUDGET_NOT_COMPARABLE`) — 운영자에게 유의미한 구분이 그 둘이고,
 * 안쪽 `ReasonCode`(과세 불일치·provenance 미선언 등)는 이 응답에 칸을 두지 않았다. 알려진
 * 제한이며, 필요해지면 칸 하나를 더하는 것은 가산이다.
 */
private fun watchUndeterminableCodeOf(reason: EvaluationDropReason): String? {
    val undeterminable = (reason as? EvaluationDropReason.WatchGateUndeterminable)?.undeterminable?.reason
    return when (undeterminable) {
        null -> null
        is WatchUndeterminableReason.BaseAmountAbsent -> "BASE_AMOUNT_ABSENT"
        is WatchUndeterminableReason.BudgetNotComparable -> "BUDGET_NOT_COMPARABLE"
    }
}

/**
 * 점수 미달 두 사유가 싣는 (임계, 실제) — 10진 문자열로 낸다. 부동소수로 내면 `0.1` 같은 값이
 * 응답에서 다른 수가 된다. 그 밖의 사유는 이 칸이 없다(`null`).
 */
private fun scorePairOf(reason: EvaluationDropReason): Pair<String, String>? =
    when (reason) {
        is EvaluationDropReason.BelowMinimumMatchScore -> {
            reason.threshold.toPlainString() to reason.actual.toPlainString()
        }

        is EvaluationDropReason.BelowMinimumProbabilityScore -> {
            reason.threshold.toPlainString() to reason.actual.toPlainString()
        }

        else -> {
            null
        }
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
