package bidvector.adapters.relay

import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ChannelRoute
import bidvector.workflow.notification.ContentRef
import bidvector.workflow.notification.ContentRenderer
import bidvector.workflow.notification.DeliveryRequest
import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.NotificationSender
import bidvector.workflow.notification.RenderedContent
import bidvector.workflow.notification.RouteDirectory
import bidvector.workflow.strategy.OperatorId

/**
 * 발송 축 셋의 **자리지킴**(D-6F10-18 ⑥, `OPEN-STR-12`) — 실 채널·렌더링·라우팅은 이 slice
 * 밖이다. 선례 `bidvector.adapters.ml.UnavailableMlAnalysis` 와 같은 처분이지만 **반대
 * 방향**이다: 그쪽은 「미가용」을 **값**으로 돌려줘 판정이 계속 가게 하고, 이쪽은 **던진다**.
 *
 * 왜 던지는가. 「미가용」을 값으로 돌려주면 relay 는 그것을 `Rejected` 또는 `Unknown` 으로
 * 읽어 행을 `FAILED`/`ISOLATED` 로 **태운다** — 종단 전이는 단방향이고 되돌릴 간선이 없으므로
 * 매 run 이 행을 영구히 잃는다(설계 검토 (3) 이 A-5 (c)를 불채택한 이유). 던지면 run 이
 * 거기서 멈추고 행은 `CLAIMED` 에 남아 다음 run 의 고아 격리가 받는다 — 그래도 잃지만,
 * **그 전에 도달 자체를 막는 층이 둘** 있다: ① `RelayWiring` 이 `Production` 환경 설정을
 * 기동 거부한다 ② 그 밖의 환경은 `Live` 가 아니라 relay 가 claim 조차 하지 않는다. 즉 이
 * 세 클래스의 본문은 **배선이 뚫렸을 때만** 도달하고, 그때 조용히 틀리는 쪽보다 멈추는 쪽이
 * 정직하다.
 *
 * 실 sender 가 들어오면 이 셋과 `RelayWiring` 의 `Production` 거부가 함께 사라진다.
 */
class UnavailableRouteDirectory : RouteDirectory {
    override fun routesFor(owner: OperatorId): Map<Channel, ChannelRoute> =
        error("배달 경로 구현이 없다 — OPEN-STR-12. relay 는 Live 환경에서만 이 자리에 닿는다")
}

class UnavailableContentRenderer : ContentRenderer {
    override fun render(
        contentRef: ContentRef,
        channel: Channel,
    ): RenderedContent = error("렌더러 구현이 없다 — OPEN-STR-12")
}

class UnavailableNotificationSender : NotificationSender {
    override fun send(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult = error("발송 채널 구현이 없다 — OPEN-STR-12")
}
