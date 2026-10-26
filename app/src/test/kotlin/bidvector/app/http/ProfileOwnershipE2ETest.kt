package bidvector.app.http

import bidvector.adapters.access.BcryptPassphraseHasher
import bidvector.app.adminDataSource
import bidvector.app.productionApplication
import bidvector.workflow.access.ClearPassphrase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

/**
 * **두 계정의 소유 경계**(M7/7C④ 2b r2, v F2) — 출하 조립 · 실 DB · 실 세션으로 잰다.
 *
 * 2a 의 어댑터 KDoc 은 「쓰기의 소유 경계는 호출자 전제이고 그 전제는 **2b 의 controller 가 자기
 * test 로 진다**」고 적었다. 2a 가 실제로 잰 것은 **SQL 의 계정 필터**이고, 「controller 가 어떤
 * 계정을 넘기는가」는 계정이 **하나뿐인** 이중체에서는 갈라낼 수 없다 — 붙든 계정과 세션 계정이 같은
 * 값이기 때문이다(변이 MB1 이 그 틈으로 살아남았다). 그래서 여기서는 **로그인 둘**로 잰다.
 *
 * `ProfileEndpointTest`(이중체)와 축이 다르다: 그쪽은 요청 판독 · 응답 모양 · 상태 코드를, 이쪽은
 * **누구의 행이 보이는가**를 잰다.
 */
class ProfileOwnershipE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "profile-ownership-test-fixture-credential"
        private const val TEST_HASH_COST = 10
        private const val PASSPHRASE = "profile-ownership-fixture-passphrase"
        private const val LOGIN_A = "profile-owner-a"
        private const val LOGIN_B = "profile-owner-b"
        private const val OK = 200
        private const val CREATED = 201
        private const val NOT_FOUND = 404
        private val QUALIFICATION_FACTS = AccountScopedRoutes.PROFILE + AccountScopedRoutes.PROFILE_QUALIFICATION_FACTS
        private val BUSINESS_DETAILS = AccountScopedRoutes.PROFILE + AccountScopedRoutes.PROFILE_BUSINESS_DETAILS
        private val CAPACITIES = AccountScopedRoutes.PROFILE + AccountScopedRoutes.PROFILE_CAPACITIES
        private val CAPACITIES_REMOVE = AccountScopedRoutes.PROFILE + AccountScopedRoutes.PROFILE_CAPACITIES_REMOVE
        private val CSRF = Regex("\"csrf\"\\s*:\\s*\"([^\"]+)\"")

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_profile_ownership_test")
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

    /** 매 test 전 **두 계정**을 새로 심는다 — 프로필 행은 계정 삭제와 함께 사라진다(외래 키). */
    @BeforeEach
    fun seedTwoAccounts() {
        val hash = BcryptPassphraseHasher(TEST_HASH_COST).hash(ClearPassphrase.wrap(PASSPHRASE))
        adminDataSource(postgres).connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE TABLE browser_session")
                statement.execute("TRUNCATE TABLE api_request_audit")
                statement.execute("DELETE FROM account_construction_capacity")
                statement.execute("DELETE FROM account_business_profile")
                statement.execute("DELETE FROM operator_profile")
                statement.execute("DELETE FROM account")
            }
            listOf(LOGIN_A, LOGIN_B).forEach { login ->
                connection
                    .prepareStatement(
                        "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, " +
                            "failed_attempts) VALUES (?, ?, now(), now(), 0)",
                    ).use { statement ->
                        statement.setString(1, login)
                        statement.setString(2, hash.encoded)
                        statement.executeUpdate()
                    }
            }
        }
    }

    @Test
    fun `A 가 쓴 사업 정보와 시평액이 B 의 조회에 보이지 않는다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)

        write(BUSINESS_DETAILS, detailsBody("합성상호A"), a) shouldBe OK
        write(CAPACITIES, capacityBody("합성면허A"), a) shouldBe OK

        val seenByB = read(AccountScopedRoutes.PROFILE, b)
        seenByB shouldContain "\"tradeName\":null"
        seenByB shouldNotContain "합성상호A"
        read(CAPACITIES, b) shouldBe "[]"

        // A 쪽은 그대로다 — B 의 조회가 A 의 행을 건드리지 않았다.
        read(AccountScopedRoutes.PROFILE, a) shouldContain "합성상호A"
    }

    @Test
    fun `B 는 A 의 면허명을 지울 수 없다 — 404 이고 A 의 행이 남는다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)
        write(CAPACITIES, capacityBody("합성면허A"), a) shouldBe OK

        write(CAPACITIES_REMOVE, """{"licenseName":"합성면허A"}""", b) shouldBe NOT_FOUND

        read(CAPACITIES, a) shouldContain "합성면허A"
    }

    @Test
    fun `두 계정이 같은 면허명을 각자 둔다 — 한쪽 삭제가 다른 쪽을 지우지 않는다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)
        write(CAPACITIES, capacityBody("같은면허"), a) shouldBe OK
        write(CAPACITIES, capacityBody("같은면허"), b) shouldBe OK

        write(CAPACITIES_REMOVE, """{"licenseName":"같은면허"}""", b) shouldBe OK

        read(CAPACITIES, a) shouldContain "같은면허"
        read(CAPACITIES, b) shouldBe "[]"
    }

    /**
     * **알려진 제한을 test 로 고정한다**(§5.5 제한 5 · v F3): V12 자격 사실은 싱글턴이라 **계정 경계
     * 밖**이다. 어느 계정의 세션이 써도 모든 계정의 조회에 보인다. 이 test 가 붉어지면 그 제한이
     * 사라졌다는 뜻이고, 그때는 장부와 OpenAPI 문면을 함께 고쳐야 한다.
     */
    @Test
    fun `자격 사실은 계정 경계 밖이다 — B 가 쓰면 A 의 조회에도 보인다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)

        write(QUALIFICATION_FACTS, qualificationBody("합성면허B"), b) shouldBe OK

        read(AccountScopedRoutes.PROFILE, a) shouldContain "합성면허B"
    }

    private fun detailsBody(tradeName: String): String =
        """{"tradeName":"$tradeName","headquartersRegion":"SEJONG","naraRegistration":"UNANSWERED"}"""

    private fun capacityBody(license: String): String =
        """{"licenseName":"$license","amountWon":1000,"assessmentYear":2026}"""

    private fun qualificationBody(license: String): String =
        """{"businessTypes":[],"licensesDeclared":true,"licenseNames":["$license"],"regionTerms":[]}"""

    private fun write(
        path: String,
        json: String,
        session: OwnerSession,
    ): Int {
        val headers = cookieAndStamp(session).apply { contentType = MediaType.APPLICATION_JSON }
        return exchange(path, HttpMethod.POST, headers, json).statusCode.value()
    }

    private fun read(
        path: String,
        session: OwnerSession,
    ): String {
        val response = exchange(path, HttpMethod.GET, cookieOnly(session))
        response.statusCode.value() shouldBe OK
        return response.body.orEmpty()
    }

    private fun signIn(login: String): OwnerSession {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response =
            exchange(
                "/api/session",
                HttpMethod.POST,
                headers,
                """{"loginName":"$login","passphrase":"$PASSPHRASE"}""",
            )
        response.statusCode.value() shouldBe CREATED
        val setCookie = requireNotNull(response.headers.getFirst(HttpHeaders.SET_COOKIE)) { "Set-Cookie 가 없다" }
        return OwnerSession(
            cookie = setCookie.substringAfter("${SessionCookieWriter.HOST_PREFIXED_NAME}=").substringBefore(";"),
            stamp = requireNotNull(CSRF.find(response.body.orEmpty())?.groupValues?.get(1)) { "csrf 가 없다" },
        )
    }

    private fun cookieOnly(session: OwnerSession): HttpHeaders =
        HttpHeaders().apply { set(HttpHeaders.COOKIE, "${SessionCookieWriter.HOST_PREFIXED_NAME}=${session.cookie}") }

    private fun cookieAndStamp(session: OwnerSession): HttpHeaders =
        cookieOnly(session).apply { set(BrowserSessionAuthenticator.CSRF_HEADER, session.stamp) }

    private fun exchange(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: String? = null,
    ): ResponseEntity<String> =
        restTemplate.exchange("http://localhost:$port$path", method, HttpEntity(body, headers), String::class.java)

    private class OwnerSession(
        val cookie: String,
        val stamp: String,
    )
}
