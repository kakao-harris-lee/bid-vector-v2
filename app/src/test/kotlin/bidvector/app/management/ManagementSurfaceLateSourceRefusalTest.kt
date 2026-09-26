package bidvector.app.management

import bidvector.app.productionApplication
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent
import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.Ordered
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.atomic.AtomicBoolean

/** 잠금이 **이름 대지 않은** 형제 키 — 이것이 열리면 readiness 가 구성 요소 세부를 낸다. */
private const val LATE_SOURCE_SIBLING_KEY = "management.endpoint.health.group.readiness.show-details"

/**
 * 「refresh 를 지났다」의 표지. `SmartInitializingSingleton` 은 singleton 전부가 만들어진 뒤
 * (`finishBeanFactoryInitialization` 끝)에 불린다 — 이 값이 참이면 거부는 초기화자가 아니라 그
 * **뒤**에서 났다. 표지가 없으면 「기동이 실패했다」가 조기 거부로도 참이 된다.
 */
private val PAST_REFRESH = AtomicBoolean(false)

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
 * DB 가 필요한 이유: 거부가 걸리는 자리가 `finishRefresh` 라 그 전에 singleton 전부(DataSource·
 * Flyway migrate)가 이미 만들어진다. [PAST_REFRESH] 표지가 그 사실을 단언으로 만든다.
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
     * 적대 init-param 을 심는 조립 조각 — 이 bean 은 `createWebServer()` 안에서 불리므로
     * `initPropertySources()` 가 소스를 실체로 바꿀 때 이 키가 그 안에 있다.
     */
    @Configuration(proxyBeanMethods = false)
    open class HostileLateSource {
        @Bean
        open fun hostileInitParameter(): ServletContextInitializer =
            ServletContextInitializer { it.setInitParameter(LATE_SOURCE_SIBLING_KEY, "always") }

        @Bean
        open fun pastRefreshMarker(): SmartInitializingSingleton = SmartInitializingSingleton { PAST_REFRESH.set(true) }
    }

    @BeforeEach
    fun resetMarker() {
        PAST_REFRESH.set(false)
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

    @Test
    fun `늦은 소스가 운반하는 잠금 밖 형제 키는 refresh 를 지나 기동을 거부한다`() {
        val thrown = shouldThrow<IllegalStateException> { builder().sources(HostileLateSource::class.java).run() }

        thrown.message!! shouldContain "D-6A2a-14"
        thrown.message!! shouldContain LATE_SOURCE_SIBLING_KEY
        PAST_REFRESH.get() shouldBe true
    }

    /**
     * **트래픽을 받기 전인가**를 실측한다. 늦은 재검사가 도는 자리(`ContextRefreshedEvent`)는
     * Boot 이 `ReadinessState.ACCEPTING_TRAFFIC` 을 발행하는 자리보다 앞이다(그 발행은
     * `ApplicationReadyEvent` **뒤**다) — 그래서 그 시점의 readiness 프로브는 200 이 아니다.
     *
     * 세 가지를 함께 잰다. ① 재검사 시점의 readiness 응답 코드(양성 대조: 끝난 뒤에는 200 이므로
     * 「경로가 없어서 200 이 아니었다」가 아니다) ② 같은 자리에 등록한 listener 가 **관리 child
     * context 의 refresh 도 받는다**(Spring 이 child event 를 parent 로도 발행한다) — 늦은 재검사가
     * 부모와 자식 **둘 다**를 판정 대상으로 본다는 배선 사실이다 ③ 정상 환경은 거부되지 않는다.
     */
    @Test
    fun `늦은 재검사는 readiness 가 트래픽을 받기 전에 돈다`() {
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
