package bidvector.app.http

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

private const val SESSION_PATH = "/api/session"

/**
 * 7D 우회 ①②③⑤⑦⑨ 의 **HTTP 층** 측정 — 쿠키·CSRF 헤더·허용 목록·응답 노출. 세션 판정 자체는
 * 실 use case 가 하고(이중체는 저장 경계뿐) 만료·잠금의 시간 축은
 * `BrowserAccessWorkflowTest` 가 잰다.
 */
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND,
        PROP_NO_STATIC_RESOURCE_MAPPINGS,
        PROP_NO_MANAGEMENT_SERVER,
    ],
)
class BrowserSessionAuthenticationTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    @Autowired
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    @BeforeEach
    fun resetState() {
        access.reset()
    }

    @Test
    fun `로그인은 쿠키를 HttpOnly·Secure·SameSite Strict·Path 로 내고 CSRF 값을 본문으로 준다`() {
        val response = signIn()

        response.statusCode.value() shouldBe 201
        val setCookie = response.headers.getFirst(HttpHeaders.SET_COOKIE) ?: ""
        setCookie shouldContain "${SessionCookieWriter.NAME}="
        setCookie shouldContain "HttpOnly"
        setCookie shouldContain "Secure"
        setCookie shouldContain "SameSite=Strict"
        setCookie shouldContain "Path=/"
        setCookie shouldContain "Max-Age=${TEST_ACCESS_POLICY.absoluteWindow.seconds}"
        (response.body?.get("csrf") as? String)?.isNotBlank() shouldBe true
        response.body?.keys shouldBe setOf("csrf", "expiresAt")
    }

    @Test
    fun `우회 ① — 쿠키 없이 상태를 바꾸는 요청은 401 이다`() {
        val response = exchange(SESSION_PATH, HttpMethod.DELETE, headers = HttpHeaders())

        response.statusCode.value() shouldBe 401
        response.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
    }

    @Test
    fun `우회 ② — 쿠키는 있고 CSRF 헤더가 없거나 틀리면 거부한다`() {
        val session = signInSession()

        missing(session).statusCode.value() shouldBe 401
        wrongStamp(session).statusCode.value() shouldBe 401
        signOut(session).statusCode.value() shouldBe 204
    }

    @Test
    fun `우회 ② — 읽기 메서드는 CSRF 를 요구하지 않는다(면제 집합이 그 셋이다)`() {
        val session = signInSession()

        exchange(SESSION_PATH, HttpMethod.GET, headers = cookieOnly(session)).statusCode.value() shouldBe 200
        BrowserSessionAuthenticator.CSRF_EXEMPT_METHODS shouldBe setOf("GET", "HEAD", "OPTIONS")
    }

    /**
     * CSRF 요구가 **세션 endpoint 하나에만** 걸린 것이 아니라는 사실 — 필터 한 등록이 전 경로를
     * 판정하므로 기존 쓰기 endpoint 도 같은 요구를 받는다. CSRF 를 실은 쪽은 **401 이 아니다**로만
     * 단언한다(그 뒤 상태 코드는 편집 도메인의 일이고 이 test 의 축이 아니다).
     */
    @Test
    fun `우회 ② — 쿠키만으로는 기존 쓰기 endpoint 도 부를 수 없다`() {
        val session = signInSession()
        val path = "/api/strategy/edit-sessions"
        val body = """{"field":"unknown-field-token"}"""

        val withoutStamp = exchange(path, HttpMethod.POST, headers = jsonWith(cookieOnly(session)), body = body)
        val withStamp = exchange(path, HttpMethod.POST, headers = jsonWith(cookieAndStamp(session)), body = body)

        withoutStamp.statusCode.value() shouldBe 401
        withStamp.statusCode.value() shouldNotBe 401
    }

    @Test
    fun `우회 ③ — 로그아웃한 쿠키는 다시 통하지 않는다`() {
        val session = signInSession()
        signOut(session).statusCode.value() shouldBe 204

        exchange(SESSION_PATH, HttpMethod.GET, headers = cookieOnly(session)).statusCode.value() shouldBe 401
    }

    @Test
    fun `우회 ⑤ — 로그인은 새 쿠키를 내고 앞 쿠키는 죽는다(세션 고정)`() {
        val first = signInSession()
        val second = signInSession()

        first.cookie shouldNotBe second.cookie
        exchange(SESSION_PATH, HttpMethod.GET, headers = cookieOnly(first)).statusCode.value() shouldBe 401
        exchange(SESSION_PATH, HttpMethod.GET, headers = cookieOnly(second)).statusCode.value() shouldBe 200
    }

    @Test
    fun `비밀 문구가 틀리면 401 이고 쿠키를 내지 않는다`() {
        val refused = signIn(passphrase = "wrong-passphrase-value")

        refused.statusCode.value() shouldBe 401
        refused.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
        refused.headers.getFirst(HttpHeaders.SET_COOKIE) shouldBe null
    }

    @Test
    fun `우회 ⑦ — 무인증 허용 목록은 로그인 하나다(집합 등식)`() {
        RequestAuthenticationFilter.OPEN_ROUTES shouldBe setOf("POST $SESSION_PATH")
    }

    @Test
    fun `우회 ⑦ — 등록된 모든 매핑이 무자격 요청을 거부한다(로그인만 예외, 기계 전수)`() {
        val routes =
            handlerMapping.handlerMethods.entries
                .flatMap { (info, _) ->
                    val methods = info.methodsCondition.methods.map { it.name }.ifEmpty { listOf("GET") }
                    val paths =
                        info.pathPatternsCondition
                            ?.patterns
                            .orEmpty()
                            .map { it.patternString.replace(PATH_VARIABLE_PATTERN, "auth-probe") }
                    paths.flatMap { path -> methods.map { method -> method to path } }
                }.toSet()

        routes.shouldNotBeEmpty()
        val statuses =
            routes.associateWith { (method, path) ->
                exchange(path, HttpMethod.valueOf(method), headers = HttpHeaders(), body = "{}").statusCode.value()
            }

        // 로그인 하나만 401 이 아니다 — 그 하나도 통과(201)가 아니라 **본문 검증**(400)에 걸린다
        // (빈 object 는 알려진 키를 갖지 않는다). 즉 허용 목록이 여는 것은 **그 경로 하나**이고
        // 그 경로의 인증 없는 성공이 아니다.
        statuses.filterValues { it != 401 }.keys shouldBe setOf("POST" to SESSION_PATH)
    }

    /**
     * 허용 목록은 `(메서드, requestURI)` **정확 일치**다. 그 비교가 열리는 방향은 하나뿐이다 —
     * 「컨테이너가 정규화한 뒤의 경로가 로그인과 같다」인데, 그때 그 요청은 **실제로 로그인
     * 경로로 라우팅되는 요청**이라 허용 목록이 제 일을 한 것이다. 위험한 방향(허용 목록을
     * 흉내 낸 표기로 **다른** endpoint 에 무인증으로 닿기)은 정확 일치라 성립하지 않는다 —
     * 정규화하면 비교 대상이 그 다른 경로가 되어 목록 밖이고, 정규화하지 않으면 문자열이
     * 다르다. 양쪽 다 401 이다.
     */
    @Test
    fun `우회 ⑦ — 허용 목록을 흉내 낸 표기로 다른 endpoint 에 닿지 못한다`() {
        val probes =
            listOf(
                "/api/session/../strategy",
                "/api/session/strategy",
                "/api/session/",
                "/API/session",
                "/api/session;a=b",
            )

        val statuses =
            probes.associateWith { path ->
                exchange(path, HttpMethod.POST, headers = jsonHeaders(), body = loginBody()).statusCode.value()
            }

        statuses.filterValues { it == 200 || it == 201 } shouldBe emptyMap()
    }

    @Test
    fun `우회 ⑨ — 응답 어디에도 비밀 문구·세션 쿠키 값이 실리지 않는다`() {
        val raw =
            restTemplate.exchange(
                url(SESSION_PATH),
                HttpMethod.POST,
                HttpEntity(loginBody(), jsonHeaders()),
                String::class.java,
            )
        val session = sessionFrom(raw.headers.getFirst(HttpHeaders.SET_COOKIE))

        (raw.body ?: "") shouldNotContainValue TEST_PASSPHRASE
        (raw.body ?: "") shouldNotContainValue session.cookie
        // CSRF 값은 세션 값에서 **유도**된 것이라 본문에 있는 것이 계약이다 — 그 값으로 쿠키를
        // 되돌릴 수 없다는 사실이 이 노출을 허용한다(단방향).
        (raw.body ?: "") shouldContain "csrf"
    }

    @Test
    fun `로그인 CSRF — form content type 은 415 로 떨어진다(교차 사이트 form 이 닿지 못한다)`() {
        val formHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val response =
            exchange(SESSION_PATH, HttpMethod.POST, headers = formHeaders, body = "loginName=x&passphrase=y")

        response.statusCode.value() shouldBe 415
        response.body?.get("code") shouldBe ErrorCode.UNSUPPORTED_MEDIA_TYPE
    }

    @Test
    fun `기존 운영자 자격 헤더 경로는 그대로 통한다 — 무회귀`() {
        val headers = HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

        exchange("/api/strategy", HttpMethod.GET, headers = headers).statusCode.value() shouldBe 200
        // 그 경로에는 브라우저 세션이 없다 — 「지금 내 세션」은 404 다(401 이 아니다: 인증은 지났다).
        exchange(SESSION_PATH, HttpMethod.GET, headers = headers).statusCode.value() shouldBe 404
    }

    @Test
    fun `세션 쿠키로도 기존 endpoint 를 읽을 수 있다 — 두 경로가 한 등록에서 판정된다`() {
        val session = signInSession()

        exchange("/api/strategy", HttpMethod.GET, headers = cookieOnly(session)).statusCode.value() shouldBe 200
    }

    @Test
    fun `저장되는 것은 쿠키 값의 해시뿐이다 — 우회 ⑧ 의 층 하나`() {
        val session = signInSession()

        access.sessions.storedKeys.shouldNotBeEmpty()
        access.sessions.storedKeys.contains(session.cookie) shouldBe false
    }

    // --- 호출 헬퍼 -------------------------------------------------------------

    private fun signIn(passphrase: String = TEST_PASSPHRASE): ResponseEntity<Map<String, Any?>> =
        exchange(SESSION_PATH, HttpMethod.POST, headers = jsonHeaders(), body = loginBody(passphrase))

    private fun signInSession(): SessionHandle {
        val response = signIn()
        response.statusCode.value() shouldBe 201
        return sessionFrom(response.headers.getFirst(HttpHeaders.SET_COOKIE), response.body?.get("csrf") as? String)
    }

    private fun signOut(session: SessionHandle): ResponseEntity<Map<String, Any?>> =
        exchange(SESSION_PATH, HttpMethod.DELETE, headers = cookieAndStamp(session))

    private fun missing(session: SessionHandle): ResponseEntity<Map<String, Any?>> =
        exchange(SESSION_PATH, HttpMethod.DELETE, headers = cookieOnly(session))

    private fun wrongStamp(session: SessionHandle): ResponseEntity<Map<String, Any?>> =
        exchange(
            SESSION_PATH,
            HttpMethod.DELETE,
            headers =
                cookieOnly(session).apply {
                    set(BrowserSessionAuthenticator.CSRF_HEADER, "not-this-session-stamp")
                },
        )

    private fun loginBody(passphrase: String = TEST_PASSPHRASE): String =
        """{"loginName":"$TEST_LOGIN","passphrase":"$passphrase"}"""

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun jsonWith(headers: HttpHeaders): HttpHeaders =
        HttpHeaders().apply {
            putAll(headers)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun cookieOnly(session: SessionHandle): HttpHeaders =
        HttpHeaders().apply { set(HttpHeaders.COOKIE, "${SessionCookieWriter.NAME}=${session.cookie}") }

    private fun cookieAndStamp(session: SessionHandle): HttpHeaders =
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

    private fun sessionFrom(
        setCookie: String?,
        stamp: String? = null,
    ): SessionHandle {
        val raw = requireNotNull(setCookie) { "Set-Cookie 가 없다" }
        val value = raw.substringAfter("${SessionCookieWriter.NAME}=").substringBefore(";")
        return SessionHandle(value, stamp ?: "")
    }

    private infix fun String.shouldNotContainValue(value: String) {
        contains(value) shouldBe false
    }

    /** test 가 들고 다니는 세션 — 쿠키 값과 CSRF 값. */
    private class SessionHandle(
        val cookie: String,
        val stamp: String,
    )
}
