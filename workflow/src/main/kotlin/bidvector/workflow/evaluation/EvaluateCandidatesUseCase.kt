package bidvector.workflow.evaluation

import bidvector.decision.LadderInput
import bidvector.decision.Verdict
import bidvector.decision.VerdictLadder
import bidvector.decision.VerdictLadderPolicyData
import bidvector.procurement.Notice
import bidvector.procurement.isBiddable
import bidvector.qualification.LicenseVerdict
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.PriorityScore
import bidvector.strategy.WatchVerdict
import bidvector.strategy.evaluate
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository

/**
 * 조합 use case(scope.md, M4 4B-2) — 열린 공고를 순회하며 각각을 판정(4B-1 사다리)에
 * 이르게 하거나, 이르지 못한 단계와 사유를 값으로 남긴다. **조합만 한다** — 수집·저장·
 * ML·알림 구현은 각각 port(scope.md ⑦, S-3b가 그 경계를 소스 스캔으로 단언).
 *
 * **판정은 공고당 한 번**(결정 5) — [VerdictLadder.judge] 호출 자리는 [reach] 안 정확히
 * 하나다. **전략은 진입에서 한 번**(결정 4) — [strategies].`load()`는 [evaluate] 안에서
 * 한 번만 불린다. **용량도 진입에서 한 번**(결정 10, 형태 ⑧) — [capacity].`snapshot()`도
 * 한 번만 불려 모든 후보가 같은 스냅샷을 공유한다(legacy처럼 스캔·영속 두 시점에 다시
 * 세지 않는다 — 애초에 두 번째로 셀 「영속 시점」이 이 slice에 없다).
 *
 * **catch-all이 없다**(결정 7) — 모든 port가 예외가 아니라 결과 갈래를 돌려주므로
 * `try`를 쓸 자리 자체가 없다. **후보 단위 격리**(결정 6) — [evaluate]는 `List.map`으로
 * 각 후보를 독립 평가한다. 한 후보의 port 실패가 다른 후보의 결과를 지우지 않는다 —
 * 공유 가변 상태·전역 rollback이 없다(legacy 실패 형태 ②의 뒤집기).
 *
 * `evaluateOne`은 각 단계가 [CandidateEvaluation.NotReached]를 내면 그 자리에서 그치는
 * guard 함수 체인(`?:` 연쇄, 4A `apply`·4B-1 `VerdictLadder.judge` 관례)으로 구성된다.
 *
 * **주 생성자는 `internal`이다(수정 라운드 2 H-1 시정).** `judge` 위임(아래)을 받는
 * 자리가 처음엔 `private val`이었으나 **생성자 매개변수는 그래도 공개 시그니처라**
 * 다른 모듈이 `judge = ...`로 넘겨 사다리를 후보와 무관한 입력·정책으로 몰 수 있었다
 * (verifier r2 실측 — 정직한 배선은 `Skip`·알림 0건인데 주입 배선은 `BidNow`·알림
 * 2건을 냈다). `Verdict.BidNow`는 위조를 막아도(4B-1) **정당한 값을 사다리 밖에서
 * 얻는 경로**가 열려 있었다는 뜻이다 — 그 값이 [NotificationRequest]의 `internal`
 * 생성자를 정직하게 지나 「판정 없이 알림을 요청했다」가 다른 문으로 성립했다. 이제
 * `judge`를 받는 이 생성자 자체가 `internal`이라 `workflow` 밖에서는 호출할 수
 * 없다 — 아래 public 보조 생성자만 밖에 남는다.
 */
class EvaluateCandidatesUseCase internal constructor(
    private val strategies: StrategyRepository,
    private val candidateSource: CandidateSourcePort,
    private val watchSubjects: WatchSubjectPort,
    private val licenseGate: LicenseGatePort,
    private val mlAnalysis: MlAnalysisPort,
    private val capacity: CapacityPort,
    private val notifications: NotificationRequestPort,
    private val correlationIds: CorrelationIdFactory,
    private val clock: Clock,
    private val ladderPolicySlot: LadderPolicySlot = EVALUATION_LADDER_POLICY_SLOT,
    /**
     * 분석 예산(D-10) — 이 run에서 ML 분석까지 실제로 도는 후보 수 상한. `null`이면
     * 무제한. legacy의 `break`(그 뒤 후보는 아예 안 본다)와 달리 이 use case는 예산을
     * 넘긴 후보도 [CandidateEvaluation.NotReached]로 **결과에 남긴다**(legacy 실패 형태
     * ⑥의 뒤집기 — 「평가하지 않았다」도 흔적 없이 사라지지 않는다). 순서는
     * [CandidateSourcePort.openCandidates]가 낸 목록 순서 그대로다.
     */
    private val analysisBudget: Int? = null,
    /**
     * 사다리 호출 위임(결정 5, verifier r1 L-1) — **`internal` 주 생성자를 통해서만
     * 닿는다**(수정 라운드 2 H-1). 실 배선(아래 public 보조 생성자)은 이 자리를 항상
     * [VerdictLadder.judge] 그대로 채운다 — 바꿀 수 없다. test만(같은 `workflow`
     * 모듈) 이 생성자를 직접 불러 계수 래퍼로 바꿀 수 있다. `reach` 안 정확히 한
     * 자리에서만 불린다는 사실은 이 위임이 있든 없든 같다 — 이 자리는 **셀 수 있게**
     * 하는 것이지 정상 배선의 호출 경로를 바꾸는 것이 아니다.
     */
    private val judge: (LadderInput, Resolution.Resolved<VerdictLadderPolicyData>) -> Verdict,
) {
    /** 실 배선(public) — `judge`는 항상 [VerdictLadder.judge]다(수정 라운드 2 H-1). */
    constructor(
        strategies: StrategyRepository,
        candidateSource: CandidateSourcePort,
        watchSubjects: WatchSubjectPort,
        licenseGate: LicenseGatePort,
        mlAnalysis: MlAnalysisPort,
        capacity: CapacityPort,
        notifications: NotificationRequestPort,
        correlationIds: CorrelationIdFactory,
        clock: Clock,
        ladderPolicySlot: LadderPolicySlot = EVALUATION_LADDER_POLICY_SLOT,
        analysisBudget: Int? = null,
    ) : this(
        strategies,
        candidateSource,
        watchSubjects,
        licenseGate,
        mlAnalysis,
        capacity,
        notifications,
        correlationIds,
        clock,
        ladderPolicySlot,
        analysisBudget,
        VerdictLadder::judge,
    )

    fun evaluate(): List<CandidateEvaluation> {
        val strategy = strategies.load()
        val candidates = candidateSource.openCandidates()
        val capacitySnapshot = capacity.snapshot()
        return candidates.mapIndexed { index, notice ->
            evaluateOne(index, notice, strategy, capacitySnapshot)
        }
    }

    private fun evaluateOne(
        index: Int,
        notice: Notice,
        strategy: OperatorStrategy,
        capacitySnapshot: CapacitySnapshot,
    ): CandidateEvaluation {
        val correlationId = correlationIds.newId()
        return analysisBudgetDrop(index, notice, correlationId)
            ?: lifecycleDrop(notice, correlationId)
            ?: watchGateDrop(notice, strategy, correlationId)
            ?: licenseGateDrop(notice, correlationId)
            ?: thresholdConfigurationDrop(strategy, notice, correlationId)
            ?: analyzeAndJudge(notice, strategy, capacitySnapshot, correlationId)
    }

    private fun analysisBudgetDrop(
        index: Int,
        notice: Notice,
        correlationId: CorrelationId,
    ): CandidateEvaluation.NotReached? =
        if (analysisBudget != null && index >= analysisBudget) {
            notReached(
                notice,
                correlationId,
                EvaluationStage.AnalysisBudget,
                EvaluationDropReason.AnalysisBudgetExhausted,
            )
        } else {
            null
        }

    private fun lifecycleDrop(
        notice: Notice,
        correlationId: CorrelationId,
    ): CandidateEvaluation.NotReached? {
        val deadline = notice.deadlineAt
        return if (deadline != null && isBiddable(notice.status, clock.now(), deadline)) {
            null
        } else {
            notReached(
                notice,
                correlationId,
                EvaluationStage.NoticeLifecycle,
                EvaluationDropReason.NoticeNotBiddable(notice.status),
            )
        }
    }

    private fun watchGateDrop(
        notice: Notice,
        strategy: OperatorStrategy,
        correlationId: CorrelationId,
    ): CandidateEvaluation.NotReached? {
        val subject =
            when (val outcome = watchSubjects.subjectFor(notice)) {
                is WatchSubjectOutcome.Unavailable -> {
                    return notReached(
                        notice,
                        correlationId,
                        EvaluationStage.WatchGate,
                        EvaluationDropReason.WatchSubjectUnavailable,
                    )
                }

                is WatchSubjectOutcome.Found -> {
                    outcome.subject
                }
            }
        return watchVerdictDrop(strategy.watchRules.evaluate(subject), notice, correlationId)
    }

    private fun licenseGateDrop(
        notice: Notice,
        correlationId: CorrelationId,
    ): CandidateEvaluation.NotReached? =
        when (val verdict = licenseGate.verdictFor(notice)) {
            is LicenseVerdict.Ineligible -> {
                notReached(
                    notice,
                    correlationId,
                    EvaluationStage.LicenseGate,
                    EvaluationDropReason.LicenseIneligible(verdict),
                )
            }

            // Uncertain은 미보유가 아니다(1C U-5) — 사다리로 넘어간다. Eligible도 통과.
            is LicenseVerdict.Eligible, is LicenseVerdict.Uncertain -> {
                null
            }
        }

    private fun thresholdConfigurationDrop(
        strategy: OperatorStrategy,
        notice: Notice,
        correlationId: CorrelationId,
    ): CandidateEvaluation.NotReached? =
        if (strategy.actionThresholds.bidNowThreshold != null && strategy.actionThresholds.reviewThreshold != null) {
            null
        } else {
            notReached(
                notice,
                correlationId,
                EvaluationStage.ThresholdConfiguration,
                EvaluationDropReason.ActionThresholdsNotConfigured,
            )
        }

    /** [thresholdConfigurationDrop]이 이미 둘 다 non-null임을 확인한 뒤에만 불린다. */
    private fun analyzeAndJudge(
        notice: Notice,
        strategy: OperatorStrategy,
        capacitySnapshot: CapacitySnapshot,
        correlationId: CorrelationId,
    ): CandidateEvaluation {
        val bidNowThreshold = requireNotNull(strategy.actionThresholds.bidNowThreshold)
        val reviewThreshold = requireNotNull(strategy.actionThresholds.reviewThreshold)
        return when (val outcome = mlAnalysis.analyze(notice, correlationId)) {
            is MlAnalysisOutcome.SimilarityProjectionNotReady -> {
                notReached(
                    notice,
                    correlationId,
                    EvaluationStage.MlAvailability,
                    EvaluationDropReason.SimilarityProjectionNotReady,
                )
            }

            is MlAnalysisOutcome.Analyzed -> {
                scoreThresholdDrop(strategy, outcome, notice, correlationId)
                    ?: reach(notice, capacitySnapshot, correlationId, bidNowThreshold, reviewThreshold, outcome)
            }
        }
    }

    private fun scoreThresholdDrop(
        strategy: OperatorStrategy,
        analysis: MlAnalysisOutcome.Analyzed,
        notice: Notice,
        correlationId: CorrelationId,
    ): CandidateEvaluation.NotReached? {
        val minimumMatch =
            strategy.actionThresholds.minimumMatchScore
                ?.score
                ?.value
        val actualMatch = analysis.matchedScore?.value
        if (minimumMatch != null && actualMatch != null && actualMatch < minimumMatch) {
            return notReached(
                notice,
                correlationId,
                EvaluationStage.ScoreThreshold,
                EvaluationDropReason.BelowMinimumMatchScore(minimumMatch, actualMatch),
            )
        }
        val minimumProbability =
            strategy.actionThresholds.minimumProbabilityScore
                ?.score
                ?.value
        val actualProbability = analysis.probabilityScore?.value
        return if (minimumProbability != null && actualProbability != null && actualProbability < minimumProbability) {
            notReached(
                notice,
                correlationId,
                EvaluationStage.ScoreThreshold,
                EvaluationDropReason.BelowMinimumProbabilityScore(minimumProbability, actualProbability),
            )
        } else {
            null
        }
    }

    /** 판정은 정확히 이 한 자리에서만 돈다(결정 5) — 두 번째 호출 경로가 이 클래스에 없다. */
    private fun reach(
        notice: Notice,
        capacitySnapshot: CapacitySnapshot,
        correlationId: CorrelationId,
        bidNowThreshold: PriorityScore,
        reviewThreshold: PriorityScore,
        analysis: MlAnalysisOutcome.Analyzed,
    ): CandidateEvaluation.Reached {
        val ladderInput =
            LadderInput(
                priorityScore = analysis.priorityScore,
                probabilityScore = analysis.probabilityScore,
                matchedScore = analysis.matchedScore,
                currentActiveBids = capacitySnapshot.currentActiveBids,
                maxActiveBids = capacitySnapshot.maxActiveBids,
            )
        val ladderPolicy =
            Resolution.Resolved(
                VerdictLadderPolicyData(
                    capacityHoldPriorityThreshold = ladderPolicySlot.capacityHoldPriorityThreshold,
                    bidNowThreshold = bidNowThreshold.score.value,
                    reviewThreshold = reviewThreshold.score.value,
                    forceBidProbabilityThreshold = ladderPolicySlot.forceBidProbabilityThreshold,
                    forceBidMatchedThreshold = ladderPolicySlot.forceBidMatchedThreshold,
                ),
                PolicyVersion(EffectiveFrom.Initial, "m4-4b2-legacy-behavior-2026-09-09"),
            )
        val verdict = judge(ladderInput, ladderPolicy)
        if (verdict is Verdict.BidNow) {
            notifications.request(NotificationRequest(notice.id, correlationId, verdict))
        }
        return CandidateEvaluation.Reached(notice.id, correlationId, verdict)
    }
}

private fun notReached(
    notice: Notice,
    correlationId: CorrelationId,
    stage: EvaluationStage,
    reason: EvaluationDropReason,
): CandidateEvaluation.NotReached = CandidateEvaluation.NotReached(notice.id, correlationId, stage, reason)

/**
 * [WatchVerdict]가 통과가 아닌 갈래를 [CandidateEvaluation.NotReached]로 옮긴다
 * (`EvaluateCandidatesUseCase.watchGateDrop`에서 분리 — 클래스당 함수 11개 한도,
 * v2-지침서 §5, detekt `TooManyFunctions`). 인스턴스 상태가 필요 없어 top-level로 뺐다.
 * **운영자 결정 2026-09-10(수정 라운드 1 M-1) — `NoGate`는 이제 통과가 아니다.**
 * legacy는 이 상태에서 스캔 자체를 하지 않았다(`_has_configured_watch_rules`
 * 게이트) — 결과(후보 0)를 그대로 두고 탈락만 값으로 남긴다.
 */
private fun watchVerdictDrop(
    verdict: WatchVerdict,
    notice: Notice,
    correlationId: CorrelationId,
): CandidateEvaluation.NotReached? =
    when (verdict) {
        is WatchVerdict.Rejected -> {
            notReached(
                notice,
                correlationId,
                EvaluationStage.WatchGate,
                EvaluationDropReason.WatchGateRejected(verdict),
            )
        }

        is WatchVerdict.Undeterminable -> {
            notReached(
                notice,
                correlationId,
                EvaluationStage.WatchGate,
                EvaluationDropReason.WatchGateUndeterminable(verdict),
            )
        }

        is WatchVerdict.NoGate -> {
            notReached(
                notice,
                correlationId,
                EvaluationStage.WatchGate,
                EvaluationDropReason.WatchGateNotConfigured(verdict),
            )
        }

        is WatchVerdict.Passed -> {
            null
        }
    }
