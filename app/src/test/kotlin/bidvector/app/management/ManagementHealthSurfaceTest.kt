package bidvector.app.management

import bidvector.app.http.OperatorCredentialFilter
import bidvector.app.productionApplication
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket

/**
 * D-6A2a-4 — **출하 조립을 실제로 띄워** 관리 표면을 잰다. 여기서 재는 것은 넷이다:
 * ① 관리 포트에서 health·liveness·readiness 가 상태 한 단어만 낸다(본문 키 하나)
 * ② 관리 포트에 health 말고 아무것도 없다(노출 = 하나, 열거 HTTP 탐침 + 런타임 endpoint 집합)
 * ③ **API 포트에는 actuator 가 없다** — 자격증명을 주고도 404 다(같은 포트에 있었다면 200)
 * ④ readiness 는 DB 가 사라지면 DOWN 이고 liveness 는 UP 을 유지한다.
 *
 * 조립은 [productionApplication] 하나로만 띄운다 — `main()`과 **같은 참조**다(D-6A1-27 의
 * 교훈: test 가 자기 사본으로 조립하면 production 배선을 지워도 초록이다).
 *
 * `@Order` 를 쓰는 이유: ④ 는 postgres 컨테이너를 **멈춰서** 잰다. 멈춘 뒤에는 ①~③ 의
 * 전제가 사라지므로 마지막에 둔다(공유 fixture 를 파괴하는 test 하나를 순서로 격리한다).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ManagementHealthSurfaceTest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "management-surface-test-fixture-credential"

        /** 관리 포트 기본값(8081)을 test 가 점유하지 않는다 — 병렬 test fork 끼리 충돌한다. */
        private val managementPort: Int = ServerSocket(0).use { it.localPort }

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_management_surface_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var context: ConfigurableApplicationContext
        private var apiPort: Int = 0

        /**
         * 관리 child context 를 잡는다 — Spring 은 child 의 event 를 parent 로도 발행하므로
         * 출하 조립에 listener 하나만 얹으면 된다(child context 자체는 parent 의 bean 이 아니다).
         */
        private var managementContext: ConfigurableApplicationContext? = null

        @JvmStatic
        @BeforeAll
        fun boot() {
            val capture =
                ApplicationListener<ServletWebServerInitializedEvent> { event ->
                    if (event.webServer.port == managementPort) {
                        managementContext = event.applicationContext
                    }
                }
            context =
                productionApplication()
                    .listeners(capture)
                    .properties(
                        mapOf(
                            "server.port" to "0",
                            "management.server.port" to managementPort.toString(),
                            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                            "bidvector.persistence.username" to postgres.username,
                            "bidvector.persistence.credential" to postgres.password,
                            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                            "bidvector.evaluation.candidate-cap" to "1000",
                        ),
                    ).run()
            apiPort =
                (context as ServletWebServerApplicationContext).webServer?.port
                    ?: error("API web server 가 뜨지 않았다")
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            context.close()
            postgres.stop()
        }
    }

    private val restTemplate: TestRestTemplate = TestRestTemplate()

    private fun management(path: String): String = "http://localhost:$managementPort$path"

    private fun api(path: String): String = "http://localhost:$apiPort$path"

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE) }

    @Suppress("UNCHECKED_CAST")
    private fun bodyOf(url: String): ResponseEntity<Map<String, Any?>> =
        restTemplate.getForEntity(url, Map::class.java) as ResponseEntity<Map<String, Any?>>

    @Test
    @Order(1)
    fun `관리 포트의 프로브 둘은 상태 한 단어만 낸다`() {
        listOf("/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
            val response = bodyOf(management(path))
            response.statusCode.value() shouldBe 200
            response.body?.keys?.toList() shouldContainExactly listOf("status")
            response.body?.get("status") shouldBe "UP"
        }
    }

    /**
     * **실측(Boot 4.1.1, 2026-09-26)** — 집계 `GET /actuator/health` 는 `show-details=never`·
     * `show-components=never` 아래에서도 `groups` 칸을 낸다(`SystemHealth.getGroups()` 가
     * `@JsonInclude(NON_EMPTY)` 라 그룹이 있으면 항상 실린다 — 세부 설정과 무관하다).
     * 그 값은 **우리가 지은 그룹 이름 둘**이고 구성 요소 이름·DB 주소·예외 메시지·버전은 없다
     * (위협 모델 ⑤ 가 막는 것은 그 넷이다). 그래서 이 축은 「키 하나」가 아니라 **실측한 모양
     * 그대로 못박는다** — `components`·`details` 같은 칸이 하나라도 늘면 이 단언이 붉어진다.
     */
    @Test
    @Order(1)
    fun `집계 health 는 상태와 그룹 이름 둘만 낸다`() {
        val response = bodyOf(management("/actuator/health"))
        response.statusCode.value() shouldBe 200
        response.body?.keys?.sorted() shouldContainExactly listOf("groups", "status")
        response.body?.get("status") shouldBe "UP"

        @Suppress("UNCHECKED_CAST")
        val groups = response.body?.get("groups") as Collection<String>
        groups.sorted() shouldContainExactly listOf("liveness", "readiness")
    }

    @Test
    @Order(2)
    fun `관리 포트에는 health 말고 아무 endpoint 도 없다`() {
        val endpoints =
            requireNotNull(managementContext) { "관리 child context 를 잡지 못했다 — 포트가 분리되지 않았을 수 있다" }
                .getBean(WebEndpointsSupplier::class.java)
                .endpoints
                .map { it.endpointId.toString() }
                .sorted()
        endpoints shouldContainExactly listOf("health")

        listOf(
            "/actuator",
            "/actuator/env",
            "/actuator/configprops",
            "/actuator/beans",
            "/actuator/info",
            "/actuator/metrics",
            "/actuator/loggers",
            "/actuator/threaddump",
            "/actuator/heapdump",
            "/actuator/mappings",
            "/actuator/shutdown",
        ).forEach { path ->
            restTemplate.getForEntity(management(path), String::class.java).statusCode.value() shouldBe 404
        }
    }

    @Test
    @Order(3)
    fun `API 포트에는 actuator 가 없다 — 자격증명을 줘도 404 다`() {
        val unauthenticated = restTemplate.getForEntity(api("/actuator/health"), String::class.java)
        unauthenticated.statusCode.value() shouldBe 401

        listOf("/actuator/health", "/actuator/health/readiness", "/actuator/env").forEach { path ->
            val authorized =
                restTemplate.exchange(
                    api(path),
                    HttpMethod.GET,
                    HttpEntity<Void>(authorizedHeaders()),
                    String::class.java,
                )
            authorized.statusCode.value() shouldBe 404
        }

        // 기존 API 는 그대로 — 관리 포트 분리가 인증 경계를 건드리지 않았다.
        val strategy =
            restTemplate.exchange(
                api("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                String::class.java,
            )
        strategy.statusCode.value() shouldBe 200
    }

    @Test
    @Order(4)
    fun `DB 가 사라지면 readiness 는 DOWN 이고 liveness 는 UP 을 유지한다`() {
        postgres.stop()

        val readiness = bodyOf(management("/actuator/health/readiness"))
        readiness.statusCode.value() shouldNotBe 200
        readiness.body?.keys?.toList() shouldContainExactly listOf("status")
        readiness.body?.get("status") shouldNotBe "UP"

        val liveness = bodyOf(management("/actuator/health/liveness"))
        liveness.statusCode.value() shouldBe 200
        liveness.body?.get("status") shouldBe "UP"
    }
}
