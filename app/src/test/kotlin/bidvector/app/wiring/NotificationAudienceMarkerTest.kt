package bidvector.app.wiring

import bidvector.workflow.notificationinbox.NotificationAudience
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.SQLException
import javax.sql.DataSource

/** 대역이 던지는 문구 — 표지 줄에 실리지 않는 것을 단언하는 자리다. */
private const val FAILURE_MESSAGE = "connection refused by fake"

/** 연결을 **못 주는** `DataSource` — 바꾸는 메서드가 하나뿐이라 `Proxy` 를 쓴다. */
private fun failingDataSource(): DataSource =
    Proxy.newProxyInstance(
        DataSource::class.java.classLoader,
        arrayOf(DataSource::class.java),
        InvocationHandler { _, method, _ ->
            if (method.name == "getConnection") {
                throw SQLException(FAILURE_MESSAGE)
            }
            throw UnsupportedOperationException("이 대역은 ${method.name} 을 모른다")
        },
    ) as DataSource

/**
 * **잃은 알림의 표지가 실제로 남는가**(M9/ops-code 수정 라운드 2 — v L-1 · cr low).
 *
 * 알려진 제한 ⑧ 은 이 로그를 운영자가 「알림이 왜 없었나」를 묻는 **유일한 자리**로 지목한다. 그런데
 * 그 한 줄을 지우거나 조건을 뒤집는 변이가 전 test 초록이었다 — 지목된 자리가 측정 밖이었다.
 *
 * 로거에 붙이는 appender 는 **이 조립의 로거 하나**다(root 가 아니다 — 공유 하네스에서 root 를 열면
 * 남의 줄을 집는다). 끝나면 떼고 멈춘다(전역 상태를 되돌림까지 단언하는 규율).
 */
class NotificationAudienceMarkerTest {
    @Test
    fun `청중을 못 읽으면 예외 클래스 이름을 실은 표지가 남는다`() {
        val logger = LoggerFactory.getLogger(NotificationInboxWiring::class.java) as Logger
        val sink = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(sink)
        try {
            val port = NotificationInboxWiring().notificationAudiencePort(failingDataSource())

            val resolved = port.accounts()

            resolved shouldBe NotificationAudience.Unavailable("SQLException")
            val warnings = sink.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
            // **한 줄이다** — 더 적히면 그것도 이 단언이 잡는다(표지가 둘이면 계수가 흔들린다).
            warnings shouldHaveSize 1
            warnings.single() shouldContain "SQLException"
            // 메시지는 싣지 않는다 — 저장소 예외 문구에는 행 값이 실릴 수 있다.
            warnings.single() shouldNotContain FAILURE_MESSAGE
        } finally {
            logger.detachAppender(sink)
            sink.stop()
        }
    }
}
