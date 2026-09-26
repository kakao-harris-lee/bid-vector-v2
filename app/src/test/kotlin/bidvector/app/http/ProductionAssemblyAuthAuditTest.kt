package bidvector.app.http

import bidvector.app.productionApplication
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import javax.sql.DataSource

/**
 * D-6A1-27 시정(verifier r1 HIGH — F-1) — 그동안 http test 넷(`OperatorAuthenticationTest`·
 * `RequestAuditFilterTest`·`OpenApiContractTest`·`ConstantTimeComparisonStructureTest`)이
 * 전부 [HttpTestApplication](test 전용 조립)을 띄워 필터 등록을 **다시 선언**했다.
 * `BidVectorApplication`을 부팅하는 test가 **0**이라 production 자격증명 필터 bean을
 * 삭제(9줄)해도 `./gradlew check`가 BUILD SUCCESSFUL이었다(verifier 실측).
 *
 * 이 test는 **`main()`과 같은 조립 호출**([bidvector.app.productionApplication] — M6/6A-2a 부터
 * `main()`과 이 test 가 공유하는 유일한 조립 함수)로 production 조립 그 자체를
 * 부팅해 위협 모델 (a)·(c)를 잰다. 닫힘 판정(D-6A1-27 문면 그대로) — **production 필터
 * bean을 삭제했을 때 전건 `check`가 RED**인가. 더해 ⓐ production `urlPatterns`를 아무
 * 것도 안 맞는 패턴으로 좁히면 RED(첫 test) ⓑ `PRODUCTION_DISPATCH_PROPERTIES`를 비우면
 * RED(둘째 test — 미매핑 경로가 컨테이너 ERROR 재디스패치로 새는 형태가 실제로 달라진다).
 *
 * `adapters` 모듈의 `PersistenceTestSupport`를 재사용하지 않는다 — `app`은 `adapters`의
 * test 소스에 의존하지 않는다(모듈 경계, 이 저장소는 testFixtures 관례를 쓰지 않는다,
 * scope.md「Phase 3 중 계약 정정」과 같은 이유). 같은 기법(수동 컨테이너 시작/종료,
 * `PersistenceTestSupport`와 같은 형태)을 이 파일 안에서 반복한다.
 *
 * **D-6A1-40 시정(verifier r2 레인 B HIGH) — (a)만 재고 (c)는 안 쟀다.** 위 두 test는
 * 상태 코드·body 키만 확인해 production **audit 필터** bean을 삭제해도 전건 `check`가
 * 초록이었다(배포 앱이 audit 행 0건을 남겨도 무엇도 안 붉음). `ApiAuditStore`는
 * (2b) 「닫는다」에 따라 읽기 메서드가 없으므로(D-6A1-7, 추가 전용 불변식을 이 test가
 * 깨지 않는다) 이 test는 **자신의 DataSource로 `api_request_audit`를 직접 조회**해
 * 위협 모델 (c)를 잰다 — production 조립이 실제로 만든 `DataSource` bean을 그대로 쓴다
 * (`PersistenceWiring`이 유일한 조립 지점이므로 별도 연결 정보를 다시 만들지 않는다).
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
                productionApplication()
                    .properties(
                        mapOf(
                            "server.port" to "0",
                            // M6/6A-2a — 관리 포트 기본값(8081)을 test 가 점유하지 않는다(병렬 fork 충돌).
                            // `0` 은 커널이 고르게 한다 — 미리 고른 번호를 닫고 다시 bind 하는
                            // 사이의 틈(TOCTOU)을 만들지 않는다(code-review r1 LOW). 이 test 는
                            // 관리 포트를 부르지 않으므로 실제 번호를 읽을 필요가 없다.
                            "management.server.port" to "0",
                            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                            "bidvector.persistence.username" to postgres.username,
                            "bidvector.persistence.credential" to postgres.password,
                            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                            // M6/6A-3+6F-3 D-6A3-7 — `EvaluationWiring`이 이 값 없이는 기동하지
                            // 않는다(기본값 없음, fail-fast). 이 test는 평가 endpoint를 부르지
                            // 않지만 production 조립 전체가 뜨려면 모든 `@ConfigurationProperties`가
                            // 바인딩돼야 한다.
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

    /** D-6A1-40 — 매 test 시작 전 표를 비운다(테스트 간 행 격리, `TRUNCATE`는 append-only
     * 불변식을 어기지 않는다 — production 코드가 아니라 test fixture 초기화다). */
    @BeforeEach
    fun resetAudit() {
        auditDataSource().connection.use { connection ->
            connection.createStatement().use { it.execute("TRUNCATE TABLE api_request_audit") }
        }
    }

    private fun url(path: String): String = "http://localhost:$port$path"

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE) }

    /** production 조립이 실제로 만든 `DataSource` bean — `PersistenceWiring`이 유일한 조립 지점. */
    private fun auditDataSource(): DataSource = context.getBean(DataSource::class.java)

    private fun auditRowCount(): Int =
        auditDataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM api_request_audit").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun auditRowCountByStatus(statusCode: Int): Int =
        auditDataSource().connection.use { connection ->
            val sql = "SELECT COUNT(*) FROM api_request_audit WHERE status_code = ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, statusCode)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

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

    /**
     * D-6A1-40 — 위협 모델 (c)를 production 조립에서 직접 잰다: 성공·인증 실패·예외(미매핑)
     * 각각 `api_request_audit` 행이 **정확히 하나**(0도 2도 아닌). 세 상태 코드가 서로
     * 달라 개별 카운트와 합계 둘 다로 겹쳐 세거나 놓치지 않았음을 확인한다.
     */
    @Test
    fun `D-6A1-40 — production 조립에서 성공·인증실패·예외 각각 api_request_audit 행이 정확히 하나다`() {
        val authorized =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                String::class.java,
            )
        authorized.statusCode.value() shouldBe 200

        val unauthenticated = restTemplate.getForEntity(url("/api/strategy"), String::class.java)
        unauthenticated.statusCode.value() shouldBe 401

        val unmapped =
            restTemplate.exchange(
                url("/does-not-exist"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                String::class.java,
            )
        unmapped.statusCode.value() shouldBe 404

        auditRowCountByStatus(200) shouldBe 1
        auditRowCountByStatus(401) shouldBe 1
        auditRowCountByStatus(404) shouldBe 1
        auditRowCount() shouldBe 3
    }
}
