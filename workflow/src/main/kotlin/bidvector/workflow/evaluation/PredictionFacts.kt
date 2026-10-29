package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.PublishedFloorExclusion
import bidvector.decision.PublishedFloorTrust
import bidvector.decision.UnitScore
import bidvector.decision.exclusion
import bidvector.decision.priority.ScoreFact
import bidvector.decision.priority.derive.DerivationAbsence
import bidvector.decision.priority.derive.deriveBudgetCapture
import bidvector.decision.priority.derive.deriveExpectedMargin
import bidvector.decision.priority.derive.marginInputsFrom
import bidvector.decision.trustPublishedFloorRate
import bidvector.procurement.Notice
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BidRate
import bidvector.sharedkernel.Measurement
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.times
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.AgencyId
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.BidPredictionRequest
import bidvector.workflow.prediction.CompetitionSample
import java.math.BigDecimal

/**
 * `OpportunityAnalysis.predictionFacts`(scope.md ③⑥)가 쓰는 예측 요청 조립·budgetCapture·
 * expectedMargin 파생 — port 를 읽지 않는다(예측 호출 자체는 `OpportunityAnalysis.kt`
 * 소관, 이 파일은 요청 조립과 응답의 값 변환만 한다).
 *
 * (D-4B8-1·2, `OPEN-4B7-TARGET-LABEL` 닫힘) — `baseAmountProvenanceLabel`은 더는
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
    // (D-4B7-9) — 표본 공급은 OpportunityAnalysis가 CompetitionSamplePort로 얻어 넘긴다.
    // 이 파일은 port를 읽지 않는다(scope.md ③ KDoc) — 값만 조립한다. 기본값을 두지 않는다 —
    // 호출부가 이 인자를 빠뜨리면 컴파일이 실패해야 한다. 표본
    // 0건은 호출부가 명시적으로 `emptyList()`를 넘겨야만 나오는 값이지, 조용히 새는
    // 기본값이 아니다.
    competitionSamples: List<CompetitionSample>,
): BidPredictionRequest =
    BidPredictionRequest(
        baseAmount = resolvedBaseAmount,
        businessCategory = notice.businessCategory,
        // (D-3H2-1) — 수요기관 코드만 읽는다. 공고기관으로 폴백하지 않는다(D-3H-2,
        // scope.md 우회 (1)) — 두 축은 canonicalize 단계에서부터 서로 다른 필드다
        // (`Agency.kt` KDoc). 이름만 있고 코드가 없으면 `AgencyCode`가 없으므로 여기서도
        // `null`(우회 (4), 이름을 키로 싣지 않는다).
        agencyId = notice.demandAgency?.code?.let { AgencyId(it.value) },
        baseAmountProvenanceLabel =
            provenanceLabelFor(notice, null, resolvedBaseAmount.amount, policies.provenancePolicy),
        competitionSamples = competitionSamples,
        objective = policies.opportunity.objective,
        releaseSelector = policies.opportunity.releaseSelector,
        correlationId = correlationId,
    )

/**
 * 세 함수(`predictedFacts`·`absentPair`·`absentPairForUnavailableSupply`)가 공유하는
 * 성분 묶음(D-4D4-2) — 예전 `Pair<ScoreFact, ScoreFact>`에 [evidence]를
 * 더한다. `internal`인 이유는 `PredictionFactsTest` 헤더 KDoc과 같다 — `analyze()`
 * 전체를 거치면 `MlAnalysisOutcome.Analyzed`가 이 성분을 그대로 노출하지 않는다.
 */
internal data class PredictionComponents(
    val budgetCapture: ScoreFact<UnitScore>,
    val expectedMargin: ScoreFact<UnitScore>,
    val evidence: PredictionEvidence,
    /**
     * DEC-10 계수(D-DEC10-5·D-DEC10-8) — 게시 하한율이 **제외됐다는 사실**이다. 점수는
     * 「하한 미보고」와 같은 값으로 접히므로 [expectedMargin] 만 보면 제외가 보이지 않는다;
     * 그 칸이 침묵 스킵을 막는다. 제외가 없으면 `null` 이고 부재는 제외가 아니다.
     */
    val floorExclusion: PublishedFloorExclusion?,
)

/**
 * fitness 가 [0,1] 밖이거나 `candidates.base`(D-4B6B-6 로 `recommendedRate`·`predictedRate`
 * 둘 다 이 값이다)가 `MarginInputs.init`의 `≤ 1` 술어를 못 만족하면 예측 응답 전체를 못
 * 믿는 것으로 보고 두 성분 다 drop 한다 — `MarginInputs`를 짓기 **전에**
 * 그 생성자의 세 술어(recommendedRate·predictedRate·floorRate 각 `fraction ≤ 1`) 중
 * `candidates.base`에서 오는 둘을 여기서 막는다. `floorRate`는 `Notice`가 주는 별도 축이라
 * [expectedMarginFact]가 그 관문을 진다(D-4B6B-4 — floorRate 문제는 margin 만 drop).
 *
 * [excludedSamples]는 (D-4D4-7) — 호출자(`OpportunityAnalysis.predictionFacts`)가
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
    val margin = expectedMarginFact(notice, predicted.candidates.base, priceFitness, capacityScore, policies)
    val evidence = PredictionEvidence.Diagnosed(predicted.diagnostics, predicted.release, excludedSamples)
    return PredictionComponents(budgetCapture, margin.score, evidence, margin.floorExclusion)
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

/**
 * 기대 마진 성분과 **그 하한율이 제외됐는가**(r1 CR L-4 — 이름 있는 결과 타입, 위치 기반 쌍이
 * 아니다). 두 칸이 **함께** 나오는 것이 요점이다: 접힌 점수만 보면 제외가 보이지 않는다.
 */
private data class MarginFact(
    val score: ScoreFact<UnitScore>,
    val floorExclusion: PublishedFloorExclusion?,
)

/**
 * DEC-10 ③ — 게시 하한율이 **신뢰 게이트를 거쳐** 점수에 들어간다(D-DEC10-3).
 *
 * 통과하지 못한 값의 처분은 **둘로 갈린다**(D-DEC10-12, 2026-10-28 팀장 결정 — r1 v M-1):
 * - `fraction > 1` — margin **성분을 떨어뜨린다**(`Absent`). 사용자 승인 거동 D-4B6-4 이고
 *   이 slice 가 되돌리지 않는다. 그 값은 [MarginInputs] 의 불변식(`≤ 1`)으로 애초에 입력이
 *   될 수 없다 — 「접는다」가 성립하지 않는 범위다. **사유 있는 두 실패 가지 공통**이다
 *   (r2 CR M-2): base 의 필터가 origin 무관이었으므로 법정표 출처의 `> 1` 도 같은 처분이다.
 *   오늘 `StatutoryTable` 생성 자리가 0 이라 도달 불가지만, DEC-03 해석기가 생기는 날 조용히
 *   갈리지 않도록 술어를 가지 밖으로 올렸다.
 * - 그 밖의 개연 불가·심판 아님 — 「하한 미보고」와 **같은 값**(`floorRate = null`)으로 접힌다
 *   (DEC-10 acceptance 첫째). `1.00000`(R-FLOOR-03 라이브 47건)이 이 가지이고, 값·점수가
 *   앞 판과 같고 **계수만 생긴다**.
 *
 * **두 가지 모두 사유가 남는다**(acceptance 셋째) — 드롭이든 접기든 [MarginFact.floorExclusion]
 * 이 실린다. 그것이 「제외는 계수된다」와 D-4B6-4 를 동시에 세우는 자리다.
 *
 * 값을 고쳐 접어 올리지 않는다 — 접는 것은 **쓰지 않는다**는 뜻이고, 밴드 끝으로 clamp 하면
 * 지어낸 하한이 점수에 들어간다.
 */
private fun expectedMarginFact(
    notice: Notice,
    predictedRate: Rate,
    priceFitness: UnitScore,
    capacityScore: UnitScore,
    policies: ResolvedPolicies,
): MarginFact {
    val trust = trustPublishedFloorRate(notice.floorRate, policies.floorTrust)
    val exclusion = trust.exclusion()
    val excludedRate =
        when (trust) {
            is PublishedFloorTrust.Trusted -> null
            PublishedFloorTrust.Absent -> null
            is PublishedFloorTrust.Implausible -> trust.rate.rate
            is PublishedFloorTrust.OriginNotJudged -> trust.rate.rate
        }
    if (excludedRate != null && exceedsMarginInputBound(excludedRate)) {
        return MarginFact(ScoreFact.Absent(MlUnavailableReason.InvalidRequest), exclusion)
    }
    // D-DEC10-22 — 통과 값을 **그대로** 넘긴다. 이 자리에서 율을 꺼내지 않는 것이 요점이다:
    // 꺼내면 그 숫자가 다시 날 `Rate` 가 되어 세탁 경로가 열린다.
    val marginInputs =
        marginInputsFrom(
            recommendedRate = predictedRate,
            floorRate = (trust as? PublishedFloorTrust.Trusted)?.rate,
            predictedRate = predictedRate,
            priceFitness = priceFitness,
            capacity = capacityScore,
        )
    return MarginFact(ScoreFact.Present(deriveExpectedMargin(marginInputs, policies.derivation)), exclusion)
}

/**
 * [MarginInputs] 의 `floorRate ≤ 1` 불변식을 **그대로 되읽는** 술어다(D-DEC10-12). 정책 값이
 * 아니라 그 타입이 든 경계라 여기서 숫자를 정하지 않는다 — 그 `init` 이 바뀌면 이 자리도 함께
 * 틀리고, 그 사실은 `MarginInputs` KDoc 이 적는다.
 */
private fun exceedsMarginInputBound(rate: Rate): Boolean = rate.fraction > BigDecimal.ONE

/** (D-4D4-1) — 예측을 시도하지 않았거나 실패로 끝난 경우는 전부 `NotPredicted`다. */
internal fun absentPair(reason: MlUnavailableReason): PredictionComponents =
    PredictionComponents(
        ScoreFact.Absent(reason),
        ScoreFact.Absent(reason),
        PredictionEvidence.NotPredicted(reason),
        floorExclusion = null,
    )

/**
 * D-4B7-9 — 표본 공급 실패(`CompetitionSampleSupply.Unavailable`) 사유를
 * 그대로 예측 성분 드롭 사유로 옮긴다(예측 자체를 시도하지 않는다). `internal`로 뽑은
 * 이유는 `PredictionFactsTest.kt` 헤더 KDoc과 같다 — `analyze()` 전체를 거치면
 * `MlAnalysisOutcome.Analyzed`가 budgetCapture·expectedMargin 성분(과 그 드롭 사유)을
 * 노출하지 않아 D-4B7-9가 실제로 `ScoreNotProvided`로 뭉개지 않고 `supply.reason`을
 * 그대로 옮기는지를 `OpportunityAnalysisTest`(통합 층)에서는 잴 수 없다 — 같은 패키지
 * test가 이 함수를 직접 불러 잰다(같은 관례).
 */
internal fun absentPairForUnavailableSupply(supply: CompetitionSampleSupply.Unavailable): PredictionComponents =
    absentPair(supply.reason)
