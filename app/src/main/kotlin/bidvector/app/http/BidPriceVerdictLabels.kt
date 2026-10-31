package bidvector.app.http

import bidvector.decision.BidNowReason
import bidvector.decision.ReviewReason
import bidvector.decision.SkipReason
import bidvector.decision.Verdict
import bidvector.workflow.evaluation.CandidateEvaluation

/*
 * **판정 축의 어휘**(M7/7C ②) — `Verdict` 세 갈래와 그 사유 코드. 전부 소진 `when` 이라
 * 4B-1 사다리에 사유가 늘면 이 자리가 컴파일로 드러난다.
 */

internal fun outcomeLabelOf(evaluation: CandidateEvaluation): EvaluationOutcomeLabel =
    when (evaluation) {
        is CandidateEvaluation.Reached -> EvaluationOutcomeLabel.REACHED
        is CandidateEvaluation.NotReached -> EvaluationOutcomeLabel.NOT_REACHED
    }

internal fun verdictLabelOf(evaluation: CandidateEvaluation): String? =
    when (evaluation) {
        is CandidateEvaluation.NotReached -> {
            null
        }

        is CandidateEvaluation.Reached -> {
            when (evaluation.verdict) {
                is Verdict.BidNow -> "BID_NOW"
                is Verdict.Review -> "REVIEW"
                is Verdict.Skip -> "SKIP"
            }
        }
    }

internal fun verdictReasonCodesOf(evaluation: CandidateEvaluation): List<String> =
    when (evaluation) {
        is CandidateEvaluation.NotReached -> {
            emptyList()
        }

        is CandidateEvaluation.Reached -> {
            when (val verdict = evaluation.verdict) {
                is Verdict.BidNow -> verdict.reasons.map(::bidNowReasonCode)
                is Verdict.Review -> verdict.reasons.map(::reviewReasonCode)
                is Verdict.Skip -> listOf(skipReasonCode(verdict.reason))
            }
        }
    }

private fun bidNowReasonCode(reason: BidNowReason): String =
    when (reason) {
        is BidNowReason.PriorityAboveBidNowThreshold -> "PRIORITY_ABOVE_BID_NOW_THRESHOLD"
        is BidNowReason.ForceBidOverride -> "FORCE_BID_OVERRIDE"
    }

private fun reviewReasonCode(reason: ReviewReason): String =
    when (reason) {
        is ReviewReason.PriorityInReviewBand -> "PRIORITY_IN_REVIEW_BAND"
        is ReviewReason.MlUnavailable -> "ML_UNAVAILABLE"
    }

private fun skipReasonCode(reason: SkipReason): String =
    when (reason) {
        SkipReason.CapacityHold -> "CAPACITY_HOLD"
        SkipReason.LowPriority -> "LOW_PRIORITY"
    }
