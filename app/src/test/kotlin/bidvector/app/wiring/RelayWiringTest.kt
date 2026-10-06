package bidvector.app.wiring

import bidvector.app.relay.NotificationRelayRunner
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.function.Supplier
import javax.sql.DataSource

/**
 * D-6F10-18 ⑧·D-6F10-20 — relay 배선의 켜짐 조건과 기동 실패.
 *
 * ① 러너는 `bidvector.relay.mode=once` 일 때만 빈이다(기본 꺼짐 — compose 가 `*.mode` 를
 * 설정하지 않으므로 평가 endpoint 만 쓰는 배포는 relay 설정 없이 뜬다) ② 켜졌을 때의 설정
 * 오류는 전부 **기동 실패**다(조용히 기본값으로 메우지 않는다) ③ **`Production` 환경 설정은
 * 거부한다** — 실 sender 가 없는 동안 Live 환경으로 뜨면 claim 뒤 발송 자리에서 터지고 그
 * 행은 다음 run 이 격리한다(매 run 이 행을 영구히 잃는다).
 *
 * `DataSource` 는 연결하지 않는 `PGSimpleDataSource` 다 — 배선은 조립만 하고 질의를 돌리지
 * 않으므로 DB 가 필요 없다(이 사실 자체가 「러너가 돌기 전에 실패한다」의 한 측면이다).
 */
class RelayWiringTest {
    private class Booted(
        val context: AnnotationConfigApplicationContext,
        val failure: Throwable?,
    )

    private fun boot(vararg properties: String): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(RelayWiring::class.java)
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
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

    private fun validProperties(
        environment: String = "development",
        owner: String = "relay-operator",
        channel: String = "telegram",
        claimLimit: String = "50",
    ) = arrayOf(
        "bidvector.relay.mode=once",
        "bidvector.relay.environment=$environment",
        "bidvector.relay.owner=$owner",
        "bidvector.relay.channel=$channel",
        "bidvector.relay.claim-limit=$claimLimit",
    )

    @Test
    fun `mode 가 없으면 러너 빈이 없다 — 기본 꺼짐`() {
        withBoot { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeanNamesForType(ApplicationRunner::class.java)
                .toList()
                .shouldBeEmpty()
            booted.context
                .getBeanNamesForType(NotificationRelayRunner::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `mode 가 once 이고 설정이 유효하면 러너 빈이 하나다`() {
        withBoot(*validProperties()) { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeanNamesForType(NotificationRelayRunner::class.java)
                .toList()
                .size shouldBe 1
            booted.context.getBean(NotificationRelayRunner::class.java) shouldNotBe null
        }
    }

    @Test
    fun `Production 환경 설정은 기동을 거부한다 — 실 sender 부재는 설정 오류다`() {
        withBoot(*validProperties(environment = "production")) { booted ->
            booted.failure shouldNotBe null
            booted.failure!!.stackTraceToString() shouldContain "OPEN-STR-12"
        }
    }

    @Test
    fun `환경이 설정되지 않으면 기동에 실패한다 — 기본값 없음`() {
        val properties = validProperties().filterNot { it.startsWith("bidvector.relay.environment") }

        withBoot(*properties.toTypedArray()) { booted ->
            booted.failure shouldNotBe null
        }
    }

    @Test
    fun `owner 가 공백이면 기동에 실패한다 — 값을 지어내지 않는다`() {
        withBoot(*validProperties(owner = " ")) { booted ->
            booted.failure shouldNotBe null
            booted.failure!!.stackTraceToString() shouldContain "bidvector.relay.owner"
        }
    }

    @Test
    fun `claim 상한이 0 이면 기동에 실패한다`() {
        withBoot(*validProperties(claimLimit = "0")) { booted ->
            booted.failure shouldNotBe null
            booted.failure!!.stackTraceToString() shouldContain "claim-limit"
        }
    }

    @Test
    fun `어휘 밖 환경 이름은 바인딩 실패다`() {
        withBoot(*validProperties(environment = "whatever")) { booted ->
            booted.failure shouldNotBe null
        }
    }
}
