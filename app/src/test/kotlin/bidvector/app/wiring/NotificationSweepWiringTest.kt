package bidvector.app.wiring

import bidvector.app.notificationinbox.NotificationSweepRunner
import bidvector.workflow.bidding.DaysBeforeWindow
import bidvector.workflow.bidding.NotificationSweepCandidatePort
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.io.PrintWriter
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.function.Supplier
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * M7/7C③-b — 알림 훑기 배선의 **켜짐 조건과 표면**.
 *
 * ① 러너는 `bidvector.notification-sweep.mode=once` 일 때만 빈이다(기본 꺼짐 — compose 는
 * `*.mode` 를 설정하지 않는다) ② 켜져도 빈은 **러너 하나와 해소된 정책 하나**다: 후보 질의와
 * use case 가 컨텍스트에 없으므로 다른 빈이 「알림을 적는 능력」이나 「전 계정 건 조회」를
 * 주입받을 자리가 없다 ③ 창 정책을 **조립이 해소**한다(러너가 시스템 시계로 다시 고르지 않는다).
 */
class NotificationSweepWiringTest {
    private class Booted(
        val context: AnnotationConfigApplicationContext,
        val failure: Throwable?,
    )

    private fun boot(vararg properties: String): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(NotificationSweepWiring::class.java)
        context.registerBean(Clock::class.java, Supplier { Clock { Instant.EPOCH } })
        // 연결하지 않는다 — 배선은 조립만 하고 질의는 러너가 refresh 뒤에 돌린다.
        context.registerBean(DataSource::class.java, Supplier { UnusableDataSource() })
        context.registerBean(
            NotificationInboxPort::class.java,
            Supplier { NotificationInboxPort { NotificationInboxOutcome.Created } },
        )
        val failure =
            try {
                context.refresh()
                null
            } catch (
                @Suppress("TooGenericExceptionCaught") thrown: Exception,
            ) {
                thrown
            }
        return Booted(context, failure)
    }

    private fun <T> withBoot(
        vararg properties: String,
        block: (Booted) -> T,
    ): T {
        val booted = boot(*properties)
        return try {
            block(booted)
        } finally {
            booted.context.close()
        }
    }

    @Test
    fun `mode 가 없으면 러너 빈이 없다 — 기본 꺼짐`() {
        withBoot { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeanNamesForType(ApplicationRunner::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `mode 가 다른 값이면 러너 빈이 없다`() {
        withBoot("bidvector.notification-sweep.mode=dry-run") { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeanNamesForType(ApplicationRunner::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `mode=once 면 훑기 러너 하나가 올라온다`() {
        withBoot("bidvector.notification-sweep.mode=once") { booted ->
            booted.failure shouldBe null
            booted.context.getBeanNamesForType(NotificationSweepRunner::class.java).size shouldBe 1
            booted.context.getBeanNamesForType(ApplicationRunner::class.java).size shouldBe 1
        }
    }

    /**
     * **후보 질의 빈이 없다** — 어댑터는 `@Bean` 함수 **안의 지역 객체**다. 그래서 다른 빈이
     * 「전 계정의 건을 읽는 능력」을 주입받을 자리가 컨텍스트에 없다(`RetentionWiring` 이 파기
     * 저장소를 지역 객체로 두는 것과 같은 규율·같은 이유).
     */
    @Test
    fun `켜져도 후보 질의는 빈이 아니다`() {
        withBoot("bidvector.notification-sweep.mode=once") { booted ->
            booted.context
                .getBeanNamesForType(NotificationSweepCandidatePort::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    /**
     * **기준일의 구역이 창을 세는 달력과 같다**(v L-3). KST 05 시(= 전날 20:00Z)는 **KST 로 오늘**
     * 이다 — UTC 로 고르면 어제가 되고, 효력일이 바뀌는 날 정책이 하루 어긋난다.
     */
    @Test
    fun `창 정책의 기준일은 KST 달력일이다`() {
        val kstEarlyMorning = Instant.parse("2026-10-31T20:00:00Z")

        notificationPolicyReferenceDate(kstEarlyMorning) shouldBe LocalDate.of(2026, 11, 1)
        // 음성 대조 — UTC 로 골랐다면 하루 전이다.
        LocalDate.ofInstant(kstEarlyMorning, ZoneOffset.UTC) shouldBe LocalDate.of(2026, 10, 31)
    }

    /** 창 정책은 **조립에서 한 번** 해소되고 러너가 그 값을 받는다 — 출하 표의 값 그대로다. */
    @Test
    fun `창 정책을 조립이 해소해 러너에 넘긴다`() {
        withBoot("bidvector.notification-sweep.mode=once") { booted ->
            val policy =
                booted.context
                    .getBean(NotificationSweepRunner::class.java)
                    .sweep.policy
            policy.bidDeadlineImminent shouldBe DaysBeforeWindow(1, 1)
            policy.bidDeadlineToday shouldBe DaysBeforeWindow(0, 0)
            policy.documentDeadline shouldBe DaysBeforeWindow(0, 2)
        }
    }
}

/**
 * **연결을 열 수 없는 `DataSource`** — 배선은 조립만 하고 질의는 러너가 refresh 뒤에 돌리므로,
 * 기동만 보는 이 test 에 실 DB 가 필요 없다는 사실을 타입으로 드러낸다(`RetentionWiringTest` 가
 * 닿지 않는 JDBC URL 로 같은 사실을 적은 것과 같은 축). 열리면 그 자체가 결함이다.
 */
private class UnusableDataSource : DataSource {
    override fun getConnection(): Connection = error("배선 test 는 연결을 열지 않는다")

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = getConnection()

    override fun getLogWriter(): PrintWriter = error("쓰지 않는다")

    override fun setLogWriter(out: PrintWriter?) = error("쓰지 않는다")

    override fun setLoginTimeout(seconds: Int) = error("쓰지 않는다")

    override fun getLoginTimeout(): Int = 0

    override fun getParentLogger(): Logger = error("쓰지 않는다")

    override fun <T : Any?> unwrap(iface: Class<T>?): T = error("쓰지 않는다")

    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}
