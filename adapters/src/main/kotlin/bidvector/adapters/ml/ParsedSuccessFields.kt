package bidvector.adapters.ml

import bidvector.sharedkernel.Rate
import bidvector.workflow.prediction.BidRateCandidates
import bidvector.workflow.prediction.PriceFitness
import contract.bidvector.ml.v1.BidRateOrigin
import contract.bidvector.ml.v1.Candidate
import contract.bidvector.ml.v1.CandidateLabel
import contract.bidvector.ml.v1.Success
import java.math.BigDecimal
import bidvector.workflow.prediction.IntervalSource as DomainIntervalSource
import bidvector.workflow.prediction.SegmentSupport as DomainSegmentSupport
import bidvector.workflow.prediction.Uncertainty as DomainUncertainty
import bidvector.workflow.prediction.Weight as DomainWeight
import contract.bidvector.ml.v1.IntervalSource as ProtoIntervalSource

/**
 * M4/4D-1(scope.md ⑦, 우회 (3)(6)) — `Success`의 형태 검증·decimal string 파싱을
 * `ResponseMapping.kt`에서 갈라낸 파일이다(detekt `TooManyFunctions`). 하나라도 무효면
 * [ParsedSuccessFields] 자체가 만들어지지 않는다 — 부분 성공을 인정하지 않는다.
 *
 * M4/4D-3(scope.md D-4D3-1~2) — 진단 여섯 성분(`trainingRowCount`·`segmentSupport`·
 * `shrinkageWeight`·`excludedObservations`·`agencySampleCount`·
 * `agencySampleBelowThreshold`)이 더해졌다. 정수 셋·bool 은 `isAcceptableSuccessShape`
 * (`hasValidDiagnosticsShape`)가 이미 음수 아님을 걸렀으므로 여기서는 그대로 읽는다 —
 * `shrinkageWeight`·`segmentSupport`만 값 파싱(정규형·enum 매핑)이 필요하다. 그 파싱
 * 헬퍼(`toWeightOrNull`·`ProtoSegmentSupport.toDomainOrNull`)와 `toDiagnostics()`는
 * `DiagnosticsShapeValidation.kt`에 있다(detekt `TooManyFunctions` — 4D-3 이 3함수를
 * 더하면 이 파일이 한도 11을 넘는다, `CandidateShapeValidation.kt`를 가른 것과 같은 사유).
 */
internal data class ParsedSuccessFields(
    val candidates: List<Rate>,
    val fitness: BigDecimal,
    val dispersion: BigDecimal,
    val estimateMargin: BigDecimal,
    val intervalSource: DomainIntervalSource,
    val trainingRowCount: Int,
    val segmentSupport: DomainSegmentSupport,
    val shrinkageWeight: DomainWeight,
    val excludedObservations: Int,
    val agencySampleCount: Int,
    val agencySampleBelowThreshold: Boolean,
)

internal fun ParsedSuccessFields.toCandidates(): BidRateCandidates =
    BidRateCandidates(candidates[0], candidates[1], candidates[2])

internal fun ParsedSuccessFields.toFitness(): PriceFitness = PriceFitness(fitness)

internal fun ParsedSuccessFields.toUncertainty(success: Success): DomainUncertainty =
    DomainUncertainty(
        sampleSize = success.uncertainty.sampleSize,
        dispersion = dispersion,
        estimateMargin = estimateMargin,
        intervalSource = intervalSource,
    )

internal fun validatedSuccessFields(success: Success): ParsedSuccessFields? {
    if (!isAcceptableSuccessShape(success)) return null
    return parsedSuccessFields(success)
}

private fun isAcceptableSuccessShape(success: Success): Boolean {
    val checks =
        listOf(
            success.uncertainty.sampleSize >= 1,
            hasExactlyThreeOrderedCandidates(success),
            success.candidatesList.all { it.origin == BidRateOrigin.BID_RATE_ORIGIN_RECOMMENDED },
            hasValidReleaseShape(success.release),
            hasOrderedCandidateRates(success),
            hasValidDiagnosticsShape(success),
        )
    return checks.all { it }
}

private fun hasExactlyThreeOrderedCandidates(success: Success): Boolean {
    val labels = success.candidatesList.map(Candidate::getLabel)
    return labels ==
        listOf(
            CandidateLabel.CANDIDATE_LABEL_CONSERVATIVE,
            CandidateLabel.CANDIDATE_LABEL_BASE,
            CandidateLabel.CANDIDATE_LABEL_AGGRESSIVE,
        )
}

private fun parsedSuccessFields(success: Success): ParsedSuccessFields? {
    val candidates = success.candidatesList.map { it.bidRate.fraction.toRateOrNull() }
    val fitness = success.fitness.score.toValidatedBigDecimalOrNull()
    val dispersion = success.uncertainty.dispersion.toValidatedBigDecimalOrNull()
    val estimateMargin = success.uncertainty.estimateMargin.toValidatedBigDecimalOrNull()
    val intervalSource = success.uncertainty.intervalSource.toDomainOrNull()
    val shrinkageWeight =
        success.diagnostics.shrinkageWeight.fraction
            .toWeightOrNull()
    val segmentSupport = success.diagnostics.segmentSupport.toDomainOrNull()
    val allValid =
        allNotNull(
            fitness,
            dispersion,
            estimateMargin,
            intervalSource,
            shrinkageWeight,
            segmentSupport,
            *candidates.toTypedArray(),
        )
    return if (!allValid) {
        null
    } else {
        ParsedSuccessFields(
            candidates = candidates.filterNotNull(),
            fitness = checkNotNull(fitness),
            dispersion = checkNotNull(dispersion),
            estimateMargin = checkNotNull(estimateMargin),
            intervalSource = checkNotNull(intervalSource),
            trainingRowCount = success.diagnostics.trainingRowCount,
            segmentSupport = checkNotNull(segmentSupport),
            shrinkageWeight = checkNotNull(shrinkageWeight),
            excludedObservations = success.diagnostics.excludedObservations,
            agencySampleCount = success.diagnostics.agencySampleCount,
            agencySampleBelowThreshold = success.diagnostics.agencySampleBelowThreshold,
        )
    }
}

private fun allNotNull(vararg values: Any?): Boolean = values.all { it != null }

/**
 * `internal` — `CandidateShapeValidation.kt`·`DiagnosticsShapeValidation.kt`(다른 파일,
 * 같은 모듈)도 같은 파싱을 쓴다(중복 금지).
 */
internal fun String.toRateOrNull(): Rate? {
    val value = toValidatedBigDecimalOrNull() ?: return null
    return if (value.signum() < 0 || value > BigDecimal.ONE) null else Rate.ofFraction(value)
}

/** `internal` — `DiagnosticsShapeValidation.kt`(`toWeightOrNull`)도 같은 파싱을 쓴다(중복 금지). */
internal fun String.toValidatedBigDecimalOrNull(): BigDecimal? =
    if (isNormalizedFraction(this)) BigDecimal(this) else null

private fun ProtoIntervalSource.toDomainOrNull(): DomainIntervalSource? =
    when (this) {
        ProtoIntervalSource.INTERVAL_SOURCE_CROSS_VALIDATION_RESIDUAL -> DomainIntervalSource.CrossValidationResidual
        ProtoIntervalSource.INTERVAL_SOURCE_TIME_HOLDOUT_RESIDUAL -> DomainIntervalSource.TimeHoldoutResidual
        ProtoIntervalSource.INTERVAL_SOURCE_POSTERIOR_PREDICTIVE -> DomainIntervalSource.PosteriorPredictive
        ProtoIntervalSource.INTERVAL_SOURCE_UNSPECIFIED, ProtoIntervalSource.UNRECOGNIZED -> null
    }
