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
     * 다른 owner 에게는 **빈 map** 이다(설계 검토 우회 7) — dispatch 가 그것을 `TargetMissing`
     * 으로 읽어 억제하므로 남의 chat 으로 가는 경로가 없다. 「아무 route 나 돌려주는」 기본값이 없다.
     */
    @Test
    fun `다른 owner 에게는 경로가 없다`() {
        directory.routesFor(OperatorId("someone-else")).shouldBeEmpty()
    }
}
