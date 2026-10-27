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

        session.post(CAPACITIES_REMOVE, """{"licenseName":"없는면허"}""").statusCode.value() shouldBe NOT_FOUND
        session.post(CAPACITIES_REMOVE, """{"licenseName":"합성토목"}""").statusCode.value() shouldBe OK
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

    @Test
    fun `쓰기 넷은 CSRF 없이는 401 이다 — 메서드가 요구를 만든다`() {
        val withoutCsrf =
            listOf(
                QUALIFICATION_FACTS to qualificationBody(declared = false, names = emptyList()),
                BUSINESS_DETAILS to detailsBody(),
                CAPACITIES to capacityBody("합성면허", 1_000L),
                CAPACITIES_REMOVE to """{"licenseName":"합성면허"}""",
            )

        withoutCsrf.forEach { (path, body) ->
            session.postWithoutCsrf(path, body).statusCode.value() shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun `공백뿐인 상호는 400 이다 — 조용히 미설정으로 접지 않는다`() {
        session.post(BUSINESS_DETAILS, detailsBody(tradeName = "\"합성상호\"")).statusCode.value() shouldBe OK

        val blanked = session.post(BUSINESS_DETAILS, detailsBody(tradeName = "\"   \""))

        blanked.statusCode.value() shouldBe BAD_REQUEST
        // 거부가 저장된 값을 건드리지 않았다 — 절 교체가 일어나지 않았다는 뜻이다.
        session.get(AccountScopedRoutes.PROFILE).body.orEmpty() shouldContain "합성상호"
    }

    @Test
    fun `제어 문자는 400 이다 — 저장 층에 닿기 전에 막는다`() {
        // **JSON 이스케이프로** 보낸다 — 날 NUL 바이트는 Jackson 이 먼저 거부해 이 축을 가린다.
        // 이 값이 저장 층에 닿으면 PostgreSQL 이 SQLSTATE 22021 로 거부하고, 그 예외는 어댑터의
        // class 23 번역 밖이라 500 이 된다(요청 탓을 서버 탓으로 말하는 것이다).
        val details =
            """{"tradeName":"\u0000합성상호","headquartersRegion":null,"naraRegistration":"UNANSWERED"}"""
        val capacity = """{"licenseName":"\u0000합성면허","amountWon":1000,"assessmentYear":2026}"""

        session.post(BUSINESS_DETAILS, details).statusCode.value() shouldBe BAD_REQUEST
        session.post(CAPACITIES, capacity).statusCode.value() shouldBe BAD_REQUEST
    }

    @Test
    fun `길이 상한은 두 표면에서 선다 — 자유 어휘 원소와 삭제 요청의 면허명`() {
        val max = ProfileInputPolicy.QUALIFICATION_TERM_MAX_CODE_POINTS
        val atLimit = "가".repeat(max)
        val overLimit = "가".repeat(max + 1)

        val accepted = session.post(QUALIFICATION_FACTS, qualificationBody(declared = true, names = listOf(atLimit)))
        val rejected = session.post(QUALIFICATION_FACTS, qualificationBody(declared = true, names = listOf(overLimit)))

        accepted.statusCode.value() shouldBe OK
        rejected.statusCode.value() shouldBe BAD_REQUEST

        // 삭제 요청에는 값 타입이 없다 — 이 규칙이 그 표면의 **유일한** 길이 상한이다.
        session.post(CAPACITIES_REMOVE, """{"licenseName":"$atLimit"}""").statusCode.value() shouldBe NOT_FOUND
        session.post(CAPACITIES_REMOVE, """{"licenseName":"$overLimit"}""").statusCode.value() shouldBe BAD_REQUEST
    }

    @Test
    fun `인증서 만료일도 공백뿐이면 400 이다 — 상호와 같은 규율`() {
        val blankDate =
            """{"tradeName":null,"headquartersRegion":null,"naraRegistration":"REGISTERED",""" +
                """"naraRegisteredFields":[],"businessCertificateExpiresOn":"   "}"""

        session.post(BUSINESS_DETAILS, blankDate).statusCode.value() shouldBe BAD_REQUEST
    }

    @Test
    fun `자격 사실의 자유 어휘도 값 규칙을 받는다 — 공백 원소 · 중복 · 개수 상한`() {
        val blankTerm = """{"businessTypes":[" "],"licensesDeclared":false,"licenseNames":[],"regionTerms":[]}"""
        val duplicated =
            """{"businessTypes":["CAT-1","CAT-1"],"licensesDeclared":false,"licenseNames":[],"regionTerms":[]}"""
        val tooMany =
            """{"businessTypes":[],"licensesDeclared":false,"licenseNames":[],"regionTerms":[""" +
                (1..ProfileInputPolicy.QUALIFICATION_TERMS_MAX + 1).joinToString(",") { "\"지역어$it\"" } +
                """]}"""

        session.post(QUALIFICATION_FACTS, blankTerm).statusCode.value() shouldBe BAD_REQUEST
        session.post(QUALIFICATION_FACTS, duplicated).statusCode.value() shouldBe BAD_REQUEST
        session.post(QUALIFICATION_FACTS, tooMany).statusCode.value() shouldBe BAD_REQUEST
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

    private fun detailsBody(tradeName: String = "null"): String =
        """{"tradeName":$tradeName,"headquartersRegion":null,"naraRegistration":"UNANSWERED"}"""

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
        val QUALIFICATION_FACTS = AccountScopedRoutes.PROFILE + AccountScopedRoutes.PROFILE_QUALIFICATION_FACTS
        val BUSINESS_DETAILS = AccountScopedRoutes.PROFILE + AccountScopedRoutes.PROFILE_BUSINESS_DETAILS
        val CAPACITIES = AccountScopedRoutes.PROFILE + AccountScopedRoutes.PROFILE_CAPACITIES
        val CAPACITIES_REMOVE = AccountScopedRoutes.PROFILE + AccountScopedRoutes.PROFILE_CAPACITIES_REMOVE
        val KEY = Regex("\"([A-Za-z]+)\"\\s*:")
    }
}
