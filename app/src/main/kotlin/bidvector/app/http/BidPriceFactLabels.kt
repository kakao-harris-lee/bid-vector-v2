package bidvector.app.http

import bidvector.decision.MlUnavailableReason
import bidvector.decision.PublishedFloorTrust
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.workflow.evaluation.MlAnalysisOutcome

/*
 * **공고 fact 와 ML 축의 어휘**(M7/7C ②, DEC-01 · DEC-03 · DEC-10 · ML-03) — 기초금액 해석
 * 갈래, 게시 하한율 근거 상태, ML 단계 상태.
 *
 * 하한율 축에서 이 파일이 하는 일은 **판정을 어휘로 옮기는 것뿐**이다(7C②-b) — 「근거가 되는가」는
 * `decision` 의 DEC-10 게이트가 판정하고, 여기에 밴드도 출처 분기도 다시 적지 않는다.
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

/** 게시값 출처가 나르는 공고 차수 — 법정 표 출처에는 그 값이 없다(소진 `when`). */
internal fun publishedOriginRoundOf(floor: FloorRate?): String? =
    when (val origin = floor?.origin) {
        null -> null
        is FloorRateOrigin.NoticeValue -> origin.noticeRevision.value
        is FloorRateOrigin.StatutoryTable -> null
    }

/**
 * 근거 칸의 **상태와 값을 한 판정에서 함께** 낸다(D-7C2B-1). 둘을 다른 함수가 따로 판정하던
 * 앞 판에서는 「상태는 근거가 못 된다는데 값이 실려 있다」가 조용히 가능했다 — 실제로 개연 불가
 * 게시값이 `USABLE` 과 함께 숫자까지 나갔다.
 */
internal data class FloorRateGrounds(
    val state: FloorRateGroundsState,
    val fraction: String?,
)

/**
 * DEC-10 게이트 판정을 표시 어휘로 옮긴다 — **밴드를 다시 적지 않고 판정을 받는다**(표시만 다른
 * 밴드를 보는 자리를 만들지 않는다). 소진 `when` 이라 게이트에 판정이 늘면 컴파일되지 않는다.
 *
 * **근거로 설 수 없는 값은 근거 칸에 싣지 않는다**(D-7C2B-2 — `ORIGIN_NOT_PUBLISHED` 의 관례를
 * 그대로 따른다). 값을 고치지도 않는다: 게이트의 `Implausible` 은 원값을 그대로 나르지만 이 칸은
 * 비우고 상태가 말한다. 출처·차수는 원문 사실이라 호출부가 따로 싣는다.
 */
internal fun floorRateGroundsOf(trust: PublishedFloorTrust): FloorRateGrounds =
    when (trust) {
        is PublishedFloorTrust.Trusted -> {
            val fraction = trust.rate.rate.fraction
            FloorRateGrounds(FloorRateGroundsState.USABLE, fraction.toPlainString())
        }

        PublishedFloorTrust.Absent -> {
            FloorRateGrounds(FloorRateGroundsState.ABSENT, null)
        }

        is PublishedFloorTrust.Implausible -> {
            FloorRateGrounds(FloorRateGroundsState.IMPLAUSIBLE, null)
        }

        is PublishedFloorTrust.OriginNotJudged -> {
            FloorRateGrounds(FloorRateGroundsState.ORIGIN_NOT_PUBLISHED, null)
        }
    }

internal fun mlAnalysisStateOf(outcome: MlAnalysisOutcome?): MlAnalysisStateLabel =
    when (outcome) {
        null -> {
            MlAnalysisStateLabel.NOT_REACHED
        }

        is MlAnalysisOutcome.Analyzed -> {
            MlAnalysisStateLabel.ANALYZED
        }

        is MlAnalysisOutcome.Unavailable -> {
            MlAnalysisStateLabel.UNAVAILABLE
        }

        is MlAnalysisOutcome.SimilarityProjectionNotReady -> {
            MlAnalysisStateLabel.SIMILARITY_PROJECTION_NOT_READY
        }
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
        MlAnalysisStateLabel.NOT_REACHED -> {
            ConfidenceNotAvailableReason.EVALUATION_NOT_REACHED_ML_STAGE
        }

        MlAnalysisStateLabel.UNAVAILABLE -> {
            ConfidenceNotAvailableReason.ML_UNAVAILABLE
        }

        MlAnalysisStateLabel.SIMILARITY_PROJECTION_NOT_READY -> {
            ConfidenceNotAvailableReason.SIMILARITY_PROJECTION_NOT_READY
        }

        // 분석됐어도 이 slice 는 가격 적합도를 싣지 않는다 — 점수 자리를 만들지 않았다.
        MlAnalysisStateLabel.ANALYZED -> {
            ConfidenceNotAvailableReason.ML_SCORE_NOT_PROVIDED
        }
    }
