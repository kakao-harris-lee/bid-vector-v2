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
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.times
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.BidPredictionRequest
import bidvector.workflow.prediction.CompetitionSample
import java.math.BigDecimal

/**
 * `OpportunityAnalysis.predictionFacts`(scope.md ③⑥)가 쓰는 예측 요청 조립·budgetCapture·
 * expectedMargin 파생 — port 를 읽지 않는다(예측 호출 자체는 `OpportunityAnalysis.kt`
 * 소관, 이 파일은 요청 조립과 응답의 값 변환만 한다).
 *
 * M4/4B-8(D-4B8-1·2, `OPEN-4B7-TARGET-LABEL` 닫힘) — `baseAmountProvenanceLabel`은 더는
 * `BaseAmountProvenance.Unknown` 상수가 아니다. 4B-7 표본 라벨과 같은 분류기
 * (`provenanceLabelFor`, `SampleConversion.kt`)를 `opening = null`로 부른다 — 대상 공고는
 * 개찰 전이라 낙찰 입력이 구조적으로 없다(`DerivedYega`는 그 부재로 항상 불가, 두 번째
 * 분류기를 두지 않는다). 정책은 `policies.provenancePolicy`(`OpportunityAnalysis.
 * resolvePolicies`가 표본과 같은 `SAMPLE_PROVENANCE_POLICY` singleton에서 resolve) — 이
 * 함수는 여전히 port·Clock을 읽지 않는다(값으로 받는다).
 */
internal fun predictionRequestFor(
    resolvedBaseAmount: ResolvedBaseAmount,
    notice: Notice,
    policies: ResolvedPolicies,
    correlationId: CorrelationId,
    // M4/4B-7(D-4B7-9) — 표본 공급은 OpportunityAnalysis가 CompetitionSamplePort로 얻어 넘긴다.
    // 이 파일은 port를 읽지 않는다(scope.md ③ KDoc) — 값만 조립한다. 기본값을 두지 않는다
    // (verifier r1 F-11) — 호출부가 이 인자를 빠뜨리면 컴파일이 실패해야 한다. 표본
    // 0건은 호출부가 명시적으로 `emptyList()`를 넘겨야만 나오는 값이지, 조용히 새는
    // 기본값이 아니다.
    competitionSamples: List<CompetitionSample>,
): BidPredictionRequest =
    BidPredictionRequest(
        baseAmount = resolvedBaseAmount,
        businessCategory = notice.businessCategory,
        agencyId = null,
        baseAmountProvenanceLabel =
            provenanceLabelFor(notice, null, resolvedBaseAmount.amount, policies.provenancePolicy),
        competitionSamples = competitionSamples,
        objective = policies.opportunity.objective,
        releaseSelector = policies.opportunity.releaseSelector,
        correlationId = correlationId,
    )

/**
 * 세 함수(`predictedFacts`·`absentPair`·`absentPairForUnavailableSupply`)가 공유하는
 * 성분 묶음(M4/4D-4, D-4D4-2) — 예전 `Pair<ScoreFact, ScoreFact>`에 [evidence]를
 * 더한다. `internal`인 이유는 `PredictionFactsTest` 헤더 KDoc과 같다 — `analyze()`
 * 전체를 거치면 `MlAnalysisOutcome.Analyzed`가 이 성분을 그대로 노출하지 않는다.
 */
internal data class PredictionComponents(
    val budgetCapture: ScoreFact<UnitScore>,
    val expectedMargin: ScoreFact<UnitScore>,
    val evidence: PredictionEvidence,
)

/**
 * fitness 가 [0,1] 밖이거나 `candidates.base`(D-4B6B-6 로 `recommendedRate`·`predictedRate`
 * 둘 다 이 값이다)가 `MarginInputs.init`의 `≤ 1` 술어를 못 만족하면 예측 응답 전체를 못
 * 믿는 것으로 보고 두 성분 다 drop 한다(verifier r1 F-1) — `MarginInputs`를 짓기 **전에**
 * 그 생성자의 세 술어(recommendedRate·predictedRate·floorRate 각 `fraction ≤ 1`) 중
 * `candidates.base`에서 오는 둘을 여기서 막는다. `floorRate`는 `Notice`가 주는 별도 축이라
 * [expectedMarginFact]가 그 관문을 진다(D-4B6B-4 — floorRate 문제는 margin 만 drop).
 *
 * [excludedSamples]는 M4/4D-4(D-4D4-7) — 호출자(`OpportunityAnalysis.predictionFacts`)가
 * `CompetitionSampleSupply.Supplied.excluded`를 그대로 넘긴다. 이 함수는 그 계수를 다시
 * 세지 않고 [PredictionEvidence.Diagnosed]로 옮겨 싣기만 한다.
 */
internal fun predictedFacts(
    predicted: BidPredictionOutcome.Predicted,
    baseAmount: BaseAmount,
    notice: Notice,
    policies: ResolvedPolicies,
    capacityScore: UnitScore,
    excludedSamples: Map<SampleExclusionReason, Int>,
): PredictionComponents {
    if (predictionUntrustworthy(predicted)) {
        return absentPair(MlUnavailableReason.ContractViolation)
    }
    val priceFitness = UnitScore(predicted.fitness.score)
    val budgetCapture = budgetCaptureFact(baseAmount, predicted.candidates.base, policies)
    val expectedMargin =
        expectedMarginFact(notice, predicted.candidates.base, priceFitness, capacityScore, policies)
    val evidence = PredictionEvidence.Diagnosed(predicted.diagnostics, predicted.release, excludedSamples)
    return PredictionComponents(budgetCapture, expectedMargin, evidence)
}

/** fitness 범위 위반 또는 `candidates.base > 1`(`MarginInputs.init`의 두 술어) — 둘 다 같은 취급. */
private fun predictionUntrustworthy(predicted: BidPredictionOutcome.Predicted): Boolean {
    val fitnessValue = predicted.fitness.score
    val fitnessOutOfRange = fitnessValue < BigDecimal.ZERO || fitnessValue > BigDecimal.ONE
    val rateOutOfRange = predicted.candidates.base.fraction > BigDecimal.ONE
    return fitnessOutOfRange || rateOutOfRange
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

/** M4/4D-4(D-4D4-1) — 예측을 시도하지 않았거나 실패로 끝난 경우는 전부 `NotPredicted`다. */
internal fun absentPair(reason: MlUnavailableReason): PredictionComponents =
    PredictionComponents(ScoreFact.Absent(reason), ScoreFact.Absent(reason), PredictionEvidence.NotPredicted(reason))

/**
 * D-4B7-9(verifier r2 N-2) — 표본 공급 실패(`CompetitionSampleSupply.Unavailable`) 사유를
 * 그대로 예측 성분 드롭 사유로 옮긴다(예측 자체를 시도하지 않는다). `internal`로 뽑은
 * 이유는 `PredictionFactsTest.kt` 헤더 KDoc과 같다 — `analyze()` 전체를 거치면
 * `MlAnalysisOutcome.Analyzed`가 budgetCapture·expectedMargin 성분(과 그 드롭 사유)을
 * 노출하지 않아 D-4B7-9가 실제로 `ScoreNotProvided`로 뭉개지 않고 `supply.reason`을
 * 그대로 옮기는지를 `OpportunityAnalysisTest`(통합 층)에서는 잴 수 없다 — 같은 패키지
 * test가 이 함수를 직접 불러 잰다(verifier r1 F-1이 세운 같은 관례).
 */
internal fun absentPairForUnavailableSupply(
    supply: CompetitionSampleSupply.Unavailable,
): PredictionComponents = absentPair(supply.reason)
