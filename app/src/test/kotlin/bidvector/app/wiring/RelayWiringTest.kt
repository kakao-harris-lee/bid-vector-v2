package bidvector.app.wiring

import bidvector.app.relay.NotificationRelayRunner
import com.tngtech.archunit.core.importer.ClassFileImporter
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

/** 기동 거부 판정 함수의 파일 facade — 배선이 이것을 참조하지 않으면 함수를 지나지 않는다. */
private const val BOOT_DECISION_FACADE = "bidvector.app.relay.RelayBootDecisionKt"

/** 옛 술어가 상수를 읽던 열거 — 그 접근이 있으면 이름 술어로 되돌아간 것이다. */
private const val RUNTIME_ENVIRONMENT = "bidvector.workflow.notification.RuntimeEnvironment"

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

    /**
     * cr G-1 — 거부가 보는 것은 **환경 이름이 아니라 정책표가 그 환경에 붙인 모드**다. 오늘
     * `Production` 하나가 `Live` 이므로 그 설정이 거부된다. 메시지도 「Live 모드」를 말한다.
     */
    @Test
    fun `발송 가능 모드 환경 설정은 기동을 거부한다 — 실 sender 부재는 설정 오류다`() {
        withBoot(*validProperties(environment = "production")) { booted ->
            booted.failure shouldNotBe null
            val trace = booted.failure!!.stackTraceToString()
            trace shouldContain "OPEN-STR-12"
            trace shouldContain "Live"
        }
    }

    /**
     * **거부 술어가 enum 이름에 묶이지 않았다는 증거**(cr G-1) — 정책표에서 `Live` 가 아닌 환경
     * 셋은 전부 통과한다. 앞 판의 `environment != Production` 과 지금의 술어는 **오늘 같은
     * 답**을 내므로 이 test 만으로는 둘을 가를 수 없다.
     *
     * 가르는 것은 둘이다(cr T-3 로 문면 정정 — 앞 판은 이 파일에 없는 test 를 「아래」라고
     * 가리켰다): **`bidvector.app.relay.RelayBootDecisionTest`** 가 정책표를 바꿔 넣어 순수
     * 함수를 직접 치고, 아래 「배선은 판정 함수를 지난다」가 **이 배선이 그 함수를 실제로
     * 부르는지**를 구조로 잠근다. 이 test 의 몫은 「오늘의 답이 맞다」뿐이다.
     */
    @Test
    fun `Live 가 아닌 환경 셋은 기동한다`() {
        listOf("staging", "development", "test").forEach { environment ->
            withBoot(*validProperties(environment = environment)) { booted ->
                booted.failure shouldBe null
            }
        }
    }

    /**
     * **R3-M-1** — 배선이 판정 함수를 **부른다**는 것을 바이트코드로 잠근다.
     *
     * 왜 거동으로 재지 않는가: 거동으로 가르려면 정책표를 주입 가능하게 해야 하고, 그러면
     * **운영 배선이 표를 밖에서 받는** 구조가 된다 — 기동 거부의 입력을 호출자가 고를 수 있게
     * 만드는 것은 그 거부가 막으려는 것과 같은 축이다. 표는 코드 안의 승인된 값으로 남기고,
     * 「그 표를 읽는 함수를 지나는가」만 구조로 묻는다.
     *
     * 무엇이 이 단언을 붉히는가: `relayBootDecision` 호출을 지우고 `environment !=
     * RuntimeEnvironment.Production` 으로 되돌리면 ⓐ 함수 facade 의존이 사라지고 ⓑ 그 enum
     * 상수 접근이 생긴다 — **둘 다** 잰다. 어느 하나만 재면 「둘을 함께 둔 채 옛 술어로
     * 판정하는」 모양이 빠져나간다.
     *
     * 이 단언이 못 보는 것: 함수를 부르고 그 결과를 **버리는** 모양. 그 자리는 위의 거부
     * test(`Production` 기동 실패)가 든다 — 함수 결과를 쓰지 않으면 거부가 사라져 그쪽이
     * 붉는다. 둘이 짝이다.
     */
    @Test
    fun `배선은 판정 함수를 지난다 — 옛 이름 술어로 되돌리면 붉는다`() {
        val wiring =
            ClassFileImporter()
                .importClasses(RelayWiring::class.java)
                .single()
        val referenced = wiring.directDependenciesFromSelf.map { it.targetClass.fullName }.toSet()

        referenced.contains(BOOT_DECISION_FACADE) shouldBe true
        // 옛 술어는 enum 상수를 **이름으로** 읽는다 — 그 접근이 있으면 되돌아간 것이다.
        wiring.fieldAccessesFromSelf
            .filter { it.targetOwner.fullName == RUNTIME_ENVIRONMENT }
            .map { it.name } shouldBe emptyList()
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
