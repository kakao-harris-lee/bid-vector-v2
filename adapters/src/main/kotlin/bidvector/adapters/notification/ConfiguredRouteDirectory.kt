package bidvector.adapters.notification

import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ChannelRoute
import bidvector.workflow.notification.RouteDirectory
import bidvector.workflow.notification.RouteKey
import bidvector.workflow.strategy.OperatorId

/**
 * 설정에서 오는 배달 경로(D-2) — 마이그레이션 0. `RouteKey` 는 **슬러그**이고 chat id 는 여기
 * 들어오지 않는다(슬러그 → chat id 해소는 sender 의 설정이 진다). 그래서 이 값이 로그·장부에
 * 그대로 남아도 발송 대상의 원문이 아니다.
 *
 * **설정된 owner 가 아니면 빈 map** 이다(설계 검토 우회 7) — dispatch 가 그것을 `TargetMissing` 으로
 * 읽어 억제하므로 남의 chat 으로 가는 경로가 없다. 「아무 route 나 돌려주는」 기본값을 두지 않는다.
 *
 * 채널 **전수**를 덮는다 — 소진 `when` 이라 [Channel] 에 값이 생기면 컴파일이 깨지고, 그 자리에서
 * 「새 채널을 활성으로 둘 것인가」를 결정하게 된다(빠뜨린 채널은 「활성인데 경로가 없다」로 읽힌다).
 */
class ConfiguredRouteDirectory(
    private val configuredOwner: OperatorId,
    private val telegramRoute: RouteKey,
) : RouteDirectory {
    // 매개변수 이름은 port 의 것(`owner`)을 그대로 쓴다 — 바꾸면 named argument 호출이 깨진다는
    // 경고가 `-Werror` 에서 컴파일 실패다. 그래서 **필드** 쪽 이름을 비켰다.
    override fun routesFor(owner: OperatorId): Map<Channel, ChannelRoute> =
        if (owner != configuredOwner) {
            emptyMap()
        } else {
            Channel.entries.associateWith(::routeFor)
        }

    private fun routeFor(channel: Channel): ChannelRoute =
        when (channel) {
            Channel.Telegram -> ChannelRoute(enabled = true, key = telegramRoute)

            // Email 은 이 slice 밖이다(D-2) — 「활성인데 경로가 없다」가 아니라 **비활성**이다.
            Channel.Email -> ChannelRoute(enabled = false, key = null)
        }
}
