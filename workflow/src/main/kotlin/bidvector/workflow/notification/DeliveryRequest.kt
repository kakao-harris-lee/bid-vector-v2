package bidvector.workflow.notification

import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.strategy.OperatorId

/** 렌더된 내용을 가리키는 참조(scope.md ①) — 요청은 문장을 담지 않는다. */
data class ContentRef(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ContentRef는 빈 문자열일 수 없다" }
    }
}

/** 4B가 만드는 통지 의도(scope.md ①, D-4E-8) — 「무엇을 어디로」만 갖는다(가치 판정은 4B 소유). */
data class NotificationIntent(
    val idempotencyKey: IdempotencyKey,
    val owner: OperatorId,
    val channel: Channel,
    val contentRef: ContentRef,
)

/**
 * sender·renderer 양쪽에 넘기는 배달 요청(scope.md ①, 설계 검토 (2)) — 유일한 생성 경로는
 * [planDelivery](`internal`, `DispatchNotification.kt`). 필드는 전부 값 타입 — 자유
 * `String` 필드 0(설계 검토 (1) 「사유를 문자열로」 방지와 같은 축).
 */
@ConsistentCopyVisibility
data class DeliveryRequest internal constructor(
    val idempotencyKey: IdempotencyKey,
    val owner: OperatorId,
    val channel: Channel,
    val route: RouteKey,
    val contentRef: ContentRef,
)
