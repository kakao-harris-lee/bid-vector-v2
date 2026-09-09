package bidvector.workflow.notification

import bidvector.workflow.strategy.OperatorId

/**
 * 배달 경로 조회 port(scope.md ③, 설계 검토 (1)) — 구현은 이 slice 밖(어댑터). dispatch의
 * 유일한 route 획득 경로가 이 함수라 API 표면에 「임의 owner의 route」가 없다(우회 없음 —
 * owner isolation의 방어선은 API 표면 자체).
 */
interface RouteDirectory {
    fun routesFor(owner: OperatorId): Map<Channel, ChannelRoute>
}

/** 채널 하나의 배달 경로 사실(scope.md ②) — `enabled`(채널 활성)과 `key`(route 유무)는 다른 축이다. */
data class ChannelRoute(
    val enabled: Boolean,
    val key: RouteKey?,
)

/** 렌더러 port(scope.md ①) — 구현(내용의 옳음)은 이 slice 밖(비방어, 위협 모델). */
interface ContentRenderer {
    fun render(
        contentRef: ContentRef,
        channel: Channel,
    ): RenderedContent
}

/**
 * 배달 sender port(scope.md ④⑤⑥) — 같은 [DeliveryRequest.idempotencyKey] 재호출은 효과
 * 0(앞 결과를 그대로 돌려준다)이어야 한다. **실 어댑터는 이 계약을 [SenderContractTest]
 * 와 같은 골격의 test로 증명해야 한다** — 실 sender가 없어 이 계약은 fake로만 증명되고
 * (위협 모델 (0)), 실 어댑터 slice의 verifier가 계승한다(알려진 제한).
 */
interface NotificationSender {
    fun send(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult
}
