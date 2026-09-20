package bidvector.app.http

import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * D-6A1-27 시정(verifier r1 HIGH — F-1) — 그동안 http test 넷(`OperatorAuthenticationTest`·
 * `RequestAuditFilterTest`·`OpenApiContractTest`·`ConstantTimeComparisonStructureTest`)이
 * 전부 [HttpTestApplication](test 전용 조립)을 띄워 필터 등록을 **다시 선언**했다.
 * [BidVectorApplication]을 부팅하는 test가 **0**이라 production 자격증명 필터 bean을
 * 삭제(9줄)해도 `./gradlew check`가 BUILD SUCCESSFUL이었다(verifier 실측).
 *
 * 이 test는 **`main()`과 같은 조립 호출**(`SpringApplicationBuilder(BidVectorApplication
 * ::class.java)`, 같은 [PRODUCTION_DISPATCH_PROPERTIES] 참조)로 production 조립 그 자체를
 * 부팅해 위협 모델 (a)·(c)를 잰다. 닫힘 판정(D-6A1-27 문면 그대로) — **production 필터
 * bean을 삭제했을 때 전건 `check`가 RED**인가. 더해 ⓐ production `urlPatterns`를 아무
 * 것도 안 맞는 패턴으로 좁히면 RED(첫 test) ⓑ [PRODUCTION_DISPATCH_PROPERTIES]를 비우면
 * RED(둘째 test — 미매핑 경로가 컨테이너 ERROR 재디스패치로 새는 형태가 실제로 달라진다).
 *
 * `adapters` 모듈의 `PersistenceTestSupport`를 재사용하지 않는다 — `app`은 `adapters`의
 * test 소스에 의존하지 않는다(모듈 경계, 이 저장소는 testFixtures 관례를 쓰지 않는다,
 * scope.md「Phase 3 중 계약 정정」과 같은 이유). 같은 기법(수동 컨테이너 시작/종료,
 * `PersistenceTestSupport`와 같은 형태)을 이 파일 안에서 반복한다.
 */
class ProductionAssemblyAuthAuditTest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "production-assembly-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_prod_assembly_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var context: ConfigurableApplicationContext
        private var port: Int = 0

        @JvmStatic
        @BeforeAll
        fun boot() {
            context =
                SpringApplicationBuilder(BidVectorApplication::class.java)
                    .properties(
                        PRODUCTION_DISPATCH_PROPERTIES +
                            mapOf(
                                "server.port" to "0",
                                "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                                "bidvector.persistence.username" to postgres.username,
                                "bidvector.persistence.credential" to postgres.password,
                                "operator.credential.value" to TEST_CREDENTIAL_VALUE,
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

    private fun url(path: String): String = "http://localhost:$port$path"

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE) }

    @Test
    fun `D-6A1-27 — production 조립에서 등록된 모든 endpoint가 자격증명 없이는 401이다 — 기계 전수`() {
        val handlerMapping = context.getBean(RequestMappingHandlerMapping::class.java)
        val paths =
            handlerMapping.handlerMethods.keys
                .mapNotNull { it.pathPatternsCondition }
                .flatMap { it.patterns }
                .map { it.patternString }
                .toSet()

        // 술어가 공허하게 참인 회귀를 막는다 — 최소 우리 endpoint 하나는 있어야 한다.
        paths.shouldNotBeEmpty()
        // code-reviewer MEDIUM 시정(OperatorAuthenticationTest와 같은 관례) — D-6A1-21 ①을
        // shouldNotBeEmpty()만으로 두지 않는다.
        paths shouldContain "/error"

        paths.forEach { path ->
            val response = restTemplate.getForEntity(url(path), String::class.java)
            response.statusCode.value() shouldBe 401
        }
    }

    @Test
    fun `D-6A1-27 — production 조립에서 올바른 자격증명은 통과하고 미매핑 경로도 같은 REQUEST 디스패치에서 끝난다`() {
        val authorized =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                String::class.java,
            )
        authorized.statusCode.value() shouldBe 200

        @Suppress("UNCHECKED_CAST")
        val unmapped =
            restTemplate.exchange(
                url("/does-not-exist"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        unmapped.statusCode.value() shouldBe 404
        unmapped.body?.get("code") shouldBe ErrorCode.NOT_FOUND
        // D-6A1-21 — Boot 기본 BasicErrorController/whitelabel 응답의 표식(timestamp·trace)이
        // 없다는 것은 컨테이너의 별도 ERROR 재디스패치가 아니라 우리 GlobalErrorHandler가
        // 같은 REQUEST 디스패치 안에서 처리했다는 실측이다. PRODUCTION_DISPATCH_PROPERTIES가
        // 비면 이 두 키가 사라져 whitelabel 표식이 나타난다(mutation closure).
        unmapped.body?.containsKey("timestamp") shouldBe false
        unmapped.body?.containsKey("trace") shouldBe false
    }
}
