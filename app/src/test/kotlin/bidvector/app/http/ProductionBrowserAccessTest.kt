package bidvector.app.http

import bidvector.adapters.access.BcryptPassphraseHasher
import bidvector.app.adminDataSource
import bidvector.app.productionApplication
import bidvector.workflow.access.ClearPassphrase
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

private const val SESSION_PATH = "/api/session"
private const val LOGIN_NAME = "production-assembly-operator"
private const val PASSPHRASE = "production-assembly-fixture-passphrase"

/** bcrypt 블록 입력의 한계(72바이트)를 **넘는** 길이 — 그 위에서 거부의 모양이 같은지 잰다. */
private const val LONG_PASSPHRASE_BYTES = 100

/**
 * M7/7D — **출하 조립**에서 브라우저 인증을 잰다(실 DB · 실 KDF · 실 필터 등록).
 *
 * `BrowserSessionAuthenticationTest` 는 HTTP 층의 이중체 위에서 쿠키·CSRF 거동을 재고, 이
 * test 는 그 위 두 층을 더 본다: **조립에 실제로 무엇이 꽂혔는가**와 **DB 에 무엇이 남는가**.
 * 둘을 합쳐야 우회 ⑧(평문 저장 0)·감사 주체 규약·무회귀가 실측된다.
 *
 * 계정 행은 **admin 연결로 직접** 심는다 — 운영자 명령(`AccountCommandRunner`)은 끝나면
 * 프로세스를 종료하므로 test 가 그것을 부팅할 수 없다(그 명령 자체는
 * `adapters` 의 `AccountProvisioningIntegrationTest` 가 실 DB 로 잰다). 해시는 **실물**
 * [BcryptPassphraseHasher] 가 만든다.
 */
class ProductionBrowserAccessTest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "production-browser-access-test-fixture-credential"

        /** 정책 최소 비용 — 실 KDF 를 쓰되 test 를 느리게 하지 않는다(값의 축은 정책 test 가 잰다). */
        private const val TEST_HASH_COST = 10

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_browser_access_test")
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

    /** 매 test 전 세션·감사를 비우고 계정 하나를 심는다(행 격리). */
    @BeforeEach
    fun seedAccount() {
        val hash = BcryptPassphraseHasher(TEST_HASH_COST).hash(ClearPassphrase.wrap(PASSPHRASE))
        adminDataSource(postgres).connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE TABLE browser_session")
                statement.execute("TRUNCATE TABLE api_request_audit")
                // 세션 경로의 편집 축(F2)이 이 셋을 쓴다 — 비우지 않으면 revision·outbox 대조가
                // 실행 순서에 의존한다(`StrategyEditProductionE2ETest` 와 같은 목록).
                statement.execute("TRUNCATE TABLE edit_session, outbox, operator_strategy, operator_strategy_revision")
                statement.execute("DELETE FROM account")
            }
            connection
                .prepareStatement(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
                        "VALUES (?, ?, now(), now(), 0)",
                ).use { statement ->
                    statement.setString(1, LOGIN_NAME)
                    statement.setString(2, hash.encoded)
                    statement.executeUpdate()
                }
        }
    }

    @Test
    fun `출하 조립의 기본 거부 등록은 하나이고 꽂힌 인증 경로는 둘이다 — 집합 등식`() {
        val registrations = context.getBeansOfType(FilterRegistrationBean::class.java)
        val authFilters = registrations.values.map { it.filter }.filterIsInstance<RequestAuthenticationFilter>()

        authFilters.size shouldBe 1
        authFilters.single().configuredAuthenticators.map { it::class.java.name } shouldBe
            listOf(
                OperatorCredentialAuthenticator::class.java.name,
                BrowserSessionAuthenticator::class.java.name,
            )
    }

    @Test
    fun `실 KDF·실 DB 로 로그인하고 쿠키로 기존 endpoint 를 읽는다 — 감사 주체는 계정을 가리킨다`() {
        val session = signIn()

        exchange("/api/strategy", HttpMethod.GET, cookieOnly(session)).statusCode.value() shouldBe 200
        auditSubjects().contains("account:${accountId()}") shouldBe true
    }

    @Test
    fun `우회 ⑧ — DB 에 쿠키 평문이 없다(열 전수) · PK 는 그 값의 SHA-256 이다`() {
        val session = signIn()

        val rows = sessionRowsAsText()
        rows.shouldNotBeEmpty()
        rows.any { it.contains(session.cookie) } shouldBe false
        storedDigests() shouldBe setOf(sha256Hex(session.cookie))
    }

    @Test
    fun `우회 ⑨ — 비밀 문구 해시가 응답·감사에 실리지 않는다`() {
        val raw =
            restTemplate.exchange(
                url(SESSION_PATH),
                HttpMethod.POST,
                HttpEntity(loginBody(), jsonHeaders()),
                String::class.java,
            )

        (raw.body ?: "") shouldContain "csrf"
        (raw.body ?: "").contains(PASSPHRASE) shouldBe false
        (raw.body ?: "").contains("\$2a\$") shouldBe false
        auditSubjects().any { it.contains(PASSPHRASE) } shouldBe false
    }

    @Test
    fun `우회 ③ — 로그아웃은 서버 쪽 폐기를 남기고 그 쿠키는 다시 통하지 않는다`() {
        val session = signIn()

        exchange(SESSION_PATH, HttpMethod.DELETE, cookieAndStamp(session)).statusCode.value() shouldBe 204

        revokedCount() shouldBe 1
        exchange(SESSION_PATH, HttpMethod.GET, cookieOnly(session)).statusCode.value() shouldBe 401
    }

    @Test
    fun `우회 ② — CSRF 헤더 없는 DELETE 는 401 이고 세션은 살아 있다`() {
        val session = signIn()

        exchange(SESSION_PATH, HttpMethod.DELETE, cookieOnly(session)).statusCode.value() shouldBe 401

        revokedCount() shouldBe 0
        exchange(SESSION_PATH, HttpMethod.GET, cookieOnly(session)).statusCode.value() shouldBe 200
    }

    @Test
    fun `우회 ⑥ — 틀린 비밀 문구는 401 이고 실패 계수가 DB 에서 오른다`() {
        val refused = exchange(SESSION_PATH, HttpMethod.POST, jsonHeaders(), loginBody("wrong-passphrase-value"))

        refused.statusCode.value() shouldBe 401
        failedAttempts() shouldBe 1
    }

    @Test
    fun `한계를 넘는 긴 비밀 문구도 401 이다 — 500 이 아니고 실패 계수도 보통처럼 오른다`() {
        val long = "z".repeat(LONG_PASSPHRASE_BYTES)

        val refused = exchange(SESSION_PATH, HttpMethod.POST, jsonHeaders(), loginBody(long))

        // 앞 판은 여기서 500 이었다(KDF 라이브러리의 strict 전략이 던졌다) — 상태 코드가 갈리면
        // 그 자체가 「이 입력은 다르게 처리된다」는 신호이고, 거부의 모양을 하나로 둔 규율이 깨진다.
        refused.statusCode.value() shouldBe 401
        refused.body?.get("code") shouldBe "UNAUTHENTICATED"
        failedAttempts() shouldBe 1
    }

    @Test
    fun `세션 경로의 편집도 감사 주체와 이벤트 행위자가 같다 — 계정을 가리킨다`() {
        val session = signIn()
        val expected = "account:${accountId()}"

        val begun = editPost("/api/strategy/edit-sessions", """{"field":"CANDIDATE_LIMIT"}""", session, 201)
        val sessionId = begun["sessionId"] as String
        editPost(
            "/api/strategy/edit-sessions/$sessionId/value",
            """{"commandId":"v1","field":"CANDIDATE_LIMIT","count":11}""",
            session,
            200,
        )
        editPost(
            "/api/strategy/edit-sessions/$sessionId/confirm",
            """{"commandId":"c1","seenRevision":0}""",
            session,
            200,
        )

        // 앞 판은 outbox 행위자가 상수 `operator` 였다 — 같은 요청의 두 기록이 다른 사람을
        // 가리켰고, 7E 화면이 쓸 경로가 바로 이쪽이다.
        queryStrings("SELECT actor_kind FROM outbox").single() shouldBe "OPERATOR"
        queryStrings("SELECT actor_detail FROM outbox").single() shouldBe expected
        // 로그인 요청 자신의 감사 행은 `unauthenticated` 다(그 시점에 주체가 없다) — 대조는
        // **편집 요청들**의 주체 집합이다.
        queryStrings(
            "SELECT DISTINCT subject FROM api_request_audit WHERE path LIKE '/api/strategy/edit-sessions%'",
        ).toSet() shouldBe setOf(expected)
    }

    @Test
    fun `기존 운영자 자격 헤더 경로는 그대로다 — 무회귀`() {
        val headers =
            HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE) }

        exchange("/api/strategy", HttpMethod.GET, headers).statusCode.value() shouldBe 200
        auditSubjects().contains("operator") shouldBe true
    }

    // --- 호출·조회 헬퍼 -------------------------------------------------------

    private fun url(path: String): String = "http://localhost:$port$path"

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun loginBody(passphrase: String = PASSPHRASE): String =
        """{"loginName":"$LOGIN_NAME","passphrase":"$passphrase"}"""

    private fun signIn(): ProductionSession {
        val response = exchange(SESSION_PATH, HttpMethod.POST, jsonHeaders(), loginBody())
        response.statusCode.value() shouldBe 201
        val setCookie = requireNotNull(response.headers.getFirst(HttpHeaders.SET_COOKIE)) { "Set-Cookie 가 없다" }
        return ProductionSession(
            cookie = setCookie.substringAfter("${SessionCookieWriter.NAME}=").substringBefore(";"),
            stamp = response.body?.get("csrf") as? String ?: "",
        )
    }

    private fun cookieOnly(session: ProductionSession): HttpHeaders =
        HttpHeaders().apply { set(HttpHeaders.COOKIE, "${SessionCookieWriter.NAME}=${session.cookie}") }

    /** 쿠키 + CSRF + JSON — 세션 경로로 쓰기 endpoint 를 부르는 자리. */
    private fun editPost(
        path: String,
        json: String,
        session: ProductionSession,
        expectedStatus: Int,
    ): Map<String, Any?> {
        val headers = cookieAndStamp(session).apply { contentType = MediaType.APPLICATION_JSON }
        val response = exchange(path, HttpMethod.POST, headers, json)
        response.statusCode.value() shouldBe expectedStatus
        return response.body ?: emptyMap()
    }

    private fun cookieAndStamp(session: ProductionSession): HttpHeaders =
        cookieOnly(session).apply { set(BrowserSessionAuthenticator.CSRF_HEADER, session.stamp) }

    @Suppress("UNCHECKED_CAST")
    private fun exchange(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: String? = null,
    ): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            url(path),
            method,
            HttpEntity(body, headers),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    /** 출하 조립이 만든 `DataSource`(최소 권한 역할) — 읽을 수 있다는 것도 재는 사실이다. */
    private fun appDataSource(): DataSource = context.getBean(DataSource::class.java)

    private fun accountId(): Long = queryStrings("SELECT id FROM account").single().toLong()

    private fun auditSubjects(): List<String> = queryStrings("SELECT subject FROM api_request_audit")

    private fun storedDigests(): Set<String> = queryStrings("SELECT session_digest FROM browser_session").toSet()

    private fun revokedCount(): Int =
        queryStrings("SELECT COUNT(*) FROM browser_session WHERE revoked_at IS NOT NULL").single().toInt()

    private fun failedAttempts(): Int = queryStrings("SELECT failed_attempts FROM account").single().toInt()

    /** 세션 행을 **열 전수**로 이어 붙인 텍스트 — 어느 열에도 평문이 없음을 한 술어로 본다. */
    private fun sessionRowsAsText(): List<String> =
        queryStrings(
            "SELECT concat_ws('|', session_digest, account_id, csrf_digest, created_at, last_used_at, " +
                "idle_expires_at, absolute_expires_at, revoked_at) FROM browser_session",
        )

    private fun queryStrings(sql: String): List<String> {
        val values = mutableListOf<String>()
        appDataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    while (rs.next()) values += rs.getString(1)
                }
            }
        }
        return values
    }

    /** 저장된 해시와 쿠키 값의 관계를 **test 가 독립으로** 계산한다(구현의 함수를 부르지 않는다). */
    private fun sha256Hex(value: String): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private class ProductionSession(
        val cookie: String,
        val stamp: String,
    )
}
