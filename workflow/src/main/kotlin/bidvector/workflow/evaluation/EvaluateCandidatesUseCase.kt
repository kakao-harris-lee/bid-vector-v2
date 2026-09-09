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
 */
class EvaluateCandidatesUseCase(
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
) {
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
        return when (val verdict = strategy.watchRules.evaluate(subject)) {
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

            // NoGate(규칙 없음)·Passed는 둘 다 통과다 — 감시 게이트가 이 후보를 막지 않는다.
            is WatchVerdict.NoGate, is WatchVerdict.Passed -> {
                null
            }
        }
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
        return when (val outcome = mlAnalysis.analyze(notice)) {
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
        val verdict = VerdictLadder.judge(ladderInput, ladderPolicy)
        if (verdict is Verdict.BidNow) {
            notifications.request(NotificationRequest(notice.id, correlationId, verdict))
        }
        return CandidateEvaluation.Reached(notice.id, correlationId, verdict)
    }

    private fun notReached(
        notice: Notice,
        correlationId: CorrelationId,
        stage: EvaluationStage,
        reason: EvaluationDropReason,
    ): CandidateEvaluation.NotReached = CandidateEvaluation.NotReached(notice.id, correlationId, stage, reason)
}
