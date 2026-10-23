package bidvector.app.http

import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.strategy.StrategyViolation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

/**
 * 투찰가 요청 endpoint(`/api/bid-price-requests`)의 계약 대조 전담(M7/7C ②) — 평탄 스키마
 * 정책은 `OpenApiContractTest` 가 문서 전체 순회로 이미 재므로(이 slice 는 그 술어를 바꾸지
 * 않는다) 여기서는 **이 path 의 상태 코드별 키 집합 등식**과 요청 스키마 ↔ DTO 등식을 잰다.
 * 파일을 따로 두는 이유는 `OpenApiDryRunContractTest` 와 같다(sizeGate, 관심사 경계).
 *
 * 키 집합 등식이 핵심이다 — 응답에 필드를 더하거나 빼면서 문서를 고치지 않으면 RED 다. 특히
 * **값이 서지 않는 축의 숫자 칸을 몰래 더하는 것**이 이 등식에 걸린다.
 */
@Suppress("UNCHECKED_CAST")
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND,
        PROP_NO_STATIC_RESOURCE_MAPPINGS,
        PROP_NO_MANAGEMENT_SERVER,
    ],
)
class OpenApiBidPriceContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @Autowired
    private lateinit var notices: MutableNoticeByIdPort

    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = bidPriceStrategy()
        strategyRepository.loadFailure = null
        notices.notice = null
    }

    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun body(currentActiveBids: Any? = 0): Map<String, Any?> =
        mapOf("noticeNumber" to "BP-1", "noticeRound" to "000", "currentActiveBids" to currentActiveBids)

    private fun post(
        requestBody: Map<String, Any?> = body(),
        headers: HttpHeaders = authorizedHeaders(),
    ): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            url("/api/bid-price-requests"),
            HttpMethod.POST,
            HttpEntity(requestBody, headers),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    @Test
    fun `200 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        notices.notice = bidPriceNotice(floorRate = publishedFloorRate())

        val response = post()

        response.statusCode.value() shouldBe 200
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("BidPriceRequestResponse")
    }

    /** 평탄성 — 응답 값 어디에도 중첩 object 가 없다(임의 깊이를 재귀로 본다). */
    @Test
    fun `200 응답 값에 중첩 object 가 없다`() {
        notices.notice = bidPriceNotice(floorRate = publishedFloorRate())

        post().body?.values?.none(::containsNestedObject) shouldBe true
    }

    /**
     * **출하 직렬화가 축별 묶음을 실제로 편다**(`D-7C2-3` R7 의 전제) — 응답 Kotlin 타입은 멤버
     * 상한 때문에 축별로 쪼개져 있고 직렬화에서 평탄해진다. 펴기가 꺼지면 본문에 묶음 프로퍼티
     * 이름(`evaluation`·`priceAxes` 등)이 나타나고 **최상위 키 집합이 계약과 달라진다** — 위
     * 키 등식 단언과 이 단언이 그 변이에서 함께 붉어진다. 이 왕복이 출하 ObjectMapper 를 지난다.
     */
    @Test
    fun `출하 직렬화가 묶음을 펴서 묶음 이름이 본문에 없다`() {
        notices.notice = bidPriceNotice(floorRate = publishedFloorRate())

        val keys = post().body?.keys ?: emptySet()

        keys.intersect(BID_PRICE_GROUP_PROPERTY_NAMES) shouldBe emptySet()
        keys shouldBe bidPriceResponseFieldNames().toSet()
    }

    /**
     * nullable 칸도 **키 자체는 남는다** — 키를 지우면 화면이 「필드가 없다」와 「값이 없다」를
     * 구분할 수 없다(`OpenApiContractTest` 의 같은 규율).
     */
    @Test
    fun `값이 없는 nullable 칸도 키는 남아 null 로 응답한다`() {
        notices.notice = bidPriceNotice(baseAmountWon = null, floorRate = null)

        val body = post().body ?: error("본문이 비었다")

        body.containsKey("baseAmountWon") shouldBe true
        body["baseAmountWon"] shouldBe null
        body.containsKey("publishedFloorRateFraction") shouldBe true
        body["publishedFloorRateFraction"] shouldBe null
        body.containsKey("verdict") shouldBe true
    }

    @Test
    fun `400 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response = post(body(currentActiveBids = -1))

        response.statusCode.value() shouldBe 400
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
    }

    @Test
    fun `404 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response = post()

        response.statusCode.value() shouldBe 404
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.NOTICE_NOT_FOUND
    }

    @Test
    fun `401 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response = post(headers = HttpHeaders())

        response.statusCode.value() shouldBe 401
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
    }

    @Test
    fun `409 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        strategyRepository.strategy = bidPriceStrategy(maxActiveBids = null)
        notices.notice = bidPriceNotice()

        val response = post()

        response.statusCode.value() shouldBe 409
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.MAX_ACTIVE_BIDS_NOT_CONFIGURED
    }

    @Test
    fun `500 응답도 ErrorBody 계약과 일치한다`() {
        notices.notice = bidPriceNotice()
        strategyRepository.loadFailure = {
            InvalidStoredStrategyException(listOf(StrategyViolation.CandidateLimitNotPositive))
        }

        val response = post()

        response.statusCode.value() shouldBe 500
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.INVALID_STORED_STRATEGY
    }

    /** 요청 스키마 ↔ 파싱이 받는 칸이 같다. 문서에만 있는 칸·구현에만 있는 칸이 없다. */
    @Test
    fun `요청 스키마의 속성과 필수 집합이 구현과 같다`() {
        spec.propertyKeys("BidPriceRequestBody") shouldBe
            setOf("noticeNumber", "noticeRound", "currentActiveBids")
        spec.requiredKeys("BidPriceRequestBody") shouldBe
            setOf("noticeNumber", "noticeRound", "currentActiveBids")
    }

    /** 문서가 선언한 상태 코드 집합이 이 endpoint 가 실제로 내는 집합과 같다. */
    @Test
    fun `문서가 선언한 상태 코드 집합이 구현과 같다`() {
        spec.responseStatusCodes("/api/bid-price-requests", "post") shouldBe
            setOf("200", "400", "401", "404", "409", "415", "500")
    }

    /**
     * 길이 상한은 **문서가 정본**이다 — 구현 상수와 문서 `maxLength` 가 갈리면 같은 입력에
     * 두 답이 난다(7C ① 의 같은 규율).
     */
    @Test
    fun `식별자 길이 상한이 문서와 구현에서 같다`() {
        val schema = spec.schema("BidPriceRequestBody")

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as Map<String, Map<String, Any?>>
        properties.getValue("noticeNumber")["maxLength"] shouldBe MAX_IDENTIFIER_LENGTH
        properties.getValue("noticeRound")["maxLength"] shouldBe MAX_IDENTIFIER_LENGTH
    }
}
