package bidvector.adapters.notificationinbox

import bidvector.workflow.notificationinbox.NotificationAudience
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.SQLException
import javax.sql.DataSource

/**
 * 연결을 **못 주는** `DataSource` — `Proxy` 를 쓰는 이유는 이 test 가 바꾸는 것이 메서드 하나인데
 * 인터페이스의 메서드는 여럿이기 때문이다(`PoolerLeaseProbeTest` 와 같은 수법).
 */
private fun failingDataSource(): DataSource =
    Proxy.newProxyInstance(
        DataSource::class.java.classLoader,
        arrayOf(DataSource::class.java),
        InvocationHandler { _, method, _ ->
            if (method.name == "getConnection") {
                throw SQLException("연결을 얻지 못했다")
            }
            throw UnsupportedOperationException("이 대역은 ${method.name} 을 모른다")
        },
    ) as DataSource

/**
 * **청중 조회의 저장 층 장애는 값으로 나온다**(M9/ops-code 수정 라운드 1 — cr MEDIUM · v M-2).
 *
 * 앞 판은 `SQLException` 을 전파했다. 이 포트는 **평가 커밋 run 안에서** 불리므로 그 전파가 채널
 * 요청 전에 run 을 끊었고, 그래서 저장 층 장애 하나가 그 판정의 **outbox 흔적까지** 함께 지웠다.
 *
 * 실 DB 를 띄우지 않는다 — 재는 것이 질의 결과가 아니라 **장애의 접힘**이라, 연결을 못 주는 대역
 * 하나로 그 축이 전부 선다(알림함 질의 자체는 실 DB test 들이 잰다).
 */
class NotificationAudienceFailureTest {
    @Test
    fun `연결 장애는 전파되지 않고 Unavailable 로 접힌다`() {
        val audience = JdbcNotificationAudience(failingDataSource())

        audience.accounts() shouldBe NotificationAudience.Unavailable
    }

    /**
     * **빈 청중과 같은 값이 아니다** — 둘을 빈 목록 하나로 접으면 장애가 「알림함이 조용히 0 건」
     * 으로 보이고 아무도 모른다. 그 구별이 타입에 있다는 것을 여기서 잰다.
     */
    @Test
    fun `장애는 받을 사람이 없는 것과 다른 값이다`() {
        val failed = JdbcNotificationAudience(failingDataSource()).accounts()

        (failed == NotificationAudience.Accounts(emptyList())) shouldBe false
    }
}
