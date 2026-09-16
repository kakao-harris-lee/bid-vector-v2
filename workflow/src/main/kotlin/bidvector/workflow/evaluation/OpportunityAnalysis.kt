package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.ProvenancePolicyData
import bidvector.decision.UnitScore
import bidvector.decision.priority.PRIORITY_POLICY
import bidvector.decision.priority.PriorityInputs
import bidvector.decision.priority.PriorityPolicyData
import bidvector.decision.priority.ScoreFact
import bidvector.decision.priority.composePriority
import bidvector.decision.priority.derive.ComplexityInputs
import bidvector.decision.priority.derive.DERIVATION_POLICY
import bidvector.decision.priority.derive.DerivationPolicyData
import bidvector.decision.priority.derive.competitivenessNotCollected
import bidvector.decision.priority.derive.deriveExecutionComplexity
import bidvector.decision.priority.derive.deriveUrgency
import bidvector.procurement.Notice
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.strategy.WatchSubject
import bidvector.workflow.embedding.EmbedTextPort
import bidvector.workflow.embedding.EmbedTextRequest
import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.BidPredictionPort
import bidvector.workflow.prediction.CallBudget
import bidvector.workflow.strategy.Clock
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * opportunity 조합기(scope.md ①) — fact(port 결과) → 값 → 4B-4·4B-5 순수 커널의 배선.
 * 판정(사다리)은 하지 않는다([EvaluateCandidatesUseCase] 소관) — 이 타입은
 * `analyze(notice, correlationId)` 하나로 [MlAnalysisOutcome]만 낸다.
 *
 * 주 생성자는 `internal`(정책 슬롯 셋을 test가 교체할 수 있게) — 판정 함수·정책을 밖에서
 * 주입하지 않는다(위협 모델 방어 (f), (2b) 값 획득 축). 공개 경로는 보조 생성자
 * (port 여섯 + [Clock])뿐이고, 정책은 출하 정본([OPPORTUNITY_POLICY]·[DERIVATION_POLICY]·
 * [PRIORITY_POLICY])으로 고정된다.
 *
 * `analyze`의 순서 고정 guard 체인(scope.md ②)은 [Step]/[andThen]으로 조립한다 — 사다리꼴
 * 중첩 `if(...)return` 대신 각 단계를 독립 함수로 나눠 실패를 [Step.Halt]로 값으로 옮긴다
 * (v2-지침서 §5 분기 도배 금지 — 반복되는 「검사 후 즉시 반환」 형태를 한 조합자로 통일).
 * 순수 변환 단계(`synthesizeTexts`·`combineEmbeddings`·`matchScoreStep`·`deriveLoadRatioStep`)는
 * `OpportunityAnalysisPipeline.kt`, 예측·마진·budgetCapture 파생은 `PredictionFacts.kt`로
 * 뺐다 — 한 파일 top-level 함수 11개 한도(`TooManyFunctions`)를 이 조합기 하나가 넘어서,
 * port 를 읽는 단계(이 class)·순수 파이프라인 변환·예측 파생 셋으로 가른다(기계적 분할이
 * 아니라 세 책임의 실제 경계).
 */
class OpportunityAnalysis internal constructor(
    private val embed: EmbedTextPort,
    private val prediction: BidPredictionPort,
    private val profile: OperatorProfilePort,
    private val workload: WorkloadPort,
    private val watchSubjects: WatchSubjectPort,
    private val capacity: CapacityPort,
    // M4/4B-7(D-4B7-9) — 경쟁 표본 조회 port. 표본 조회는 예측 요청 조립의 일부다(predictionFacts).
    private val samples: CompetitionSamplePort,
    private val clock: Clock,
    private val opportunityPolicyTable: EffectiveDatedPolicy<OpportunityPolicyData>,
    private val derivationPolicyTable: EffectiveDatedPolicy<DerivationPolicyData>,
    private val priorityPolicyTable: EffectiveDatedPolicy<PriorityPolicyData>,
) : MlAnalysisPort {
    constructor(
        embed: EmbedTextPort,
        prediction: BidPredictionPort,
        profile: OperatorProfilePort,
        workload: WorkloadPort,
        watchSubjects: WatchSubjectPort,
        capacity: CapacityPort,
        samples: CompetitionSamplePort,
        clock: Clock,
    ) : this(
        embed,
        prediction,
        profile,
        workload,
        watchSubjects,
        capacity,
        samples,
        clock,
        OPPORTUNITY_POLICY,
        DERIVATION_POLICY,
        PRIORITY_POLICY,
    )

    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome {
        val referenceDate = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC)
        return when (val result = runPipeline(notice, correlationId, referenceDate)) {
            is Step.Ok -> result.value
            is Step.Halt -> result.outcome
        }
    }

    /** scope.md ② 순서 고정 guard 체인 — 한 단계라도 [Step.Halt] 면 이후 단계를 부르지 않는다. */
    private suspend fun runPipeline(
        notice: Notice,
        correlationId: CorrelationId,
        referenceDate: LocalDate,
    ): Step<MlAnalysisOutcome> =
        resolvePolicies(referenceDate).andThen { policies ->
            findSubject(notice).andThen { subject ->
                findProfile().andThen { profileFacts ->
                    synthesizeTexts(subject, profileFacts, policies.opportunity).andThen { texts ->
                        embedPairStep(texts, policies, correlationId).andThen { vectors ->
                            matchScoreStep(vectors, policies).andThen { matchScore ->
                                finalizeAnalysis(notice, subject, matchScore, policies, correlationId)
                            }
                        }
                    }
                }
            }
        }

    /**
     * M4/4B-8(D-4B8-5) — `SAMPLE_PROVENANCE_POLICY`도 이 자리에서 함께 resolve한다.
     * `predictionRequestFor`(`PredictionFacts.kt`, port를 읽지 않는 파일)가 `Clock`을 직접
     * 읽지 않고도 대상 공고 라벨에 쓸 정책을 받도록 — 표본 라벨(`JdbcCompetitionSampleSource`)과
     * 대상 라벨이 같은 정책 singleton을 참조한다(대상·표본이 같은 version으로 판정).
     */
    private fun resolvePolicies(referenceDate: LocalDate): Step<ResolvedPolicies> {
        val opportunity = resolvedOrNull(opportunityPolicyTable, referenceDate)
        val derivation = resolvedOrNull(derivationPolicyTable, referenceDate)
        val priority = resolvedOrNull(priorityPolicyTable, referenceDate)
        val provenance = resolvedOrNull(SAMPLE_PROVENANCE_POLICY, referenceDate)
        // detekt ComplexCondition(≤3) — 네 slot 을 한 `&&` 사슬로 묶지 않고 중첩한다(스마트캐스트
        // 보존 — 중간 Boolean 변수로 쪼개면 opportunity 등이 다시 nullable 로 보인다).
        return if (opportunity != null && derivation != null && priority != null) {
            if (provenance != null) {
                ok(ResolvedPolicies(opportunity.value, opportunity.version, derivation, priority.value, provenance))
            } else {
                halt(MlUnavailableReason.ContractViolation)
            }
        } else {
            halt(MlUnavailableReason.ContractViolation)
        }
    }

    private fun findSubject(notice: Notice): Step<WatchSubject> =
        when (val outcome = watchSubjects.subjectFor(notice)) {
            is WatchSubjectOutcome.Found -> ok(outcome.subject)
            WatchSubjectOutcome.Unavailable -> halt(MlUnavailableReason.ScoreNotProvided)
        }

    private fun findProfile(): Step<ProfileFacts> =
        profile.current()?.let(::ok) ?: halt(MlUnavailableReason.ScoreNotProvided)

    /**
     * scope.md ②(4) — notice 를 먼저 부르고, 그 응답이 이미 `Unavailable` 이면 profile 은
     * 부르지 않는다(verifier r1 F-4 — 실패가 확정된 뒤 원격 호출·예산을 더 쓰지 않는다).
     */
    private suspend fun embedPairStep(
        texts: SynthesizedTexts,
        policies: ResolvedPolicies,
        correlationId: CorrelationId,
    ): Step<EmbeddedVectors> {
        val noticeOutcome = embedOne(texts.notice, policies.opportunity, correlationId)
        if (noticeOutcome is EmbeddingOutcome.Unavailable) {
            return halt(bridgeEmbeddingReason(noticeOutcome.reason))
        }
        val profileOutcome = embedOne(texts.profile, policies.opportunity, correlationId)
        return combineEmbeddings(noticeOutcome, profileOutcome, policies.priority.normEpsilon)
    }

    private suspend fun embedOne(
        text: SynthesizedText,
        opportunity: OpportunityPolicyData,
        correlationId: CorrelationId,
    ): EmbeddingOutcome {
        val request =
            EmbedTextRequest(
                text = text.value,
                kind = text.kind,
                releaseSelector = opportunity.releaseSelector,
                correlationId = correlationId,
            )
        return embed.embed(request, CallBudget(opportunity.embeddingBudget))
    }

    private suspend fun finalizeAnalysis(
        notice: Notice,
        subject: WatchSubject,
        matchScore: UnitScore,
        policies: ResolvedPolicies,
        correlationId: CorrelationId,
    ): Step<MlAnalysisOutcome> =
        deriveLoadRatioStep(capacity.snapshot()).andThen { loadRatio ->
            val capacityScore = UnitScore(BigDecimal.ONE.subtract(loadRatio.value))
            val remaining = notice.deadlineAt?.let { deadline -> Duration.between(clock.now(), deadline) }
            composeOutcome(notice, subject, matchScore, remaining, loadRatio, capacityScore, policies, correlationId)
        }

    /** scope.md ⑦⑧ — 파생·예측·penalty 를 [PriorityInputs] 로 모아 [composePriority] 를 부른다. */
    private suspend fun composeOutcome(
        notice: Notice,
        subject: WatchSubject,
        matchScore: UnitScore,
        remaining: Duration?,
        loadRatio: UnitScore,
        capacityScore: UnitScore,
        policies: ResolvedPolicies,
        correlationId: CorrelationId,
    ): Step<MlAnalysisOutcome> {
        val components = predictionFacts(notice, policies, capacityScore, correlationId)
        val complexityInputs =
            ComplexityInputs(
                budget = notice.baseAmount?.amount,
                keywordHits = KeywordHitsCounter.count(subject.fullText, policies.opportunity),
                remaining = remaining,
                loadRatio = loadRatio,
                match = matchScore,
                capacity = capacityScore,
            )
        val complexityOutcome = deriveExecutionComplexity(complexityInputs, policies.derivation)
        val priorityInputs =
            PriorityInputs(
                match = ScoreFact.Present(matchScore),
                urgency = deriveUrgency(remaining, policies.derivation).toScoreFact(),
                competitiveness = competitivenessNotCollected().toScoreFact(),
                budgetCapture = components.budgetCapture,
                expectedMargin = components.expectedMargin,
                loadRatio = ScoreFact.Present(loadRatio),
                workload = workload.current().toScoreFact(),
                complexity = ScoreFact.Present(complexityOutcome.score),
            )
        return ok(finalOutcomeOf(composePriority(priorityInputs, policies.priority), matchScore, components.evidence))
    }

    /** scope.md ⑥ — `notice.baseAmount`가 없으면 예측 자체를 생략한다(D-4B6B-4). */
    private suspend fun predictionFacts(
        notice: Notice,
        policies: ResolvedPolicies,
        capacityScore: UnitScore,
        correlationId: CorrelationId,
    ): PredictionComponents {
        val resolvedBaseAmount = notice.baseAmount ?: return absentPair(MlUnavailableReason.ScoreNotProvided)
        // detekt ReturnCount(≤2) — 표본 공급 Unavailable 분기를 예측 호출 분기와 같은 when 안에
        // 스마트캐스트로 접는다(M4/4B-7, 두 번째이자 마지막 return).
        return when (val supply = competitionSampleSupplyFor(notice, policies)) {
            is CompetitionSampleSupply.Unavailable -> {
                absentPairForUnavailableSupply(supply)
            }

            is CompetitionSampleSupply.Supplied -> {
                val baseAmount = resolvedBaseAmount.amount
                val request =
                    predictionRequestFor(resolvedBaseAmount, notice, policies, correlationId, supply.samples)
                val budget = CallBudget(policies.opportunity.predictionBudget)
                when (val outcome = prediction.predict(request, budget)) {
                    is BidPredictionOutcome.Unavailable -> {
                        absentPair(outcome.reason)
                    }

                    is BidPredictionOutcome.Unmeasurable -> {
                        absentPair(MlUnavailableReason.ScoreNotProvided)
                    }

                    is BidPredictionOutcome.Predicted -> {
                        predictedFacts(outcome, baseAmount, notice, policies, capacityScore, supply.excluded)
                    }
                }
            }
        }
    }

    /**
     * M4/4B-7(D-4B7-3·9) — 대상 공고에 공종이 없으면 조회 자체를 하지 않고 표본 0건으로
     * 접는다(값을 지어내지 않는다 — 조회 축[CompetitionSampleQuery.categoryCode]이 필수라
     * 지을 값이 없다). 공종이 있으면 port로 넘긴다 — 자격 판정은 port 구현(어댑터)이
     * `SampleEligibility`를 불러 진다(D-4B7-4).
     */
    private fun competitionSampleSupplyFor(
        notice: Notice,
        policies: ResolvedPolicies,
    ): CompetitionSampleSupply {
        val categoryCode =
            notice.businessCategory?.code
                ?: return CompetitionSampleSupply.Supplied(samples = emptyList(), excluded = emptyMap())
        val query =
            CompetitionSampleQuery(
                categoryCode = categoryCode,
                excludeNoticeId = notice.id,
                asOf = clock.now(),
                windowDays = policies.opportunity.sampleWindowDays,
                limit = policies.opportunity.maxSamples,
            )
        return samples.samplesFor(query)
    }
}

/**
 * [OpportunityAnalysis.analyze] guard 체인의 중간 상태(internal — `OpportunityAnalysisPipeline.kt`
 * 도 이 타입으로 낸다) — 성공은 값을, 실패는 최종 결과를 나른다.
 */
internal sealed interface Step<out T> {
    data class Ok<T>(
        val value: T,
    ) : Step<T>

    data class Halt(
        val outcome: MlAnalysisOutcome,
    ) : Step<Nothing>
}

internal fun <T> ok(value: T): Step<T> = Step.Ok(value)

internal fun halt(reason: MlUnavailableReason): Step<Nothing> = Step.Halt(MlAnalysisOutcome.Unavailable(reason))

private suspend fun <T, R> Step<T>.andThen(f: suspend (T) -> Step<R>): Step<R> =
    when (this) {
        is Step.Ok -> f(value)
        is Step.Halt -> this
    }

/**
 * 세 파일이 공유하는 정책 스냅샷(internal) — 매 `analyze` 호출마다 한 번만 resolve 한다.
 * `provenancePolicy`는 M4/4B-8(D-4B8-5) — 대상 공고 라벨(`predictionRequestFor`)이 표본
 * 라벨(`sampleOf`)과 같은 `SAMPLE_PROVENANCE_POLICY` singleton·같은 version을 쓴다.
 */
internal data class ResolvedPolicies(
    val opportunity: OpportunityPolicyData,
    val opportunityVersion: PolicyVersion,
    val derivation: Resolution.Resolved<DerivationPolicyData>,
    val priority: PriorityPolicyData,
    val provenancePolicy: Resolution.Resolved<ProvenancePolicyData>,
)

private fun <T> resolvedOrNull(
    policy: EffectiveDatedPolicy<T>,
    referenceDate: LocalDate,
): Resolution.Resolved<T>? =
    when (val resolution = policy.resolve(referenceDate)) {
        is Resolution.Resolved -> resolution
        is Resolution.NotApplicable -> null
    }
