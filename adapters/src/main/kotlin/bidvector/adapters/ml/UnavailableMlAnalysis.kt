package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.procurement.Notice
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.event.CorrelationId

/**
 * 항상-미가용 [MlAnalysisPort] 구현(M4/4B-3 scope.md ⑤, 운영자 결정 2026-09-10 D-6 (b)) —
 * 실 점수 provider 부재 기간(2E·M5 진행 중, `OPEN-4D-LADDER-SOURCE` (a) 임시 상태 (c))에
 * **앱이 배선해야 할 [MlAnalysisPort]의 유일한 production 구현**이다(verifier r1 L-2 —
 * 「유일한 실 배선」이 「앱이 이미 이 경로로 돈다」로 읽히지 않도록 정정). **조립
 * 루트(`EvaluateCandidatesUseCase`에 실제로 꽂는 자리)는 이 slice 범위 밖이다** — M6/`app`
 * 이 배선한다. `UnavailableMlAnalysisTest`가 이 구현으로 실제 `EvaluateCandidatesUseCase`
 * 를 직접 돌려 「fail-safe 실물」을 증명하지만, 그 test 자신이 조립 지점이지 `app`의 실제
 * 배선이 아니다.
 *
 * 상태·호출 계수를 두지 않는다(DI 관례상 인자 없는 class) — 값은 항상 같다.
 */
class UnavailableMlAnalysis : MlAnalysisPort {
    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome = MlAnalysisOutcome.Unavailable(MlUnavailableReason.ScoreNotProvided)
}
