package bidvector.app.http

import bidvector.decision.MlUnavailableReason
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.workflow.evaluation.MlAnalysisOutcome

/*
 * **공고 fact 와 ML 축의 어휘**(M7/7C ②, DEC-01 · DEC-03 · ML-03) — 기초금액 해석 갈래,
 * 게시 하한율의 출처 판정, ML 단계 상태. 「게시값만 근거다」가 이 파일의 분기다.
 */

internal fun baseAmountResolutionLabel(resolved: ResolvedBaseAmount): String =
    when (resolved) {
        is ResolvedBaseAmount.Direct -> "DIRECT"
        is ResolvedBaseAmount.FallbackFromBudget -> "FALLBACK_FROM_BUDGET"
        is ResolvedBaseAmount.DerivedFromOpeningAmount -> "DERIVED_FROM_OPENING"
    }


internal fun floorRateOriginLabel(floor: FloorRate): String =
    when (floor.origin) {
        is FloorRateOrigin.NoticeValue -> "NOTICE_VALUE"
        is FloorRateOrigin.StatutoryTable -> "STATUTORY_TABLE"
    }


/**
 * **게시값만 근거다**(운영자 결정 2026-10-15) — 법정 하한율 표에서 온 값은 근거 칸에 싣지
 * 않는다. V2 에 법정 하한율 해석기가 없어 그런 값이 들어올 경로는 오늘 없지만, 타입이 그 값을
 * 표현할 수 있는 한 이 분기가 그 자리를 막는다.
 */
internal fun usableFloorRateOf(floor: FloorRate?): FloorRate? =
    floor?.takeIf { it.origin is FloorRateOrigin.NoticeValue }


/** 게시값 출처가 나르는 공고 차수 — 법정 표 출처에는 그 값이 없다(소진 `when`). */
internal fun publishedOriginRoundOf(floor: FloorRate?): String? =
    when (val origin = floor?.origin) {
        null -> null
        is FloorRateOrigin.NoticeValue -> origin.noticeRevision.value
        is FloorRateOrigin.StatutoryTable -> null
    }


internal fun floorRateGroundsStateOf(floor: FloorRate?): FloorRateGroundsState =
    when {
        floor == null -> FloorRateGroundsState.ABSENT
        floor.origin is FloorRateOrigin.NoticeValue -> FloorRateGroundsState.USABLE
        else -> FloorRateGroundsState.ORIGIN_NOT_PUBLISHED
    }


internal fun mlAnalysisStateOf(outcome: MlAnalysisOutcome?): MlAnalysisStateLabel =
    when (outcome) {
        null -> MlAnalysisStateLabel.NOT_REACHED
        is MlAnalysisOutcome.Analyzed -> MlAnalysisStateLabel.ANALYZED
        is MlAnalysisOutcome.Unavailable -> MlAnalysisStateLabel.UNAVAILABLE
        is MlAnalysisOutcome.SimilarityProjectionNotReady ->
            MlAnalysisStateLabel.SIMILARITY_PROJECTION_NOT_READY
    }


internal fun mlUnavailableReasonLabelOf(outcome: MlAnalysisOutcome?): String? =
    (outcome as? MlAnalysisOutcome.Unavailable)
        ?.let { mlUnavailableReasonCode(it.reason) }


private fun mlUnavailableReasonCode(reason: MlUnavailableReason): String =
    when (reason) {
        MlUnavailableReason.ScoreNotProvided -> "SCORE_NOT_PROVIDED"
        MlUnavailableReason.DeadlineExceeded -> "DEADLINE_EXCEEDED"
        MlUnavailableReason.CircuitOpen -> "CIRCUIT_OPEN"
        MlUnavailableReason.RetryBudgetExhausted -> "RETRY_BUDGET_EXHAUSTED"
        MlUnavailableReason.TransportFailed -> "TRANSPORT_FAILED"
        MlUnavailableReason.ModelNotReady -> "MODEL_NOT_READY"
        MlUnavailableReason.ReleaseMismatch -> "RELEASE_MISMATCH"
        MlUnavailableReason.ContractViolation -> "CONTRACT_VIOLATION"
        MlUnavailableReason.UnsupportedSchema -> "UNSUPPORTED_SCHEMA"
        MlUnavailableReason.UnsupportedRelease -> "UNSUPPORTED_RELEASE"
        MlUnavailableReason.InvalidRequest -> "INVALID_REQUEST"
    }


internal fun confidenceNotAvailableReasonOf(state: MlAnalysisStateLabel): ConfidenceNotAvailableReason =
    when (state) {
        MlAnalysisStateLabel.NOT_REACHED -> ConfidenceNotAvailableReason.EVALUATION_NOT_REACHED_ML_STAGE
        MlAnalysisStateLabel.UNAVAILABLE -> ConfidenceNotAvailableReason.ML_UNAVAILABLE
        MlAnalysisStateLabel.SIMILARITY_PROJECTION_NOT_READY ->
            ConfidenceNotAvailableReason.SIMILARITY_PROJECTION_NOT_READY
        // 분석됐어도 이 slice 는 가격 적합도를 싣지 않는다 — 점수 자리를 만들지 않았다.
        MlAnalysisStateLabel.ANALYZED -> ConfidenceNotAvailableReason.ML_SCORE_NOT_PROVIDED
    }
