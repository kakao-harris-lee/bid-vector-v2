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

/** 사업 정보 쓰기 경로 — 체크리스트의 입력을 **API 로** 심는다(저장 이중체를 직접 만지지 않는다). */
private const val BUSINESS_DETAILS = "${AccountScopedRoutes.PROFILE}${AccountScopedRoutes.PROFILE_BUSINESS_DETAILS}"

/**
 * 시작하기 체크리스트 operation 의 **HTTP 거동**(M7/7C④-3) — 세션으로 실제로 부른다.
 *
 * 여기서 잠그는 것:
 *  · 계정 없는 인증(자격 헤더 경로)은 **401** 이다 — 계정 범위 뿌리이기 때문이다(D-7C4C-7).
 *  · **세 상태가 wire 에서 갈라진다**(D-7C4-8 Q3) — 완료 · 미완 · `awaiting` 짝.
 *  · 「해당 없음」 항목은 **어느 목록에도 없다** — 세 목록의 여집합이 그 상태다.
 *  · 항목 순서는 **커널 선언 순서**이고 응답 조립이 다시 정렬하지 않는다.
 *
 * 입력은 프로필 쓰기 operation 으로 심는다 — 저장 이중체를 직접 만지면 「계정이 그 값을 실제로
 * 갖는가」를 건너뛴 채 판정만 보게 된다.
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
class OnboardingChecklistEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    @Autowired
    private lateinit var store: InMemoryBusinessProfileStore

    @Autowired
    private lateinit var operatorProfile: InMemoryOperatorProfile

    private lateinit var session: SessionTestClient

    @BeforeEach
    fun signIn() {
        access.reset()
        store.reset()
        operatorProfile.reset()
        session = SessionTestClient(restTemplate, ::url).signIn()
    }

    @Test
    fun `계정 없는 인증은 401 이다 — 자격 헤더만으로는 체크리스트에 닿지 못한다`() {
        val headers = HttpHeaders().apply { set("X-Operator-Credential", TEST_CREDENTIAL) }

        val response =
            restTemplate.exchange(
                url(AccountScopedRoutes.ONBOARDING_CHECKLIST),
                HttpMethod.GET,
                HttpEntity<String>(headers),
                String::class.java,
            )

        response.statusCode.value() shouldBe UNAUTHORIZED
        response.body.orEmpty() shouldContain "\"code\":\"${ErrorCode.UNAUTHENTICATED}\""
    }

    /**
     * 아무것도 심지 않은 계정 — 미완이 여섯이고, 시평액은 **선행 대기**다(등록분야를 아직 모른다).
     * 감시 관심 업종은 test 전략 fixture 가 이미 갖고 있어 완료 쪽에 선다.
     */
    @Test
    fun `세 목록이 wire 에서 갈라진다 — 완료 · 미완 · 선행 대기 짝`() {
        val body = session.get(AccountScopedRoutes.ONBOARDING_CHECKLIST).body.orEmpty()

        body shouldContain "\"completed\":[\"WATCH_CONDITIONS\"]"
        body shouldContain
            "\"pending\":[\"TRADE_NAME\",\"NARA_READINESS\",\"BUSINESS_TYPES\",\"LICENSES\"," +
            "\"HEADQUARTERS_REGION\",\"BUDGET_RANGE\"]"
        body shouldContain
            "\"awaiting\":[{\"item\":\"CONSTRUCTION_CAPACITY\",\"prerequisite\":\"NARA_READINESS\"}]"
    }

    /**
     * 물품만 등록한 계정의 시평액 항목은 **해당 없음**이다 — 세 목록 어디에도 이름이 없다. 「해당
     * 없음」을 미완으로 접으면 공사로 입찰하지 않는 운영자에게 영원히 끝나지 않는 항목이 보인다.
     */
    @Test
    fun `해당 없음 항목은 어느 목록에도 없다`() {
        val details =
            """{"tradeName":null,"headquartersRegion":null,"naraRegistration":"REGISTERED",""" +
                """"naraRegisteredFields":["GOODS"],"businessCertificateExpiresOn":"2027-03-31"}"""
        session.post(BUSINESS_DETAILS, details).statusCode.value() shouldBe OK

        val body = session.get(AccountScopedRoutes.ONBOARDING_CHECKLIST).body.orEmpty()

        body shouldNotContain "CONSTRUCTION_CAPACITY"
        // 같은 쓰기가 나라장터 준비를 완료로 옮긴다 — 「아무것도 안 바뀌어 조용한」 초록이 아니고,
        // 완료 목록의 순서가 선언 순서(NARA_READINESS → WATCH_CONDITIONS)라는 것도 여기서 보인다.
        body shouldContain "\"completed\":[\"NARA_READINESS\",\"WATCH_CONDITIONS\"]"
        body shouldContain "\"awaiting\":[]"
    }

    /**
     * 공사로 등록하면 같은 항목이 **선행 대기에서 미완으로** 옮겨 간다 — 세 상태가 실제로 세 갈래로
     * 움직이는지를 한 test 에서 본다(두 상태로 접힌 응답이면 이 이동이 보이지 않는다).
     */
    @Test
    fun `공사 등록은 시평액을 선행 대기에서 미완으로 옮긴다`() {
        val details =
            """{"tradeName":null,"headquartersRegion":null,"naraRegistration":"REGISTERED",""" +
                """"naraRegisteredFields":["CONSTRUCTION"],"businessCertificateExpiresOn":"2027-03-31"}"""
        session.post(BUSINESS_DETAILS, details).statusCode.value() shouldBe OK

        val body = session.get(AccountScopedRoutes.ONBOARDING_CHECKLIST).body.orEmpty()

        body shouldContain
            "\"pending\":[\"TRADE_NAME\",\"BUSINESS_TYPES\",\"LICENSES\",\"HEADQUARTERS_REGION\"," +
            "\"CONSTRUCTION_CAPACITY\",\"BUDGET_RANGE\"]"
        body shouldContain "\"awaiting\":[]"
    }

    private companion object {
        const val OK = 200
        const val UNAUTHORIZED = 401
    }
}
