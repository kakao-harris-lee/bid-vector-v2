package bidvector.app.http

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * 투찰가 요청 endpoint 의 HTTP 층 — 상태 코드와 거부 형태. 401 전수는
 * `OperatorAuthenticationTest` 가 기계 전수로 이미 재고(새 매핑도 자동 포함) CSRF 는
 * `BrowserSessionAuthenticationTest` 가 메서드 집합으로 잰다. 여기서 재는 것은 **이 endpoint
 * 고유의 400·404 와 200 의 형태**다.
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
class BidPriceRequestEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @Autowired
    private lateinit var notices: MutableNoticeByIdPort

    @Autowired
    private lateinit var judgements: MutableLicenseJudgementPort

    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = bidPriceStrategy()
        strategyRepository.loadFailure = null
        notices.notice = null
        judgements.judgement = licenseJudgementFixture()
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun post(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            url("/api/bid-price-requests"),
            HttpMethod.POST,
            HttpEntity(body, authorizedHeaders()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    private fun postRaw(rawBody: String): ResponseEntity<Map<String, Any?>> {
        val headers = authorizedHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return restTemplate.exchange(
            url("/api/bid-price-requests"),
            HttpMethod.POST,
            HttpEntity(rawBody, headers),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    private fun validBody(
        number: String = "BP-1",
        round: String = "000",
        currentActiveBids: Any? = 0,
    ): Map<String, Any?> =
        mapOf("noticeNumber" to number, "noticeRound" to round, "currentActiveBids" to currentActiveBids)

    @Test
    fun `우회 ① — 자격증명이 없으면 401 이고 본문은 ErrorBody 다`() {
        val response =
            restTemplate.postForEntity(
                url("/api/bid-price-requests"),
                HttpEntity(validBody()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 401
        response.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
    }

    @Test
    fun `없는 공고는 404 이고 경로 부재와 다른 코드다`() {
        val response = post(validBody())

        response.statusCode.value() shouldBe 404
        response.body?.get("code") shouldBe ErrorCode.NOTICE_NOT_FOUND
    }

    @Test
    fun `공고가 있으면 200 이고 판정·근거·고지를 낸다`() {
        notices.notice = bidPriceNotice(floorRate = publishedFloorRate("0.87745"))

        val response = post(validBody())

        response.statusCode.value() shouldBe 200
        val body = response.body ?: error("본문이 비었다")
        body["noticeNumber"] shouldBe "BP-1"
        body["noticeRound"] shouldBe "000"
        body["maxActiveBids"] shouldBe 10
        body["currentActiveBids"] shouldBe 0
        body["licenseVerdict"] shouldBe "ELIGIBLE"
        body["licenseValidity"] shouldBe "NOT_VERIFIED"
        body["publishedFloorRateFraction"] shouldBe "0.87745"
        body["floorRateGroundsState"] shouldBe "USABLE"
        body["bidPriceMenuState"] shouldBe "NOT_COMPUTED"
        body["confidenceState"] shouldBe "NOT_AVAILABLE"
        body["floorShortfallState"] shouldBe "NOT_COMPUTED"
        body["inboxRecordState"] shouldBe "NOT_RECORDED_YET"
    }

    /**
     * **개연 불가 게시 하한율은 wire 에 숫자로 나가지 않는다**(DEC-10 게이트 · D-7C2B-2). 순수
     * 조립 단언(`BidPriceRequestResponseTest`)과 따로 두는 이유는 이 값이 **직렬화를 지나**
     * 화면 근거 칸에 닿기 때문이다 — 칸은 남고(계약 키 집합) 값만 비어 나간다.
     */
    @Test
    fun `개연 불가 게시 하한율은 상태만 나가고 근거 칸은 비어 나간다`() {
        notices.notice = bidPriceNotice(floorRate = publishedFloorRate("1.00000"))

        val response = post(validBody())

        response.statusCode.value() shouldBe 200
        val body = response.body ?: error("본문이 비었다")
        body["floorRateGroundsState"] shouldBe "IMPLAUSIBLE"
        body.containsKey("publishedFloorRateFraction") shouldBe true
        body["publishedFloorRateFraction"] shouldBe null
        body["publishedFloorRateOrigin"] shouldBe "NOTICE_VALUE"
    }

    /**
     * 공고번호는 **정규화해서 찾는다**(`NoticeNumber` 가 그 규칙의 단일 출처) — 소문자·여분
     * 공백 표기로 같은 공고에 닿는다. 정규화를 빼면 같은 공고가 404 가 된다.
     */
    @Test
    fun `공고번호 표기 차이는 정규화가 흡수한다`() {
        notices.notice = bidPriceNotice(bidPriceNoticeId("BP-1"))

        post(validBody(number = "  bp-1  ")).statusCode.value() shouldBe 200
    }

    @Test
    fun `여력 상한이 없는 전략은 409 다 — fail-closed`() {
        strategyRepository.strategy = bidPriceStrategy(maxActiveBids = null)
        notices.notice = bidPriceNotice()

        val response = post(validBody())

        response.statusCode.value() shouldBe 409
        response.body?.get("code") shouldBe ErrorCode.MAX_ACTIVE_BIDS_NOT_CONFIGURED
    }

    @Test
    fun `currentActiveBids 가 음수면 400 이고 공고 조회보다 앞선다`() {
        // 공고를 설정하지 않았다 — 입력 형태가 먼저 거부되므로 404 가 아니라 400 이어야 한다.
        val response = post(validBody(currentActiveBids = -1))

        response.statusCode.value() shouldBe 400
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    /**
     * D-6A3-19 와 같은 규율 — Jackson 의 조용한 강제 변환을 쓰지 않는다. 수를 공고번호로,
     * 문자열·소수를 여력 수로 받아들이면 값을 지어내는 것에 가깝다.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"noticeRound":"000","currentActiveBids":0}""",
            """{"noticeNumber":null,"noticeRound":"000","currentActiveBids":0}""",
            """{"noticeNumber":123,"noticeRound":"000","currentActiveBids":0}""",
            """{"noticeNumber":"   ","noticeRound":"000","currentActiveBids":0}""",
            """{"noticeNumber":"BP-1","noticeRound":"1","currentActiveBids":0}""",
            """{"noticeNumber":"BP-1","noticeRound":"0001","currentActiveBids":0}""",
            """{"noticeNumber":"BP-1","noticeRound":"00a","currentActiveBids":0}""",
            """{"noticeNumber":"BP-1","noticeRound":"000","currentActiveBids":"0"}""",
            """{"noticeNumber":"BP-1","noticeRound":"000","currentActiveBids":1.7}""",
            """{"noticeNumber":"BP-1","noticeRound":"000"}""",
            """["BP-1","000",0]""",
            """{}""",
            "",
            "not-json",
        ],
    )
    fun `형식에 어긋난 본문은 전부 400 INVALID_REQUEST 다`(rawBody: String) {
        val response = postRaw(rawBody)

        response.statusCode.value() shouldBe 400
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    @Test
    fun `제어 문자가 든 공고번호는 400 이다`() {
        val response = postRaw("""{"noticeNumber":"BP\u00001","noticeRound":"000","currentActiveBids":0}""")

        response.statusCode.value() shouldBe 400
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    @Test
    fun `길이 상한을 넘는 공고번호는 400 이다`() {
        val long = "B".repeat(MAX_IDENTIFIER_LENGTH + 1)

        post(validBody(number = long)).statusCode.value() shouldBe 400
    }

    @Test
    fun `GET 으로는 닿지 않는다 — 매핑은 POST 하나다`() {
        val response =
            restTemplate.exchange(
                url("/api/bid-price-requests"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 405
        response.body?.get("code") shouldBe ErrorCode.METHOD_NOT_ALLOWED
    }
}
