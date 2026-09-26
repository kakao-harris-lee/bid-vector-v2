package bidvector.app.management

import bidvector.app.productionApplication
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent
import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.Ordered
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/** 잠금이 **이름 대지 않은** 형제 키 — 이것이 열리면 readiness 가 구성 요소 세부를 낸다. */
private const val LATE_SOURCE_SIBLING_KEY = "management.endpoint.health.group.readiness.show-details"

/**
 * **거부가 기동 완료 전에 났는가**의 표지(verifier r3 M-1). refresh 가 끝난 **뒤에** Boot 이
 * 내는 event 셋을 기록한다 — 하나라도 관측되면 재검사 자리가 refresh 밖으로 밀린 것이다.
 *
 * 왜 `AvailabilityChangeEvent(ACCEPTING_TRAFFIC)`·`ApplicationReadyEvent` 만으로 부족한가
 * (verifier r3 M-1 실측): 재검사를 **그 두 event 자리로 옮기는** 변이에서는 재검사 listener 가
 * 같은 event 를 먼저 받아 던지고, multicast 가 그 자리에서 끊겨 이 기록기가 그 event 를 보지
 * 못할 수 있다(재검사의 우선순위가 최상위다). `ApplicationStartedEvent` 는 어느 변형에서도
 * 재검사가 듣지 않는 자리이고 셋 가운데 가장 먼저 오므로, **listener 순서에 기대지 않고**
 * 「refresh 를 지나 기동이 섰다」를 잡는다.
 */
private class PostRefreshEventRecorder : ApplicationListener<ApplicationEvent> {
    val observed: MutableList<String> = mutableListOf()

    override fun onApplicationEvent(event: ApplicationEvent) {
        when {
            event is ApplicationStartedEvent -> observed += "ApplicationStartedEvent"
            event is ApplicationReadyEvent -> observed += "ApplicationReadyEvent"
            event is AvailabilityChangeEvent<*> && event.state == ReadinessState.ACCEPTING_TRAFFIC ->
                observed += "AvailabilityChangeEvent(ACCEPTING_TRAFFIC)"
        }
    }
}

/**
 * D-6A2a-14 — **refresh 를 지나는** 부팅으로 늦은 재검사를 잰다.
 *
 * `ManagementSurfaceBootRefusalTest` 는 초기화자(또는 `ApplicationPreparedEvent`)에서 끝난다 —
 * 그래서 refresh **도중에** 실체로 채워지는 property source 를 구조적으로 볼 수 없다. verifier r2
 * F-1r 이 그 사각을 출하 이미지에서 실측했다: 서블릿 컨텍스트 init-param 소스는 초기화자 시점에
 * 비열거 stub 이고, `createWebServer()` 끝의 `initPropertySources()` 가 그것을 열거 가능한 소스로
 * 바꾼다. 그 소스가 잠금이 이름 대지 않은 **형제 키**를 들고 오면 r1 결함이 전부 다시 열렸다.
 *
 * **적대 입력을 `server.servlet.context-parameters` 속성으로 주지 않는다.** 그 이름공간은 이제
 * 조기 거부 대상(보조 잠금)이라 초기화자가 먼저 끊고, 그러면 이 test 는 **늦은 재검사를 지우고도
 * 초록**이 된다(공허한 단언). 대신 init-param 을 **서블릿 컨텍스트에 직접** 심는다 — 채널이
 * 무엇이든 「늦게 채워지는 소스가 형제 키를 운반한다」는 형태 그대로다.
 *
 * **적대 조각을 `@Configuration` 으로 두지 않는다**(2026-09-26 실측 — D-6A1-27 과 같은 함정):
 * 출하 조립의 컴포넌트 스캔 기준은 `bidvector.app` 과 그 하위 전부이고 test 소스도 그 범위 안이라,
 * `@Configuration` 을 붙이면 이 조각이 **다른 모든 production 조립 부팅에 끼어들어** 그 부팅들을
 * 전부 거부시켰다. 그래서 초기화자가 bean 을 **손으로 등재**한다(스테레오타입 annotation 0).
 *
 * DB 가 필요한 이유: 거부가 걸리는 자리는 부모의 `finishRefresh` 라 그 전에 singleton 전부
 * (DataSource·Flyway migrate)가 이미 만들어진다. 「관리 child context 의 refresh 를 보았다」가
 * 그 사실의 표지다 — child 는 부모 `finishRefresh` 의 `SmartLifecycle` 단계에서 서기 때문이다.
 */
class ManagementSurfaceLateSourceRefusalTest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "late-source-refusal-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_late_source_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            postgres.stop()
        }
    }

    /**
     * 적대 init-param 을 심는 bean 을 손으로 등재한다 — 이 bean 은 `createWebServer()` 안에서
     * 불리므로 `initPropertySources()` 가 소스를 실체로 바꿀 때 이 키가 그 안에 있다.
     * `registerSingleton` 으로 등재해도 `getBeanNamesForType` 이 수동 singleton 을 포함하므로
     * Boot 의 `ServletContextInitializer` 수집에 잡힌다.
     */
    private fun hostileLateSource(): ApplicationContextInitializer<ConfigurableApplicationContext> =
        ApplicationContextInitializer { context ->
            context.beanFactory.registerSingleton(
                "hostileLateInitParameter",
                ServletContextInitializer { it.setInitParameter(LATE_SOURCE_SIBLING_KEY, "always") },
            )
        }

    private fun builder(): SpringApplicationBuilder =
        productionApplication()
            .properties(
                mapOf(
                    "server.port" to "0",
                    "management.server.port" to "0",
                    "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                    "bidvector.persistence.username" to postgres.username,
                    "bidvector.persistence.credential" to postgres.password,
                    "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                    "bidvector.evaluation.candidate-cap" to "1000",
                ),
            )

    /**
     * 「검사는 readiness 전에」를 **거부 자체와 같은 부팅에서** 잠근다(verifier r3 M-1). 재검사를
     * 늦은 자리(`AvailabilityChangeEvent(ACCEPTING_TRAFFIC)`·`ApplicationReadyEvent`)로 옮기면
     * 거부는 여전히 나므로 문면 단언만으로는 자리 이동이 보이지 않는다 — 그래서 **그 자리들에
     * 도달했다는 흔적이 0** 임을 함께 요구한다([PostRefreshEventRecorder] 의 KDoc 이 왜 세 event 를
     * 함께 보는지를 든다).
     */
    @Test
    fun `늦은 소스가 운반하는 잠금 밖 형제 키는 refresh 를 지나 기동을 거부한다`() {
        var managementContextRefreshed = false
        val observeChildRefresh =
            ApplicationListener<ContextRefreshedEvent> { event ->
                if (WebServerApplicationContext.hasServerNamespace(event.applicationContext, "management")) {
                    managementContextRefreshed = true
                }
            }
        val recorder = PostRefreshEventRecorder()

        val thrown =
            shouldThrow<IllegalStateException> {
                builder().initializers(hostileLateSource()).listeners(observeChildRefresh, recorder).run()
            }

        thrown.message!! shouldContain "D-6A2a-14"
        thrown.message!! shouldContain LATE_SOURCE_SIBLING_KEY
        // 「refresh 를 지났다」의 표지 — 없으면 「기동이 실패했다」가 조기 거부로도 참이 된다.
        managementContextRefreshed shouldBe true
        // 「readiness 전이다」의 표지 — 재검사가 refresh 밖으로 밀리면 이 목록이 비지 않는다.
        recorder.observed.shouldBeEmpty()
    }

    /**
     * **readiness 가 수락을 알리기 전인가**를 실측한다(r3 정정 — privacy-gate r3 L-5 · verifier r3
     * L-3). 늦은 재검사가 도는 자리(`ContextRefreshedEvent`)는 Boot 이
     * `ReadinessState.ACCEPTING_TRAFFIC` 을 발행하는 자리보다 앞이다(그 발행은
     * `ApplicationReadyEvent` **뒤**다) — 그래서 그 시점의 readiness 프로브는 200 이 아니다.
     *
     * **connector 는 그보다 먼저 bind 된다**: 이 test 가 재검사 시점에 관리 포트로 HTTP 요청을
     * 보내 503 을 받는다는 사실 자체가 「그 시점에 포트가 요청을 처리한다」는 뜻이다. 그러므로
     * 정확한 문장은 「트래픽을 한 번도 받지 않는다」가 아니라 「readiness 가 수락을 알리기
     * 전에 거부한다」다. 그 사이 창(밀리초)에 관해서는 `checklist.md` 알려진 제한 18 이 정본이다.
     *
     * 세 가지를 함께 잰다. ① 재검사 시점의 readiness 응답 코드(양성 대조: 끝난 뒤에는 200 이므로
     * 「경로가 없어서 200 이 아니었다」가 아니다) ② 같은 자리에 등록한 listener 가 **관리 child
     * context 의 refresh 도 받는다**(Spring 이 child event 를 부모에게도 발행한다) — 늦은 재검사가
     * 부모와 자식 **둘 다**를 판정 대상으로 본다는 배선 사실이다 ③ 정상 환경은 거부되지 않는다.
     */
    @Test
    fun `늦은 재검사는 readiness 가 수락을 알리기 전에 돈다`() {
        val rest = TestRestTemplate()
        var managementPort = 0
        var managementContextRefreshed = false
        var readinessCodeWhenChecked: Int? = null

        val capturePort =
            ApplicationListener<ServletWebServerInitializedEvent> { event ->
                if (WebServerApplicationContext.hasServerNamespace(event.applicationContext, "management")) {
                    managementPort = event.webServer.port
                }
            }
        val observeRefresh =
            object : ApplicationListener<ContextRefreshedEvent>, Ordered {
                // 늦은 재검사(최상위 우선순위) **뒤**에 돈다 — 재검사가 통과한 시점을 잰다.
                override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

                override fun onApplicationEvent(event: ContextRefreshedEvent) {
                    if (WebServerApplicationContext.hasServerNamespace(event.applicationContext, "management")) {
                        managementContextRefreshed = true
                    } else {
                        readinessCodeWhenChecked = readinessCode(rest, managementPort)
                    }
                }
            }

        val context = builder().listeners(capturePort, observeRefresh).run()
        try {
            readinessCodeWhenChecked shouldBe 503
            readinessCode(rest, managementPort) shouldBe 200
            managementContextRefreshed shouldBe true
        } finally {
            context.close()
        }
    }

    private fun readinessCode(
        rest: TestRestTemplate,
        port: Int,
    ): Int {
        require(port > 0) { "관리 포트를 읽지 못했다 — 관리 web server 가 뜨지 않았을 수 있다" }
        val url = "http://localhost:$port/actuator/health/readiness"
        return rest.getForEntity(url, String::class.java).statusCode.value()
    }
}
