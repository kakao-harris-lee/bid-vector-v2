package bidvector.app.http

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

private const val FRESH_LOGIN = "fresh-operator"
private const val FRESH_PASSPHRASE = "a-long-enough-passphrase"

/**
 * 열린 가입 endpoint(M15/15B)의 **HTTP 층** 측정 — 무인증으로 닿는가, 응답이 로그인과 같은 모양인가,
 * 거부가 칸별로 갈리는가, 그리고 남용 상한이 실제로 서는가.
 *
 * **자기 컨텍스트에서 돈다**(`bidvector.test.lane` 한 줄). 상한의 계수는 빈 하나의 메모리에 살므로,
 * 다른 test class 와 컨텍스트를 나눠 쓰면 이 class 가 센 수가 그쪽으로 샌다. 창 비우기는 **시계를
 * 미는 것**으로 한다 — production 의 「창 밖은 잊는다」와 같은 경로이고, test 전용 초기화 메서드를
 * production 에 두지 않는다.
 */
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND,
        PROP_NO_STATIC_RESOURCE_MAPPINGS,
        PROP_NO_MANAGEMENT_SERVER,
        "bidvector.test.lane=sign-up",
    ],
)
class AccountSignUpEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    @Autowired
    private lateinit var clock: FixedClock

    @BeforeEach
    fun resetState() {
        access.reset()
        // 창을 지나 보내 상한의 계수를 비운다 — test 사이의 격리이자 「창 밖은 잊는다」의 측정이다.
        clock.advance(TEST_SIGN_UP_WINDOW.toMillis() + 1)
    }

    @Test
    fun `가입은 무인증으로 닿고 201 로 쿠키와 CSRF 값을 낸다 — 로그인과 같은 모양`() {
        val response = signUp()

        response.statusCode.value() shouldBe 201
        val setCookie = response.headers.getFirst(HttpHeaders.SET_COOKIE) ?: ""
        setCookie shouldContain "${SessionCookieWriter.HOST_PREFIXED_NAME}="
        setCookie shouldContain "HttpOnly"
        setCookie shouldContain "SameSite=Strict"
        response.body?.keys shouldBe setOf("csrf", "expiresAt")
        access.signUps.created shouldBe listOf(FRESH_LOGIN)
    }

    @Test
    fun `가입이 낸 세션으로 곧바로 보호 경로를 읽는다 — 따로 로그인하지 않는다`() {
        val response = signUp()
        val cookie = cookieOf(response)

        exchange("/api/strategy", HttpMethod.GET, headers = cookieOnly(cookie)).statusCode.value() shouldBe 200
    }

    @Test
    fun `같은 아이디는 409 다 — 다른 정보를 함께 내지 않는다`() {
        signUp().statusCode.value() shouldBe 201

        val duplicate = signUp(passphrase = "another-long-passphrase")

        duplicate.statusCode.value() shouldBe 409
        duplicate.body?.get("code") shouldBe ErrorCode.ACCOUNT_EXISTS
        duplicate.headers.getFirst(HttpHeaders.SET_COOKIE) shouldBe null
        // 본문이 말하는 것은 「그 이름이 쓰인다」뿐이다 — 계정 식별자·시각이 실리지 않는다.
        duplicate.body?.keys shouldBe setOf("code", "message", "correlationId")
        access.signUps.created shouldBe listOf(FRESH_LOGIN)
    }

    @Test
    fun `아이디 형식 오류와 비밀번호 형식 오류를 다른 코드로 가른다 — 화면이 칸을 안다`() {
        signUp(login = "AB").statusCode.value() shouldBe 400
        signUp(login = "AB").body?.get("code") shouldBe ErrorCode.LOGIN_NAME_INVALID
        signUp(passphrase = "short").body?.get("code") shouldBe ErrorCode.PASSPHRASE_INVALID
        access.signUps.created shouldBe emptyList()
    }

    @Test
    fun `상한을 넘으면 429 다 — 그리고 창이 지나면 다시 열린다`() {
        repeat(TEST_SIGN_UP_ATTEMPTS) { index ->
            signUp(login = "limit-probe-$index").statusCode.value() shouldBe 201
        }

        val refused = signUp(login = "limit-probe-over")
        refused.statusCode.value() shouldBe 429
        refused.body?.get("code") shouldBe ErrorCode.SIGN_UP_RATE_LIMITED

        clock.advance(TEST_SIGN_UP_WINDOW.toMillis() + 1)
        signUp(login = "limit-probe-after").statusCode.value() shouldBe 201
    }

    /**
     * verifier r1 MEDIUM-1 — **상한이 「시도」를 KDF 앞에서 센다**는 성질. 앞 판의 429 test 는 성공
     * 열 건으로 상한을 채워서, 상한을 use case **뒤**로 옮기고 성공만 세는 변이가 **살아남았다**
     * (실측). 여기서는 **계정이 하나도 서지 않는** 요청으로 칸을 채운다 — 그 변이에서는 칸이
     * 비어 있어 열한 번째가 201 이고, 이 test 가 붉는다.
     */
    @Test
    fun `무효한 시도도 칸을 먹는다 — 상한이 KDF 앞에서 시도를 센다`() {
        repeat(TEST_SIGN_UP_ATTEMPTS) { index ->
            // 아이디 형식 거부 — use case 는 해시도 삽입도 돌지 않는다.
            signUp(login = "X$index").statusCode.value() shouldBe 400
        }
        access.signUps.created shouldBe emptyList()

        val refused = signUp()

        refused.statusCode.value() shouldBe 429
        refused.body?.get("code") shouldBe ErrorCode.SIGN_UP_RATE_LIMITED
        // 상한이 뒤에 섰다면 이 요청이 201 이었을 것이다 — 계정도 서지 않는다.
        access.signUps.created shouldBe emptyList()
    }

    @Test
    fun `가입 CSRF — form content type 은 415 로 떨어진다(교차 사이트 form 이 닿지 못한다)`() {
        val formHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val response =
            exchange(ACCOUNTS_PATH, HttpMethod.POST, headers = formHeaders, body = "loginName=x&passphrase=y")

        response.statusCode.value() shouldBe 415
        response.body?.get("code") shouldBe ErrorCode.UNSUPPORTED_MEDIA_TYPE
    }

    @Test
    fun `응답 어디에도 비밀번호가 실리지 않는다`() {
        val raw =
            restTemplate.exchange(
                url(ACCOUNTS_PATH),
                HttpMethod.POST,
                HttpEntity(body(FRESH_LOGIN, FRESH_PASSPHRASE), jsonHeaders()),
                String::class.java,
            )

        (raw.body ?: "") shouldNotContain FRESH_PASSPHRASE
        (raw.body ?: "") shouldContain "csrf"
    }

    @Test
    fun `알 수 없는 키는 400 이다 — 본문이 넓어지지 않는다`() {
        val response =
            exchange(
                ACCOUNTS_PATH,
                HttpMethod.POST,
                headers = jsonHeaders(),
                body = """{"loginName":"$FRESH_LOGIN","passphrase":"$FRESH_PASSPHRASE","role":"admin"}""",
            )

        response.statusCode.value() shouldBe 400
        access.signUps.created shouldBe emptyList()
    }

    /**
     * **`SessionUnavailable` 은 죽은 분기가 아니다**(r3 열린 질문 ① — 두 라운드 미측정이었다).
     * 실물에서 그 갈래는 가입이 행을 세운 **뒤** 세션을 적기 전에 자격이 움직이는 순간이고
     * (운영자 `rotate` · 행 소멸), 조건부 UPDATE 가 0행을 고쳐 `Contended` 로 끝난다. 여기서는
     * 확정 이중체를 그 상태로 세워 **HTTP 층의 503 매핑과 「쿠키를 내지 않는다」**를 잰다.
     *
     * use case 층은 `AccountSignUpTest` 가, 화면 문면은 `SignUpScreen.test.tsx` 가 같은 코드로
     * 잰다 — 셋이 함께 서야 이 갈래가 「살아 있다」가 된다.
     */
    @Test
    fun `세션이 서지 않으면 503 이고 쿠키를 내지 않는다 — 계정은 남는다`() {
        access.completion.contended = true

        val response = signUp()

        response.statusCode.value() shouldBe 503
        response.body?.get("code") shouldBe ErrorCode.SIGN_UP_SESSION_UNAVAILABLE
        response.headers.getFirst(HttpHeaders.SET_COOKIE) shouldBe null
        // 계정은 섰다 — 그 사람은 로그인으로 들어올 수 있고, 그래서 화면이 로그인을 가리킨다.
        access.signUps.created shouldBe listOf(FRESH_LOGIN)
    }

    // --- 호출 헬퍼 -------------------------------------------------------------

    private fun signUp(
        login: String = FRESH_LOGIN,
        passphrase: String = FRESH_PASSPHRASE,
    ): ResponseEntity<Map<String, Any?>> =
        exchange(ACCOUNTS_PATH, HttpMethod.POST, headers = jsonHeaders(), body = body(login, passphrase))

    private fun body(
        login: String,
        passphrase: String,
    ): String = """{"loginName":"$login","passphrase":"$passphrase"}"""

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun cookieOf(response: ResponseEntity<Map<String, Any?>>): String {
        val raw = requireNotNull(response.headers.getFirst(HttpHeaders.SET_COOKIE)) { "Set-Cookie 가 없다" }
        return raw.substringAfter("${SessionCookieWriter.HOST_PREFIXED_NAME}=").substringBefore(";")
    }

    private fun cookieOnly(cookie: String): HttpHeaders =
        HttpHeaders().apply { set(HttpHeaders.COOKIE, "${SessionCookieWriter.HOST_PREFIXED_NAME}=$cookie") }

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
}
