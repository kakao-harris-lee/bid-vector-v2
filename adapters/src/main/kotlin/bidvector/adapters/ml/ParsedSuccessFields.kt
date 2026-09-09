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
import bidvector.workflow.prediction.Uncertainty as DomainUncertainty
import contract.bidvector.ml.v1.IntervalSource as ProtoIntervalSource

/**
 * M4/4D-1(scope.md ⑦, 우회 (3)(6)) — `Success`의 형태 검증·decimal string 파싱을
 * `ResponseMapping.kt`에서 갈라낸 파일이다(detekt `TooManyFunctions`). 하나라도 무효면
 * [ParsedSuccessFields] 자체가 만들어지지 않는다 — 부분 성공을 인정하지 않는다.
 */
internal data class ParsedSuccessFields(
    val candidates: List<Rate>,
    val fitness: BigDecimal,
    val dispersion: BigDecimal,
    val estimateMargin: BigDecimal,
    val intervalSource: DomainIntervalSource,
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

private fun isAcceptableSuccessShape(success: Success): Boolean =
    success.uncertainty.sampleSize >= 1 &&
        hasExactlyThreeOrderedCandidates(success) &&
        success.candidatesList.all { it.origin == BidRateOrigin.BID_RATE_ORIGIN_RECOMMENDED }

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
    val allValid = allNotNull(fitness, dispersion, estimateMargin, intervalSource, *candidates.toTypedArray())
    return if (!allValid) {
        null
    } else {
        ParsedSuccessFields(
            candidates = candidates.filterNotNull(),
            fitness = checkNotNull(fitness),
            dispersion = checkNotNull(dispersion),
            estimateMargin = checkNotNull(estimateMargin),
            intervalSource = checkNotNull(intervalSource),
        )
    }
}

private fun allNotNull(vararg values: Any?): Boolean = values.all { it != null }

private fun String.toRateOrNull(): Rate? {
    val value = toValidatedBigDecimalOrNull() ?: return null
    return if (value.signum() < 0 || value > BigDecimal.ONE) null else Rate.ofFraction(value)
}

private fun String.toValidatedBigDecimalOrNull(): BigDecimal? =
    if (isNormalizedFraction(this)) BigDecimal(this) else null

private fun ProtoIntervalSource.toDomainOrNull(): DomainIntervalSource? =
    when (this) {
        ProtoIntervalSource.INTERVAL_SOURCE_CROSS_VALIDATION_RESIDUAL -> DomainIntervalSource.CrossValidationResidual
        ProtoIntervalSource.INTERVAL_SOURCE_TIME_HOLDOUT_RESIDUAL -> DomainIntervalSource.TimeHoldoutResidual
        ProtoIntervalSource.INTERVAL_SOURCE_UNSPECIFIED, ProtoIntervalSource.UNRECOGNIZED -> null
    }
