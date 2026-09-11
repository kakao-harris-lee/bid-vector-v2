package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import bidvector.decision.priority.ScoreFact
import bidvector.decision.priority.derive.DerivationAbsence
import bidvector.decision.priority.derive.MarginInputs
import bidvector.decision.priority.derive.deriveBudgetCapture
import bidvector.decision.priority.derive.deriveExpectedMargin
import bidvector.procurement.Notice
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.times
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.BidPredictionRequest
import java.math.BigDecimal

/**
 * `OpportunityAnalysis.predictionFacts`(scope.md ③⑥)가 쓰는 예측 요청 조립·budgetCapture·
 * expectedMargin 파생 — port 를 읽지 않는다(예측 호출 자체는 `OpportunityAnalysis.kt`
 * 소관, 이 파일은 요청 조립과 응답의 값 변환만 한다).
 */
internal fun predictionRequestFor(
    resolvedBaseAmount: ResolvedBaseAmount,
    notice: Notice,
    policies: ResolvedPolicies,
    correlationId: CorrelationId,
): BidPredictionRequest =
    BidPredictionRequest(
        baseAmount = resolvedBaseAmount,
        businessCategory = notice.businessCategory,
        agencyId = null,
        baseAmountProvenanceLabel = BaseAmountProvenance.Unknown,
        competitionSamples = emptyList(),
        objective = policies.opportunity.objective,
        releaseSelector = policies.opportunity.releaseSelector,
        correlationId = correlationId,
    )

/**
 * fitness 가 [0,1] 밖이거나 `candidates.base`(D-4B6B-6 로 `recommendedRate`·`predictedRate`
 * 둘 다 이 값이다)가 `MarginInputs.init`의 `≤ 1` 술어를 못 만족하면 예측 응답 전체를 못
 * 믿는 것으로 보고 두 성분 다 drop 한다(verifier r1 F-1) — `MarginInputs`를 짓기 **전에**
 * 그 생성자의 세 술어(recommendedRate·predictedRate·floorRate 각 `fraction ≤ 1`) 중
 * `candidates.base`에서 오는 둘을 여기서 막는다. `floorRate`는 `Notice`가 주는 별도 축이라
 * [expectedMarginFact]가 그 관문을 진다(D-4B6B-4 — floorRate 문제는 margin 만 drop).
 */
internal fun predictedFacts(
    predicted: BidPredictionOutcome.Predicted,
    baseAmount: BaseAmount,
    notice: Notice,
    policies: ResolvedPolicies,
    capacityScore: UnitScore,
): Pair<ScoreFact<UnitScore>, ScoreFact<UnitScore>> {
    val fitnessValue = predicted.fitness.score
    if (fitnessValue < BigDecimal.ZERO || fitnessValue > BigDecimal.ONE) {
        return absentPair(MlUnavailableReason.ContractViolation)
    }
    if (predicted.candidates.base.fraction > BigDecimal.ONE) {
        return absentPair(MlUnavailableReason.ContractViolation)
    }
    val priceFitness = UnitScore(fitnessValue)
    val budgetCapture = budgetCaptureFact(baseAmount, predicted.candidates.base, policies)
    val expectedMargin =
        expectedMarginFact(notice, predicted.candidates.base, priceFitness, capacityScore, policies)
    return budgetCapture to expectedMargin
}

private fun budgetCaptureFact(
    baseAmount: BaseAmount,
    recommendedRate: Rate,
    policies: ResolvedPolicies,
): ScoreFact<UnitScore> {
    val roundingPolicy =
        Resolution.Resolved(policies.opportunity.recommendedAmountRounding, policies.opportunityVersion)
    val rounded = (baseAmount * BidRate.recommended(recommendedRate)).roundedWith(roundingPolicy)
    return when (rounded) {
        is Measurement.Measured -> {
            deriveBudgetCapture(rounded.value.value, baseAmount, policies.derivation).toScoreFact()
        }

        is Measurement.Unmeasurable -> {
            val reason = DerivationAbsence.MoneyArithmeticUnmeasurable(rounded.reason)
            ScoreFact.Absent(bridgeDerivationAbsence(reason))
        }
    }
}

private fun expectedMarginFact(
    notice: Notice,
    predictedRate: Rate,
    priceFitness: UnitScore,
    capacityScore: UnitScore,
    policies: ResolvedPolicies,
): ScoreFact<UnitScore> {
    val floorRate = notice.floorRate?.rate
    if (floorRate != null && floorRate.fraction > BigDecimal.ONE) {
        return ScoreFact.Absent(bridgeDerivationAbsence(DerivationAbsence.FloorRateOutOfRange))
    }
    val marginInputs =
        MarginInputs(
            recommendedRate = predictedRate,
            floorRate = floorRate,
            predictedRate = predictedRate,
            priceFitness = priceFitness,
            capacity = capacityScore,
        )
    return ScoreFact.Present(deriveExpectedMargin(marginInputs, policies.derivation))
}

internal fun absentPair(reason: MlUnavailableReason): Pair<ScoreFact<UnitScore>, ScoreFact<UnitScore>> =
    ScoreFact.Absent(reason) to ScoreFact.Absent(reason)
