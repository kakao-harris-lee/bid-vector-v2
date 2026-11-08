package bidvector.adapters.notification

import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ChannelRoute
import bidvector.workflow.strategy.OperatorId
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * D-2 — 경로는 설정에서 온다. `RouteKey` 는 슬러그이고 **chat id 는 여기 들어오지 않는다**
 * (어댑터가 슬러그 → chat id 를 해소한다).
 */
class ConfiguredRouteDirectoryTest {
    private val directory = ConfiguredRouteDirectory(FAKE_OWNER, FAKE_ROUTE)

    @Test
    fun `설정된 owner 에게 Telegram 은 활성이고 Email 은 비활성이다`() {
        val routes = directory.routesFor(FAKE_OWNER)

        routes[Channel.Telegram] shouldBe ChannelRoute(enabled = true, key = FAKE_ROUTE)
        routes[Channel.Email] shouldBe ChannelRoute(enabled = false, key = null)
    }

    /**
     * **키 집합이 채널 전수다** — 빠진 채널은 `channelEnabled=true, routeConfigured=false` 로
     * 읽혀 「활성인데 경로가 없다」가 된다. 전수를 덮으면 그 읽기가 설정의 뜻과 어긋나지 않는다.
     */
    @Test
    fun `키 집합이 채널 전수다`() {
        directory.routesFor(FAKE_OWNER).keys shouldBe Channel.entries.toSet()
    }

    /**
     * 다른 owner 에게는 **빈 map** 이다 — dispatch 가 그것을 `TargetMissing` 으로 읽어 억제하므로
     * 「아무 route 나 돌려주는」 기본값이 없다.
     *
     * **이것을 「owner 격리 방어선」으로 주장하지 않는다(cr M-1).** production 에서 이 클래스는
     * `ConfiguredRouteDirectory(target.owner, …)` 로 세워지고 `routesFor` 를 부르는 것도 같은
     * `target.owner` 다(`RelayOutboxNotifications` 가 그 값으로 intent 를 짓는다) — 즉 **이 분기는
     * 운영 경로에서 도달하지 않는다.** 그것을 막는 층이라고 적으면 공허한 주장이다.
     *
     * 그래서 이 test 가 재는 것은 **정의된 거동**이다: 뒤에 owner 출처가 둘로 갈리는 변경이
     * 오면(설정 owner ≠ relay target owner) 그때 이 분기가 살아나고, 그 거동이 미결이 아니다.
     * 단일 운영자(`OPEN-STR-07`)에서 출처를 둘로 늘리는 것은 방어가 아니라 오설정 표면이라
     * 이 slice 는 늘리지 않았다 — `checklist.md` 에 등재.
     */
    @Test
    fun `다른 owner 에게는 경로가 없다 — 정의된 거동(운영 경로에서는 도달하지 않는다)`() {
        directory.routesFor(OperatorId("someone-else")).shouldBeEmpty()
    }
}
