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
 * **설정된 owner 가 아니면 빈 map** 이다 — dispatch 가 그것을 `TargetMissing` 으로 읽어 억제한다.
 * 「아무 route 나 돌려주는」 기본값을 두지 않는다.
 *
 * **그 빈 map 을 「남의 chat 으로 가는 경로를 막는 층」이라고 읽지 않는다**(cr M-1 · v N-3 로 정정).
 * production 에서 이 객체는 `ConfiguredRouteDirectory(target.owner, …)` 로 세워지고 `routesFor` 를
 * 부르는 것도 같은 `target.owner` 다(relay 가 그 값으로 intent 를 짓는다) — **비교의 양쪽이 같은
 * 값이라 그 분기는 운영 경로에서 도달하지 않는다.** 설계 검토 우회 7 은 이 라운드에 「방어하지
 * 않는다」로 정정됐고, 실제 격리는 단일 운영자 전제(`OPEN-STR-07`)가 진다.
 *
 * 남긴 것은 **거동 서술**이고(빈 map 이다) 내린 것은 **방어 주장**이다(경로가 없다). 뒤에 owner 출처가
 * 둘로 갈리는 변경이 오면 그때 이 분기가 살아나고, 그 거동은 test 가 이미 정의해 두었다.
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
