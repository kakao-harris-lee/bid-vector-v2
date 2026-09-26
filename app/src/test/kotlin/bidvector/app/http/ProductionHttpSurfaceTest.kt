package bidvector.app.http

import bidvector.app.productionApplication
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/** 경로 변수 자리에 넣는 고정 값 — 어느 세션도 가리키지 않는다(형식 판정만 본다). */
private const val PATH_VARIABLE_PROBE = "surface-gate-probe"

private val PROBED_METHODS =
    listOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)

/**
 * Boot 가 스스로 등록하는 오류 경로 — 우리 계약의 표면이 아니다. 메서드 조건이 비어 있어
 * 「선언하지 않은 메서드」 자체가 없고, OpenAPI 에도 적지 않는다. **건너뛰는 이유를 이름으로
 * 남겨** 집합 등식의 한쪽에 세운다(조용히 빠지지 않는다).
 */
private val FRAMEWORK_PATHS = setOf("/error")

/**
 * `OPEN-API-WRONG-METHOD-500` 폐쇄 판정과 문서↔구현 표면 등식(M6/6A-2b D-6A2b-7·19·20·21).
 *
 * **모집단이 출하 조립이다(verifier r1 F-1 ③).** 이전 판은 test 전용 조립
 * (`HttpTestApplication`, 스캔 루트가 HTTP 층 하나)에서 매핑을 거뒀다 — 다른 패키지의 진짜
 * 컨트롤러가 들어와도 보이지 않았고, 그래서 「등록된 모든 매핑」이라는 말이 실제보다 좁았다.
 * 여기서는 `main()` 과 같은 조립 함수로 뜬 컨텍스트에서 거둔다.
 *
 * **집합 등식이 항진식이 아니다(F-3).** 두드린 경로만 `probed` 에 넣고, 건너뛴 경로는 이유가
 * 붙은 [FRAMEWORK_PATHS] 에만 둔다 — `probed ∪ 건너뜀 == 전체 매핑`. 수집에서 한 갈래가
 * 빠지면(예: 경로 변수 매핑) 이 등식이 곧바로 깨진다.
 */
@Suppress("UNCHECKED_CAST")
class ProductionHttpSurfaceTest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "surface-gate-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_surface_gate_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var context: ConfigurableApplicationContext
        private var port: Int = 0

        @JvmStatic
        @BeforeAll
        fun boot() {
            context =
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
                    ).run()
            port = (context as ServletWebServerApplicationContext).webServer?.port ?: error("web server가 뜨지 않았다")
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            context.close()
            postgres.stop()
        }
    }

    private val restTemplate: TestRestTemplate = TestRestTemplate()
    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    private fun url(path: String): String = "http://localhost:$port$path"

    /** 매핑 → 선언된 메서드 집합. 경로 변수는 구체 값으로 치환한다(RestTemplate 이 템플릿으로 읽지 않게). */
    private fun mappings(): Map<String, Set<String>> {
        val handlerMapping = context.getBean(RequestMappingHandlerMapping::class.java)
        val collected = mutableMapOf<String, MutableSet<String>>()
        handlerMapping.handlerMethods.keys.forEach { info ->
            val methods = info.methodsCondition.methods.map(RequestMethod::name)
            info.pathPatternsCondition?.patterns.orEmpty().forEach { pattern ->
                val path = pattern.patternString.replace(PATH_VARIABLE_PATTERN, PATH_VARIABLE_PROBE)
                collected.getOrPut(path) { mutableSetOf() } += methods
            }
        }
        return collected
    }

    private fun headers(
        contentType: MediaType?,
        accept: MediaType? = null,
    ): HttpHeaders =
        HttpHeaders().apply {
            set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE)
            contentType?.let { this.contentType = it }
            accept?.let { this.accept = listOf(it) }
        }

    private fun exchange(
        path: String,
        method: HttpMethod,
        body: String? = null,
        contentType: MediaType? = null,
        accept: MediaType? = null,
    ): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            url(path),
            method,
            HttpEntity(body, headers(contentType, accept)),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    @Test
    fun `선언하지 않은 메서드는 500 이 아니라 405 다 — 출하 조립의 매핑 전수`() {
        val mappings = mappings()
        mappings.keys.shouldNotBeEmpty()
        // 경로 변수 매핑이 모집단에 실제로 들어 있는지 — MU4(수집에서 그 갈래를 빼는 변이)의 직접 앵커.
        mappings.keys shouldContainAll
            setOf(
                "/api/strategy",
                "/api/strategy/edit-sessions",
                "/api/strategy/edit-sessions/$PATH_VARIABLE_PROBE/confirm",
            )

        val probed = mutableSetOf<String>()
        val statuses = mutableMapOf<String, Int>()
        mappings.forEach { (path, declared) ->
            if (path in FRAMEWORK_PATHS) return@forEach
            val undeclared = PROBED_METHODS.filterNot { it.name() in declared }
            if (undeclared.isEmpty()) return@forEach
            probed += path
            undeclared.forEach { method -> statuses["$method $path"] = exchange(path, method).statusCode.value() }
        }

        // 항진식이 아니다 — 두드린 경로 ∪ 이유가 붙은 제외 == 전체 매핑.
        probed + FRAMEWORK_PATHS shouldBe mappings.keys
        statuses.filterValues { it == 500 } shouldBe emptyMap()
        statuses.filterValues { it != 405 } shouldBe emptyMap()
    }

    @Test
    fun `405 응답 본문은 ErrorBody 이고 Allow 헤더가 함께 온다`() {
        val response =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.DELETE,
                HttpEntity<Void>(headers(null)),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 405
        response.body?.keys shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.METHOD_NOT_ALLOWED
        response.headers.getFirst("Allow") shouldBe "GET"
    }

    @Test
    fun `본문을 받는 매핑은 미지원 미디어 타입·깨진 JSON·누락에 500 을 내지 않는다 — 전수`() {
        val postPaths = mappings().filterValues { "POST" in it }.keys
        postPaths.shouldNotBeEmpty()

        val outcomes = mutableMapOf<String, Pair<Int, Any?>>()
        postPaths.forEach { path ->
            val plainText = exchange(path, HttpMethod.POST, "not json", MediaType.TEXT_PLAIN)
            val brokenJson = exchange(path, HttpMethod.POST, "{", MediaType.APPLICATION_JSON)
            val emptyObject = exchange(path, HttpMethod.POST, "{}", MediaType.APPLICATION_JSON)
            outcomes["415 $path"] = plainText.statusCode.value() to plainText.body?.get("code")
            outcomes["400 $path"] = brokenJson.statusCode.value() to brokenJson.body?.get("code")
            outcomes["누락 $path"] = emptyObject.statusCode.value() to emptyObject.body?.get("code")
        }

        outcomes.filterKeys { it.startsWith("415 ") }.values.toSet() shouldBe
            setOf(415 to ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        outcomes.filterKeys { it.startsWith("400 ") }.values.toSet() shouldBe
            setOf(400 to ErrorCode.INVALID_REQUEST)
        outcomes.filterKeys { it.startsWith("누락 ") }.values.toSet() shouldBe
            setOf(400 to ErrorCode.INVALID_REQUEST)
    }

    /**
     * D-6A2b-21 — `Accept` 협상 실패 축. 이 앱은 JSON 하나만 낸다: 받아들일 수 없는 타입을
     * 요구하면 406 이고, 500 으로 새지 않는다.
     */
    @Test
    fun `받아들일 수 없는 Accept 는 406 이고 500 이 아니다 — 전수`() {
        val statuses =
            mappings()
                .filterKeys { it !in FRAMEWORK_PATHS }
                .filterValues { "GET" in it }
                .keys
                .associateWith { path ->
                    exchange(path, HttpMethod.GET, accept = MediaType.APPLICATION_PDF).statusCode.value()
                }

        statuses.keys.shouldNotBeEmpty()
        statuses.values.toSet() shouldBe setOf(406)
        // **본문은 비어 있다(실측).** 클라이언트가 JSON 을 받지 않겠다고 했으므로 우리 `ErrorBody`
        // 도 쓸 수 없다 — Spring 은 상태만 내고 끝낸다. 누출 축에서는 그것이 가장 안전한 결과이고,
        // 그래서 여기서는 「우리 형태의 본문」이 아니라 **「어떤 본문도 없다」**를 잠근다.
        val raw =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(headers(null, MediaType.APPLICATION_PDF)),
                String::class.java,
            )
        raw.statusCode.value() shouldBe 406
        (raw.body ?: "") shouldBe ""
    }

    /**
     * C-5b — **문서의 (경로, 메서드) 집합 == 출하 조립의 등록 매핑 집합**. 새 컨트롤러가 문서
     * 없이 들어오거나 문서가 없는 경로를 약속하면 이 등식이 깨진다. 수집기는 위 형식 게이트와
     * 같은 것을 쓴다 — 모집단이 하나다.
     */
    @Test
    fun `문서의 경로·메서드 집합이 등록된 매핑 집합과 같다`() {
        val documented =
            (spec["paths"] as Map<String, Any?>)
                .filterKeys { it != UNMATCHED_PATH_TEMPLATE }
                .flatMap { (path, item) ->
                    val concrete = path.replace(PATH_VARIABLE_PATTERN, PATH_VARIABLE_PROBE)
                    (item as Map<String, Any?>).keys.map { method -> "${method.uppercase()} $concrete" }
                }.toSet()

        val registered =
            mappings()
                .filterKeys { it !in FRAMEWORK_PATHS }
                .flatMap { (path, methods) -> methods.map { "$it $path" } }
                .toSet()

        documented shouldBe registered
    }

    @Test
    fun `201 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response =
            exchange(
                "/api/strategy/edit-sessions",
                HttpMethod.POST,
                """{"field":"CANDIDATE_LIMIT"}""",
                MediaType.APPLICATION_JSON,
            )

        response.statusCode.value() shouldBe 201
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("EditSessionResponse")
        response.body?.values?.none(::containsNestedObject) shouldBe true
    }

    @Test
    fun `404 응답의 최상위 키 집합이 ErrorBody 계약과 일치한다`() {
        val response = exchange("/api/strategy/edit-sessions/no-such-session", HttpMethod.GET)

        response.statusCode.value() shouldBe 404
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.SESSION_NOT_FOUND
    }
}

/** 미매핑 경로는 문서화 목적의 템플릿이라 등록 매핑에 대응이 없다(D-6A1-21). */
private const val UNMATCHED_PATH_TEMPLATE = "/{unmatched}"
