package bidvector.app.http

import bidvector.workflow.profile.ProfileInputPolicy
import bidvector.workflow.profile.StorageWriteOutcome
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

/**
 * 프로필 operation 여섯의 **HTTP 거동**(M7/7C④ 2b) — 세션으로 실제로 부른다.
 *
 * 여기서 잠그는 것:
 *  · 계정 없는 인증(자격 헤더 경로)은 **401** 이다 — 계정 범위 경로이기 때문이다.
 *  · 요청에 `accountId` 를 넣는 길이 없다(알려진 키 밖은 400) — 2b 표적 2 의 HTTP 쪽 절반.
 *  · 면허의 **세 상태**가 왕복에서 보존된다(미설정 · 미신고 · 신고).
 *  · 시평액 **행 수 상한**은 409 다(D-7C4-8 Q1).
 *  · 저장 층의 제약 거부는 400 이고 **응답에 행 값이 없다**(2b 표적 1).
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
class ProfileEndpointTest : HttpIntegrationTestBase() {
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
    fun `계정 없는 인증은 401 이다 — 자격 헤더만으로는 프로필에 닿지 못한다`() {
        val headers = HttpHeaders().apply { set("X-Operator-Credential", TEST_CREDENTIAL) }

        val response =
            restTemplate.exchange(
                url(AccountScopedRoutes.PROFILE),
                HttpMethod.GET,
                HttpEntity<String>(headers),
                String::class.java,
            )

        response.statusCode.value() shouldBe UNAUTHORIZED
        response.body.orEmpty() shouldContain "\"code\":\"${ErrorCode.UNAUTHENTICATED}\""
    }

    @Test
    fun `프로필 미설정 — 지어낸 값이 없고 판정 대기 축 셋이 실린다`() {
        val body = session.get(AccountScopedRoutes.PROFILE).body.orEmpty()

        body shouldContain "\"qualificationDeclared\":false"
        body shouldContain "\"licensesDeclared\":false"
        body shouldContain "\"tradeName\":null"
        body shouldContain "\"naraRegistration\":\"UNANSWERED\""
        body shouldContain "\"QUAL_08_REGION\""
        body shouldContain "\"QUAL_10_PERFORMANCE\""
        body shouldContain "\"QUAL_11_CONSTRUCTION_CAPACITY\""
    }

    @Test
    fun `면허 세 상태가 왕복에서 보존된다 — 미설정 · 미신고 · 신고`() {
        session.post(QUALIFICATION_FACTS, qualificationBody(declared = false, names = emptyList()))
        session.get(AccountScopedRoutes.PROFILE).body.orEmpty() shouldContain "\"licensesDeclared\":false"

        session.post(QUALIFICATION_FACTS, qualificationBody(declared = true, names = emptyList()))
        val declaredEmpty = session.get(AccountScopedRoutes.PROFILE).body.orEmpty()
        declaredEmpty shouldContain "\"qualificationDeclared\":true"
        declaredEmpty shouldContain "\"licensesDeclared\":true"
        declaredEmpty shouldContain "\"licenseNames\":[]"

        session.post(QUALIFICATION_FACTS, qualificationBody(declared = true, names = listOf("합성면허")))
        session.get(AccountScopedRoutes.PROFILE).body.orEmpty() shouldContain "\"licenseNames\":[\"합성면허\"]"
    }

    @Test
    fun `요청에 accountId 를 넣을 자리가 없다 — 알려진 키 밖은 400`() {
        val body = """{"tradeName":"합성상호","headquartersRegion":null,"naraRegistration":"UNANSWERED","accountId":9}"""

        val response = session.post(BUSINESS_DETAILS, body)

        response.statusCode.value() shouldBe BAD_REQUEST
        response.body.orEmpty() shouldContain "\"code\":\"${ErrorCode.INVALID_REQUEST}\""
    }

    @Test
    fun `미등록인데 등록분야를 실으면 400 이다 — 받아 두고 버리지 않는다`() {
        val body =
            """{"tradeName":null,"headquartersRegion":null,"naraRegistration":"NOT_REGISTERED",""" +
                """"naraRegisteredFields":["GOODS"]}"""

        session.post(BUSINESS_DETAILS, body).statusCode.value() shouldBe BAD_REQUEST
    }

    @Test
    fun `사업 정보 왕복 — 상호 원문과 등록 상태가 그대로 돌아온다`() {
        val body =
            """{"tradeName":" (주)합성상호 ","headquartersRegion":"SEJONG","naraRegistration":"REGISTERED",""" +
                """"naraRegisteredFields":["CONSTRUCTION","GOODS"],"businessCertificateExpiresOn":"2027-03-31"}"""

        val written = session.post(BUSINESS_DETAILS, body)

        written.statusCode.value() shouldBe OK
        val read = session.get(AccountScopedRoutes.PROFILE).body.orEmpty()
        read shouldContain "\"tradeName\":\" (주)합성상호 \""
        read shouldContain "\"headquartersRegion\":\"SEJONG\""
        read shouldContain "\"naraRegisteredFields\":[\"CONSTRUCTION\",\"GOODS\"]"
        read shouldContain "\"businessCertificateExpiresOn\":\"2027-03-31\""
    }

    @Test
    fun `시평액 목록은 최상위 배열이고 삭제는 없는 행에 404 다`() {
        session.post(CAPACITIES, capacityBody("합성토목", 1_000L))

        val list = session.get(CAPACITIES)
        list.statusCode.value() shouldBe OK
        list.body
            .orEmpty()
            .trimStart()
            .first() shouldBe '['

        session.post("$CAPACITIES/remove", """{"licenseName":"없는면허"}""").statusCode.value() shouldBe NOT_FOUND
        session.post("$CAPACITIES/remove", """{"licenseName":"합성토목"}""").statusCode.value() shouldBe OK
    }

    @Test
    fun `행 수 상한을 넘기면 409 다 — 상한 값은 본문에 싣지 않는다`() {
        repeat(ROWS_MAX) { index ->
            session.post(CAPACITIES, capacityBody("합성면허$index", 1_000L)).statusCode.value() shouldBe OK
        }

        val over = session.post(CAPACITIES, capacityBody("한줄더", 1_000L))

        over.statusCode.value() shouldBe CONFLICT
        over.body.orEmpty() shouldContain "\"code\":\"${ErrorCode.CAPACITY_ROWS_EXCEEDED}\""
        keysOf(over.body.orEmpty()) shouldBe listOf("code", "message", "correlationId")
    }

    /**
     * 2b 표적 1 — 저장 층이 제약으로 거부하면 400 이고, 응답에는 **코드 · 고정 문구 · correlationId**
     * 셋뿐이다. 거부된 행의 값(상호)도 SQLSTATE 도 본문에 없다.
     */
    @Test
    fun `저장 제약 거부는 400 이고 응답에 행 값이 없다`() {
        store.rejection = StorageWriteOutcome.RejectedByStorage("23514")
        val body = """{"tradeName":"합성상호","headquartersRegion":null,"naraRegistration":"UNANSWERED"}"""

        val response = session.post(BUSINESS_DETAILS, body)

        response.statusCode.value() shouldBe BAD_REQUEST
        val text = response.body.orEmpty()
        text shouldContain "\"code\":\"${ErrorCode.INVALID_REQUEST}\""
        text shouldNotContain "합성상호"
        text shouldNotContain "23514"
        keysOf(text) shouldBe listOf("code", "message", "correlationId")
    }

    private fun keysOf(body: String): List<String> = KEY.findAll(body).map { it.groupValues[1] }.toList()

    private fun qualificationBody(
        declared: Boolean,
        names: List<String>,
    ): String =
        """{"businessTypes":[],"licensesDeclared":$declared,""" +
            """"licenseNames":[${names.joinToString(",") { "\"$it\"" }}],"regionTerms":[]}"""

    private fun capacityBody(
        license: String,
        amount: Long,
    ): String = """{"licenseName":"$license","amountWon":$amount,"assessmentYear":2026}"""

    private companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        const val CONFLICT = 409
        val ROWS_MAX = ProfileInputPolicy.CAPACITY_ROWS_MAX
        val QUALIFICATION_FACTS = AccountScopedRoutes.PROFILE + QUALIFICATION_FACTS_PATH
        val BUSINESS_DETAILS = AccountScopedRoutes.PROFILE + BUSINESS_DETAILS_PATH
        val CAPACITIES = AccountScopedRoutes.PROFILE + CAPACITIES_PATH
        val KEY = Regex("\"([A-Za-z]+)\"\\s*:")
    }
}
