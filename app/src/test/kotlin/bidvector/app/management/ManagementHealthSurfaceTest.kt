package bidvector.app.management

import bidvector.app.http.OperatorCredentialFilter
import bidvector.app.productionApplication
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

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

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_management_surface_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var context: ConfigurableApplicationContext
        private var apiPort: Int = 0
        private var managementPort: Int = 0

        /**
         * 관리 child context 를 잡는다 — Spring 은 child 의 event 를 parent 로도 발행하므로
         * 출하 조립에 listener 하나만 얹으면 된다(child context 자체는 parent 의 bean 이 아니다).
         */
        private var managementContext: ConfigurableApplicationContext? = null

        /**
         * **포트를 미리 고르지 않는다**(code-review r1 LOW). `ServerSocket(0)` 으로 번호를 얻어
         * 닫은 뒤 Boot 가 bind 하기까지는 틈이 있어, 병렬 fork 나 이 호스트의 다른 프로세스가
         * 그 번호를 가져가면 `Address already in use` 로 붉는다. 둘 다 `0` 으로 주고 실제
         * 번호는 **이미 뜬 서버의 event** 에서 읽는다. 판별은 번호 비교가 아니라 Boot 자신의
         * 서버 이름공간(`management`)이다.
         *
         * `management.server.port` 를 **명령행 인자**로 주는 것은 양성 대조다(D-6A2a-10) —
         * 배치가 정할 수 있는 유일한 관리 표면 키가 가장 높은 우선순위 자리에서 주어져도
         * 출하 조립이 끝까지 뜬다는 사실을 이 test 전체가 전제로 깐다.
         */
        @JvmStatic
        @BeforeAll
        fun boot() {
            val capture =
                ApplicationListener<ServletWebServerInitializedEvent> { event ->
                    if (WebServerApplicationContext.hasServerNamespace(event.applicationContext, "management")) {
                        managementContext = event.applicationContext
                        managementPort = event.webServer.port
                    } else {
                        apiPort = event.webServer.port
                    }
                }
            context =
                productionApplication()
                    .listeners(capture)
                    .properties(
                        mapOf(
                            "server.port" to "0",
                            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                            "bidvector.persistence.username" to postgres.username,
                            "bidvector.persistence.credential" to postgres.password,
                            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                            "bidvector.evaluation.candidate-cap" to "1000",
                        ),
                    ).run("--management.server.port=0")
            require(apiPort > 0) { "API web server 가 뜨지 않았다" }
            require(managementPort > 0) { "관리 web server 가 뜨지 않았다 — 포트가 분리되지 않았을 수 있다" }
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
            // privacy-gate r1 L-1 — 구성 요소 경로다. `show-components=never` 아래에서는 404 이고,
            // 그 설정이 열리는 순간 **가장 먼저 세부를 내는 경로**다. 그래서 탐침에 넣는다.
            "/actuator/health/db",
        ).forEach { path ->
            restTemplate.getForEntity(management(path), String::class.java).statusCode.value() shouldBe 404
        }
    }

    /**
     * D-6A2a-13 ② — 관리 포트의 **actuator 밖 표면**을 실측으로 못박는다(verifier r1 F-4 ·
     * privacy-gate r1 L-1). 계약 (2b) 는 「관리 포트의 그 밖의 경로: 없어야 한다」였는데
     * `/error` 는 404 가 아니다 — Boot 의 오류 처리 경로가 관리 child context 에도 붙는다.
     *
     * 고치지 않고 **모양을 잠그는** 쪽을 고른 근거: 이 응답에 새는 값이 없다(우리가 만든 예외가
     * 아니라 「디스패치된 오류 없음」 상태라 구성 요소 이름·DB 주소·예외 메시지·스택이 없다).
     * 대신 칸이 하나라도 늘면(예: `message`·`trace`·`exception`) 이 단언이 붉어진다.
     */
    @Test
    @Order(2)
    fun `관리 포트의 error 경로는 값을 싣지 않는 고정 모양이다`() {
        val response = bodyOf(management("/error"))

        response.statusCode.value() shouldBe 200
        response.body?.keys?.sorted() shouldContainExactly listOf("error", "status", "timestamp")
        response.body?.get("error") shouldBe "None"
    }

    /**
     * D-6A2a-13 ② — 관리 포트의 **비 GET** 관측. `OPEN-API-WRONG-METHOD-500`(API 포트에서 이미
     * 넘긴 OPEN)과 같은 계열이며, 본문이 일반 메시지뿐임을 여기서 잰다(응답에 우리 쪽 정보가
     * 실리면 이 단언이 붉어진다).
     */
    @Test
    @Order(2)
    fun `관리 포트의 비 GET 요청은 일반 오류 본문만 낸다`() {
        listOf(HttpMethod.POST, HttpMethod.DELETE).forEach { method ->
            val response =
                restTemplate.exchange(
                    management("/actuator/health"),
                    method,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )
            response.statusCode.value() shouldBe 500
            val body = response.body ?: ""
            listOf("postgres", "jdbc", "Exception", "readinessState", "diskSpace").forEach { leak ->
                body shouldNotContain leak
            }
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
