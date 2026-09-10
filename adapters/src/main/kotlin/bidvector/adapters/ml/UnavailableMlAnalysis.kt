package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.procurement.Notice
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.event.CorrelationId

/**
 * 항상-미가용 [MlAnalysisPort] 구현(M4/4B-3 scope.md ⑤, 운영자 결정 2026-09-10 D-6 (b)) —
 * 실 점수 provider 부재 기간(2E·M5 진행 중, `OPEN-4D-LADDER-SOURCE` (a) 임시 상태 (c))의
 * 유일한 실 배선이다. `UnavailableMlAnalysisTest`가 이 배선으로 실제
 * `EvaluateCandidatesUseCase`를 돌려 「fail-safe 실물」을 증명한다.
 *
 * 상태·호출 계수를 두지 않는다(DI 관례상 인자 없는 class) — 값은 항상 같다.
 */
class UnavailableMlAnalysis : MlAnalysisPort {
    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome = MlAnalysisOutcome.Unavailable(MlUnavailableReason.ScoreNotProvided)
}
