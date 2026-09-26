package bidvector.app.http

import bidvector.app.productionApplication
import bidvector.app.wiring.StrategyEditExecutor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

private const val EDIT_SESSIONS = "/api/strategy/edit-sessions"

/**
 * D-6A2b-3·5·13 — **출하 조립 그대로** 편집 왕복을 돈다(`main()` 과 같은 조립 함수
 * `productionApplication()`, 실 Postgres). `HttpTestApplication` 기반 test 는 커넥션
 * 경계를 이중체로 바꾸므로 「배선이 실제로 서고 값이 DB 에 남는가」를 재지 못한다 —
 * 그 자리를 이 test 가 진다.
 *
 * 함께 잰다: ① 쓰기 경로가 인증 경계 **아래**에 있다(무자격 401 + DB 행 0) ② 감사 기록의
 * 주체와 이벤트 봉투의 행위자가 **같은 값**이다(D-6A2b-5 — 상수 두 자리가 어긋나면 그
 * 사실이 여기서 드러난다: 두 값을 문면으로 맞춘다는 약속 대신 실측으로 잠근다)
 * ③ 복원한 스캔 제외 필터(D-6A2b-9)가 우리 빈을 걷어내지 않았다.
 */
@Suppress("UNCHECKED_CAST")
class StrategyEditProductionE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "edit-e2e-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_edit_e2e_test")
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

    private fun url(path: String): String = "http://localhost:$port$path"

    private fun dataSource(): DataSource = context.getBean(DataSource::class.java)

    @BeforeEach
    fun resetTables() {
        dataSource().connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "TRUNCATE TABLE api_request_audit, edit_session, outbox, " +
                        "operator_strategy, operator_strategy_revision",
                )
            }
        }
    }

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun post(
        path: String,
        json: String,
        withCredential: Boolean = true,
    ): ResponseEntity<Map<String, Any?>> {
        val requestHeaders =
            if (withCredential) headers() else HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return restTemplate.exchange(
            url(path),
            HttpMethod.POST,
            HttpEntity(json, requestHeaders),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    private fun get(path: String): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            url(path),
            HttpMethod.GET,
            HttpEntity<Void>(headers()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    private fun <T> queryFirst(
        sql: String,
        read: (java.sql.ResultSet) -> T,
    ): T? =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs -> if (rs.next()) read(rs) else null }
            }
        }

    private fun count(sql: String): Long = queryFirst(sql) { it.getLong(1) } ?: 0L

    @Test
    fun `출하 조립에서 begin → value → confirm 이 전략을 바꾸고 이벤트를 남긴다`() {
        val sessionId = post(EDIT_SESSIONS, """{"field":"CANDIDATE_LIMIT"}""").body?.get("sessionId") as String

        post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"v1","field":"CANDIDATE_LIMIT","count":11}""")
            .statusCode
            .value() shouldBe 200
        val confirmed = post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c1","seenRevision":0}""")

        confirmed.statusCode.value() shouldBe 200
        confirmed.body?.get("state") shouldBe "APPLIED"

        val strategy = get("/api/strategy").body
        strategy?.get("candidateLimit") shouldBe 11
        strategy?.get("revision") shouldBe 1
        count("SELECT count(*) FROM outbox WHERE idempotency_key = 'strategy-updated-1'") shouldBe 1L
        queryFirst("SELECT state FROM edit_session") { it.getString(1) } shouldBe "APPLIED"
    }

    @Test
    fun `감사 주체와 이벤트 봉투의 행위자가 같은 값이다 — 상수 두 자리의 실측 대조`() {
        val sessionId = post(EDIT_SESSIONS, """{"field":"CANDIDATE_LIMIT"}""").body?.get("sessionId") as String
        post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"v1","field":"CANDIDATE_LIMIT","count":11}""")
        post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c1","seenRevision":0}""")

        val auditSubject = queryFirst("SELECT DISTINCT subject FROM api_request_audit") { it.getString(1) }
        val actorKind = queryFirst("SELECT actor_kind FROM outbox") { it.getString(1) }
        val actorDetail = queryFirst("SELECT actor_detail FROM outbox") { it.getString(1) }

        auditSubject shouldNotBe null
        actorKind shouldBe "OPERATOR"
        actorDetail shouldBe auditSubject
    }

    @Test
    fun `무자격 쓰기는 401 이고 세션 행을 만들지 않는다`() {
        val anonymous = post(EDIT_SESSIONS, """{"field":"CANDIDATE_LIMIT"}""", withCredential = false)

        anonymous.statusCode.value() shouldBe 401
        anonymous.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
        count("SELECT count(*) FROM edit_session") shouldBe 0L
        count("SELECT count(*) FROM operator_strategy") shouldBe 0L
    }

    /** D-6A2b-9 — 되살린 스캔 제외 필터 둘이 우리 빈을 걷어내지 않았다(빈 집합 실측). */
    @Test
    fun `출하 조립에 편집 실행기 빈이 있다`() {
        context.getBean(StrategyEditExecutor::class.java) shouldNotBe null
    }
}
