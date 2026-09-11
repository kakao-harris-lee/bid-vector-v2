package bidvector.workflow.notification

/**
 * 환경이 배달을 어떻게 다루는지(scope.md ⑥⑦) — 채널 무관. [RuntimeEnvironment] →
 * [DeliveryMode] 전사상은 정책 데이터([NotificationDeliveryPolicyData])가 갖는다.
 * `Blocked`가 어느 환경 때문인지는 이 타입이 아니라 [SuppressionReason.EnvironmentBlocked]
 * 가 나른다(정책/환경 축 분리, 설계 검토 (1)).
 */
sealed interface DeliveryMode {
    data object Live : DeliveryMode

    data object DryRun : DeliveryMode

    data object Blocked : DeliveryMode
}
