package bidvector.workflow.embedding

/**
 * `EmbedTextPort.embed`가 답을 얻지 못한 사유(scope.md ⑤, D-4D2-3) — 채널 부재·deadline
 * 초과·breaker open·검증 실패·`ApplicationFailure` 각각이 다른 사실이다(운영이 다르게
 * 대응해야 한다). decision 모듈의 `MlUnavailableReason`을 재사용하지 않는다 — 그 모듈
 * 전체를 이 slice가 소비하지 않는다(scope.md out_of_scope, D-4D2-1). 값 이름은
 * `MlUnavailableReason`과 의도적으로 같게 맞춘다(같은 개념 축의 두 번째 실물, 4D-1 관례
 * 계승) — `ScoreNotProvided`(decision 전용 fail-safe 배선)는 이 축에 없다.
 */
sealed interface EmbeddingUnavailableReason {
    data object CircuitOpen : EmbeddingUnavailableReason

    data object DeadlineExceeded : EmbeddingUnavailableReason

    data object RetryBudgetExhausted : EmbeddingUnavailableReason

    data object TransportFailed : EmbeddingUnavailableReason

    data object ReleaseMismatch : EmbeddingUnavailableReason

    data object ContractViolation : EmbeddingUnavailableReason

    data object UnsupportedSchema : EmbeddingUnavailableReason

    data object UnsupportedRelease : EmbeddingUnavailableReason

    data object InvalidRequest : EmbeddingUnavailableReason

    data object ModelNotReady : EmbeddingUnavailableReason
}
