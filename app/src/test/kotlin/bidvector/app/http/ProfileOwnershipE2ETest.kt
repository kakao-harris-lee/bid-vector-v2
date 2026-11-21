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

/** M9/OPS-10 — 이 조립이 쓰는 행에 찍히는 릴리스 라벨(test 고정값). */
private const val TEST_RELEASE_LABEL = "profile-ownership-e2e-release"

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
        private val SESSION_ID = Regex("\"sessionId\"\\s*:\\s*\"([^\"]+)\"")
        private val EDIT_SESSIONS = STRATEGY_EDIT_SESSIONS

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
                            // M9/OPS-10 — `bidvector.release.sha` 는 기본값이 없다(`ReleaseWiring`).
                            // 값을 주지 않으면 출하 조립이 바인딩 실패로 뜨지 않는다.
                            "bidvector.release.sha" to TEST_RELEASE_LABEL,
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
                // M15/15A — 네 표가 계정을 FK 로 참조하므로 계정 삭제 전에 비운다.
                statement.execute("DELETE FROM edit_session")
                statement.execute("DELETE FROM operator_strategy_revision")
                statement.execute("DELETE FROM operator_strategy")
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
     * **주장이 뒤집혔다**(M15/15A — V28). 앞 판은 「V12 자격 사실은 싱글턴이라 계정 경계 **밖**이다」를
     * 알려진 제한으로 고정했다(§5.5 제한 5 · v F3). 이 slice 가 그 표를 계정 소유로 옮겼으므로 같은
     * 자리에서 같은 시나리오로 **반대**를 잰다 — test 를 지우지 않고 주장을 바꾼 이유는, 지우면
     * 「그 제한이 언제 어떻게 사라졌는가」가 이력에서 사라지기 때문이다.
     *
     * **양방향으로 잰다**: 한 방향만 보면 「쓰기가 아무 데도 안 간다」는 구현도 초록이다.
     */
    @Test
    fun `자격 사실은 계정 경계 안이다 — B 가 쓴 면허가 A 에 보이지 않고 그 반대도 같다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)

        write(QUALIFICATION_FACTS, qualificationBody("합성면허B"), b) shouldBe OK
        write(QUALIFICATION_FACTS, qualificationBody("합성면허A"), a) shouldBe OK

        val seenByA = read(AccountScopedRoutes.PROFILE, a)
        seenByA shouldContain "합성면허A"
        seenByA shouldNotContain "합성면허B"

        val seenByB = read(AccountScopedRoutes.PROFILE, b)
        seenByB shouldContain "합성면허B"
        seenByB shouldNotContain "합성면허A"
    }

    /**
     * **격리 축 — 자격 사실 미설정은 계정마다 선다.** B 만 쓰면 A 의 조회는 「아직 설정 안 함」이고
     * 그 값은 B 의 행을 빌려 온 값이 아니다. 위 test 의 음성 대조다: 「빌려 오지 않는다」와 「아무도
     * 아무것도 못 읽는다」를 가른다(B 는 자기 값을 읽는다).
     */
    @Test
    fun `행이 없는 계정의 자격 사실은 미설정이다 — 남의 행을 빌려 오지 않는다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)

        write(QUALIFICATION_FACTS, qualificationBody("합성면허B"), b) shouldBe OK

        // 행이 없는 계정은 **미설정 기본값**을 본다 — 「선언한 면허가 없다」가 아니라 「아직 아무것도
        // 적지 않았다」이고, 그 wire 표현은 15A 가 만들지 않았다(D-6F6-3, 행 없음 = 포트가 `null`).
        // 재는 것은 그 기본값이 **남의 행으로 채워지지 않는다**는 것이다.
        val seenByA = read(AccountScopedRoutes.PROFILE, a)
        seenByA shouldNotContain "합성면허B"
        seenByA shouldContain "\"licenseNames\":[]"
        read(AccountScopedRoutes.PROFILE, b) shouldContain "합성면허B"
    }

    /**
     * **격리 축 — 전략.** B 가 편집 세션으로 감시 조건을 바꿔도 A 의 전략 조회에 보이지 않는다.
     * 전략은 쓰기 경로가 편집 세션 하나뿐이라(STR-07) 이 여정이 곧 「계정별 쓰기」의 전부다.
     *
     * 개정 번호도 함께 본다 — A 는 행이 없어 개정 0 이고 B 는 저장 뒤 1 이다. 두 계정이 **각자의**
     * 개정을 갖는다는 것이 `operator_strategy_revision` 의 계정 범위 키가 뜻하는 바다.
     */
    @Test
    fun `전략은 계정 경계 안이다 — B 가 저장한 감시어가 A 의 조회에 보이지 않는다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)

        confirmRequiredKeyword(b, "합성감시어B", seenRevision = 0)

        val seenByB = read(AccountScopedRoutes.STRATEGY, b)
        seenByB shouldContain "합성감시어B"
        seenByB shouldContain "\"revision\":1"

        val seenByA = read(AccountScopedRoutes.STRATEGY, a)
        seenByA shouldNotContain "합성감시어B"
        seenByA shouldContain "\"revision\":0"
    }

    /**
     * **격리 축 — 편집 세션.** 서버가 만든 세션 id 를 B 가 손에 넣어도 A 의 세션은 **없는 세션**이다.
     * 404 로 떨어지는 것이 요점이다 — 「남의 세션이 있다」를 알려 주지 않는다(7D 의 관례).
     *
     * 양방향으로 잰다: 자기 세션은 보이고 남의 세션은 안 보인다. 한쪽만 보면 「아무 세션도 안 보인다」
     * 는 구현도 초록이다.
     */
    @Test
    fun `편집 세션은 계정 경계 안이다 — 남의 세션 id 는 없는 세션이다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)

        val sessionOfA = beginEditSession(a)

        readStatus(editSessionPath(sessionOfA), a) shouldBe OK
        readStatus(editSessionPath(sessionOfA), b) shouldBe NOT_FOUND
    }

    /**
     * **격리 축 — 제안 목록.** 목록은 계산이고 그 입력이 전략이므로, 계정마다 다른 전략이면 개정
     * 헤더가 다르다. 공고를 심지 않아 둘 다 빈 목록이지만 **헤더의 개정**이 두 계정에서 갈린다 —
     * 그 값이 「누구의 전략으로 계산했는가」의 유일한 관측점이다.
     */
    @Test
    fun `제안 목록은 계정의 전략으로 계산한다 — 개정 헤더가 계정마다 다르다`() {
        val a = signIn(LOGIN_A)
        val b = signIn(LOGIN_B)

        confirmRequiredKeyword(b, "합성감시어B", seenRevision = 0)

        strategyRevisionHeaderOf(b) shouldBe "1"
        strategyRevisionHeaderOf(a) shouldBe "0"
    }

    private fun detailsBody(tradeName: String): String =
        """{"tradeName":"$tradeName","headquartersRegion":"SEJONG","naraRegistration":"UNANSWERED"}"""

    private fun capacityBody(license: String): String =
        """{"licenseName":"$license","amountWon":1000,"assessmentYear":2026}"""

    private fun qualificationBody(license: String): String =
        """{"businessTypes":[],"licensesDeclared":true,"licenseNames":["$license"],"regionTerms":[]}"""

    /** 전략을 바꾸는 유일한 코드 경로 — 편집 세션 begin → value → confirm(STR-07). */
    private fun confirmRequiredKeyword(
        session: OwnerSession,
        term: String,
        seenRevision: Int,
    ) {
        val id = beginEditSession(session)
        write(
            "${editSessionPath(id)}/value",
            """{"commandId":"v-$term","field":"REQUIRED_KEYWORD","terms":["$term"]}""",
            session,
        ) shouldBe OK
        write(
            "${editSessionPath(id)}/confirm",
            """{"commandId":"c-$term","seenRevision":$seenRevision}""",
            session,
        ) shouldBe OK
    }

    private fun beginEditSession(session: OwnerSession): String {
        val headers = cookieAndStamp(session).apply { contentType = MediaType.APPLICATION_JSON }
        val response = exchange(EDIT_SESSIONS, HttpMethod.POST, headers, """{"field":"REQUIRED_KEYWORD"}""")
        response.statusCode.value() shouldBe CREATED
        return requireNotNull(SESSION_ID.find(response.body.orEmpty())?.groupValues?.get(1)) { "sessionId 가 없다" }
    }

    private fun editSessionPath(sessionId: String): String = "$EDIT_SESSIONS/$sessionId"

    private fun readStatus(
        path: String,
        session: OwnerSession,
    ): Int = exchange(path, HttpMethod.GET, cookieOnly(session)).statusCode.value()

    private fun strategyRevisionHeaderOf(session: OwnerSession): String {
        val response = exchange(AccountScopedRoutes.SUGGESTIONS, HttpMethod.GET, cookieOnly(session))
        response.statusCode.value() shouldBe OK
        return requireNotNull(response.headers.getFirst(STRATEGY_REVISION_HEADER)) { "개정 헤더가 없다" }
    }

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
