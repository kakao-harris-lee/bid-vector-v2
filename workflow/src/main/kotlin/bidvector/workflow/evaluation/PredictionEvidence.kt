package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.workflow.prediction.ModelReleaseRef
import bidvector.workflow.prediction.PredictionDiagnostics

/**
 * 판정 근거 캐리어(scope.md D-4D4-1, `OPEN-4D3-DIAGNOSTICS-RENDER` 닫힘) — `decision`
 * 모듈을 우회해(`decision`은 `shared-kernel`만 의존) `workflow` 안에서 예측 진단을
 * `Analyzed`·`NotificationRequest`까지 나른다. 예측을 시도하지 않았거나(기초금액 없음·
 * 표본 공급 `Unavailable`) 시도가 `Unavailable`·`Unmeasurable`·`ContractViolation`으로
 * 끝난 경우가 전부 [NotPredicted]다 — 「분석됐는데 근거가 없다」는 상태가 없다.
 */
sealed interface PredictionEvidence {
    /**
     * 예측이 성공해 진단을 얻은 경우. [excludedSamples]는 [CompetitionSampleSupply.Supplied.excluded]
     * 를 그대로 옮긴다(D-4D4-7, D-4B7-9 실효) — 여기서 계수를 다시 세지 않는다.
     */
    data class Diagnosed(
        val diagnostics: PredictionDiagnostics,
        val release: ModelReleaseRef,
        val excludedSamples: Map<SampleExclusionReason, Int>,
    ) : PredictionEvidence

    /** 예측을 시도하지 않았거나 시도가 실패로 끝난 경우 — 사유는 어댑터·상위 단계가 실은 값 그대로. */
    data class NotPredicted(
        val reason: MlUnavailableReason,
    ) : PredictionEvidence
}
