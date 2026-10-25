package bidvector.app.http

import bidvector.workflow.access.AccountId
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * **계정은 typed 속성으로만 온다**(M7/7B 계약 D-7B-2) — 계정 범위 핸들러의 `AccountId` 는 audit
 * 주체 라벨(`account:<값>`)을 파싱해 얻지 않는다.
 *
 * 두 층에서 잰다. 앞 넷은 [RequestAuthenticationFilter] 와 접근자를 **요청 객체 하나로** 직접
 * 두드린다(라벨과 속성을 따로 조작할 수 있는 자리는 여기뿐이다). 뒤 둘은 실 세션 로그인으로
 * 입찰함에 닿아 **실제 인증 경로가 그 속성을 심는지**를 본다.
 *
 * production 에 문자열 파싱 경로가 0 이라는 것은 구조 test(`AccountCarrierStructureTest`)가 잰다.
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
class AuthenticatedAccountTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    @BeforeEach
    fun resetState() {
        access.reset()
    }

    // --- 필터 · 접근자 ------------------------------------------------------------

    @Test
    fun `계정을 낸 인증 경로는 그 계정을 싣는다`() {
        val request = passThrough(granting(AccountId(TEST_ACCOUNT_ID)))

        request.authenticatedAccount()?.value shouldBe TEST_ACCOUNT_ID
    }

    @Test
    fun `주체 라벨을 바꿔도 계정은 바뀌지 않는다`() {
        val request = passThrough(granting(AccountId(TEST_ACCOUNT_ID)))

        request.setAttribute(AUDIT_SUBJECT_ATTRIBUTE, "account:$FORGED_ACCOUNT_ID")

        request.authenticatedAccount()?.value shouldBe TEST_ACCOUNT_ID
    }

    @Test
    fun `주체 라벨만 있고 계정 속성이 없으면 계정은 없다`() {
        val request = MockHttpServletRequest("GET", AccountScopedRoutes.BID_CASES)
        request.setAttribute(AUDIT_SUBJECT_ATTRIBUTE, "account:$TEST_ACCOUNT_ID")

        request.authenticatedAccount().shouldBeNull()
    }

    /**
     * 계정 속성을 적는 자리는 이 판정 하나다 — 앞서 놓인 값은 판정 전에 지워진다. 계정을 내지
     * 않는 경로(자격 헤더)로 통과해도, 거부돼도 남지 않는다.
     */
    @Test
    fun `판정 전에 놓인 계정 속성은 지워진다 — 통과해도 거부돼도`() {
        val viaCredential = MockHttpServletRequest("GET", AccountScopedRoutes.BID_CASES)
        viaCredential.setAttribute(AUTHENTICATED_ACCOUNT_ATTRIBUTE, AccountId(FORGED_ACCOUNT_ID))
        filterOf(AuthenticationResult.Authenticated(OperatorCredentialAuthenticator.OPERATOR_SUBJECT))
            .doFilter(viaCredential, MockHttpServletResponse(), MockFilterChain())
        viaCredential.authenticatedAccount().shouldBeNull()

        val denied = MockHttpServletRequest("GET", AccountScopedRoutes.BID_CASES)
        denied.setAttribute(AUTHENTICATED_ACCOUNT_ATTRIBUTE, AccountId(FORGED_ACCOUNT_ID))
        val response = MockHttpServletResponse()
        filterOf(AuthenticationResult.Denied).doFilter(denied, response, MockFilterChain())
        response.status shouldBe UNAUTHORIZED
        denied.authenticatedAccount().shouldBeNull()
    }

    // --- 실 인증 경로 --------------------------------------------------------------

    @Test
    fun `세션으로 닿으면 입찰함이 그 세션의 계정으로 열린다`() {
        val session = SessionTestClient(restTemplate, ::url).signIn()

        val opened =
            session.post(
                AccountScopedRoutes.BID_CASES,
                """{"noticeNumber":"R7B-ACCOUNT-CARRIER","noticeRound":"000","bidKind":"REAL"}""",
            )
        opened.statusCode.value() shouldBe CREATED

        val listed = session.get("${AccountScopedRoutes.BID_CASES}?kind=REAL")
        listed.statusCode.value() shouldBe OK
        listed.body.orEmpty() shouldContain "R7B-ACCOUNT-CARRIER"
    }

    @Test
    fun `자격 헤더로는 입찰함이 401 이다 — 그 경로는 계정을 심지 않는다`() {
        val headers = HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

        val response =
            restTemplate.exchange(
                url("${AccountScopedRoutes.BID_CASES}?kind=REAL"),
                HttpMethod.GET,
                HttpEntity<String>(null, headers),
                String::class.java,
            )
        response.statusCode.value() shouldBe UNAUTHORIZED
    }

    private fun granting(account: AccountId): RequestAuthenticationFilter =
        filterOf(AuthenticationResult.Authenticated(BrowserSessionAuthenticator.subjectOf(account), account))

    private fun filterOf(result: AuthenticationResult): RequestAuthenticationFilter =
        RequestAuthenticationFilter(listOf(StubAuthenticator(result)))

    private fun passThrough(filter: RequestAuthenticationFilter): MockHttpServletRequest {
        val request = MockHttpServletRequest("GET", AccountScopedRoutes.BID_CASES)
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())
        return request
    }

    private class StubAuthenticator(
        private val result: AuthenticationResult,
    ) : RequestAuthenticator {
        override fun authenticate(request: jakarta.servlet.http.HttpServletRequest): AuthenticationResult = result
    }

    private companion object {
        const val FORGED_ACCOUNT_ID = 999L
        const val OK = 200
        const val CREATED = 201
        const val UNAUTHORIZED = 401
    }
}
