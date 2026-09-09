package bidvector.workflow.evaluation

/**
 * 후보가 판정 사다리(4B-1)에 이르지 못했을 때, 어느 단계에서 멈췄는지(scope.md ⑥,
 * 설계 검토 (4) 1 — 「단계 + 사유」). [CandidateEvaluation.NotReached]가 [EvaluationDropReason]
 * 과 함께 싣는다 — 사유 하나는 항상 정확히 한 단계에 속한다(각 [EvaluationDropReason]
 * variant는 자신이 발생하는 단계가 정해져 있다, `EvaluateCandidatesUseCase` 참고).
 */
sealed interface EvaluationStage {
    /** 공고 상태가 입찰 가능(`Open`/`Renoticed`이고 마감 전)이 아니었다(D-2). */
    data object NoticeLifecycle : EvaluationStage

    /** 감시 필드 일곱(1E `WatchRules`) 판정에서 걸렸다(D-1·D-3~D-8). */
    data object WatchGate : EvaluationStage

    /** 보유 면허로 자격이 미달했다(1C, D-9). */
    data object LicenseGate : EvaluationStage

    /** 운영자 전략에 사다리를 돌릴 임계가 설정돼 있지 않았다(신설). */
    data object ThresholdConfiguration : EvaluationStage

    /** 분석 예산이 소진돼 이 공고를 평가하지 않았다(D-10). */
    data object AnalysisBudget : EvaluationStage

    /** ML 분석 입력(유사도 projection 등)이 아직 준비되지 않았다(D-11). */
    data object MlAvailability : EvaluationStage

    /** 적합도·확률 점수가 운영자 최소치 미만이었다(D-12·D-13). */
    data object ScoreThreshold : EvaluationStage
}
