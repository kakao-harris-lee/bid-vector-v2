package bidvector.app.http

import bidvector.procurement.Agency
import bidvector.procurement.AgencyCode
import bidvector.procurement.AgencyName
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeTitle
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.notice.NoticeSearchLimits
import bidvector.workflow.notice.NoticeSearchPage
import bidvector.workflow.notice.NoticeSearchState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import java.net.URI
import java.time.Instant

/**
 * `GET /api/notices` 의 HTTP 층 거동 — 조건 조립·헤더·거부 상태 코드. 실 질의 거동은
 * `bidvector.adapters.notice.JdbcNoticeSearchTest`(실 PostgreSQL)가 잰다.
 *
 * 설계 검토 (2) 의 우회 ①(자격증명 없이 호출)은 `OperatorAuthenticationTest` 의 **기계
 * 전수**가 이미 이 경로를 포함하지만(등록된 모든 매핑을 거둔다), 이 endpoint 가 실제로 그
 * 전수에 들어왔는지는 여기서 한 번 더 직접 잰다 — 전수가 공허하게 참이 되는 회귀를 두 자리
 * 에서 막는다.
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
class NoticeSearchEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var notices: FakeNoticeSearchPort

    private val deadline = Instant.parse("2026-11-01T05:00:00Z")

    @BeforeEach
    fun resetFixture() {
        notices.page = NoticeSearchPage(emptyList(), 0L, NoticeSearchState.NOTHING_COLLECTED)
        notices.lastCriteria = null
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    /**
     * **`URI` 로 넘긴다** — `String` URL 은 `RestTemplate` 이 URI 템플릿으로 보고 다시
     * 인코딩해(`%` → `%25`) 서버가 받는 값이 달라진다(실측: `100%` 가 `100%25` 로 도착).
     * 이 test 가 재려는 것은 **서버가 받은 값**이라 인코딩을 여기서 고정한다.
     */
    private fun get(query: String): ResponseEntity<List<Map<String, Any?>>> =
        restTemplate.exchange(
            URI.create(url("/api/notices$query")),
            HttpMethod.GET,
            HttpEntity<Void>(authorizedHeaders()),
            List::class.java,
        ) as ResponseEntity<List<Map<String, Any?>>>

    private fun errorOf(query: String): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            URI.create(url("/api/notices$query")),
            HttpMethod.GET,
            HttpEntity<Void>(authorizedHeaders()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    private fun sampleNotice(): Notice {
        val id = NoticeId(NoticeNumber.of("SAMPLE-1"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-10-16T00:00:00Z"),
            )
        return Notice.collected(
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        1_500L,
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.Published(id.round),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = deadline,
                openingScheduledAt = null,
                raw = observation,
                title = NoticeTitle.of("도로 포장 공사"),
                demandAgency = Agency(AgencyCode.of("A-1"), AgencyName.of("서울특별시")),
                noticeAgency = Agency(AgencyCode.of("B-1"), AgencyName.of("조달청")),
            ),
        )
    }

    @Test
    fun `우회 ① — 자격증명이 없으면 401 이고 본문은 ErrorBody 다`() {
        val response =
            restTemplate.getForEntity(url("/api/notices"), Map::class.java)
                as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 401
        response.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
    }

    @Test
    fun `공고 요약과 두 헤더를 낸다`() {
        notices.page = NoticeSearchPage(listOf(sampleNotice()), 1L, NoticeSearchState.MATCHED)

        val response = get("")

        response.statusCode.value() shouldBe 200
        response.headers.getFirst(TOTAL_COUNT_HEADER) shouldBe "1"
        response.headers.getFirst(RESULT_STATE_HEADER) shouldBe "MATCHED"
        val row = response.body?.single() ?: error("본문이 비었다")
        row["noticeNumber"] shouldBe "SAMPLE-1"
        row["noticeRound"] shouldBe "000"
        row["status"] shouldBe "Open"
        row["title"] shouldBe "도로 포장 공사"
        row["demandAgencyName"] shouldBe "서울특별시"
        row["noticeAgencyName"] shouldBe "조달청"
        row["baseAmountWon"] shouldBe 1500
        row["baseAmountBasis"] shouldBe "BASE_AMOUNT"
        row["baseAmountCurrency"] shouldBe "KRW"
        row["baseAmountVatTreatment"] shouldBe "INCLUSIVE"
        row["baseAmountProvenance"] shouldBe "PUBLISHED"
        row["deadlineAt"] shouldBe deadline.toString()
    }

    @Test
    fun `값이 없는 축은 키를 지우지 않고 null 로 답한다`() {
        val id = NoticeId(NoticeNumber.of("BARE-1"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-10-16T00:00:00Z"),
            )
        val bare =
            Notice.collected(
                NoticeCollected(
                    id = id,
                    businessCategory = null,
                    baseAmount = null,
                    estimatedAmount = null,
                    allocatedBudget = null,
                    floorRate = null,
                    deadlineAt = null,
                    openingScheduledAt = null,
                    raw = observation,
                ),
            )
        notices.page = NoticeSearchPage(listOf(bare), 1L, NoticeSearchState.MATCHED)

        val row = get("").body?.single() ?: error("본문이 비었다")

        row.containsKey("baseAmountWon") shouldBe true
        row["baseAmountWon"] shouldBe null
        row["title"] shouldBe null
        row["deadlineAt"] shouldBe null
    }

    @Test
    fun `빈 결과의 두 뜻이 헤더로 갈린다`() {
        notices.page = NoticeSearchPage(emptyList(), 0L, NoticeSearchState.NOTHING_COLLECTED)
        val nothingCollected = get("")

        nothingCollected.headers.getFirst(RESULT_STATE_HEADER) shouldBe "NOTHING_COLLECTED"
        nothingCollected.headers.getFirst(TOTAL_COUNT_HEADER) shouldBe "0"

        notices.page = NoticeSearchPage(emptyList(), 0L, NoticeSearchState.NO_MATCH)
        get("?q=없는낱말").headers.getFirst(RESULT_STATE_HEADER) shouldBe "NO_MATCH"
    }

    @Test
    fun `질의가 조건으로 옮겨진다 — 리터럴 텍스트·기초금액·마감·페이지`() {
        notices.page = NoticeSearchPage(emptyList(), 0L, NoticeSearchState.NO_MATCH)

        get("?q=100%25&agency=%EC%A1%B0%EB%8B%AC%EC%B2%AD&amountMin=1000&amountMax=2000&page=2&size=5")

        val criteria = notices.lastCriteria ?: error("조건이 전달되지 않았다")
        criteria.text?.value shouldBe "100%"
        criteria.agencyText?.value shouldBe "조달청"
        criteria.page shouldBe 2
        criteria.size shouldBe 5
        criteria.offset shouldBe 10L
    }

    @Test
    fun `우회 ③④⑥ — 상한 초과 size · 역전 범위 · 긴 텍스트는 400 이고 사유를 싣지 않는다`() {
        val overCap = NoticeSearchLimits.MAX_PAGE_SIZE + 1
        val longText = "가".repeat(NoticeSearchLimits.MAX_TEXT_LENGTH + 1)

        val sizeRejected = errorOf("?size=$overCap")
        sizeRejected.statusCode.value() shouldBe 400
        sizeRejected.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
        sizeRejected.body?.keys shouldBe setOf("code", "message", "correlationId")

        errorOf("?size=100000").statusCode.value() shouldBe 400
        errorOf("?amountMin=2000&amountMax=1000").statusCode.value() shouldBe 400
        errorOf("?deadlineFrom=2026-11-02T00:00:00Z&deadlineTo=2026-11-01T00:00:00Z").statusCode.value() shouldBe 400
        errorOf("?q=$longText").statusCode.value() shouldBe 400
        errorOf("?page=-1").statusCode.value() shouldBe 400
    }

    @Test
    fun `형식이 틀린 값은 500 이 아니라 400 이다 — 조용한 강제 변환도 없다`() {
        errorOf("?amountMin=abc").statusCode.value() shouldBe 400
        errorOf("?amountMin=1.5").statusCode.value() shouldBe 400
        errorOf("?page=abc").statusCode.value() shouldBe 400
        errorOf("?size=abc").statusCode.value() shouldBe 400
        errorOf("?deadlineFrom=2026-11-01").statusCode.value() shouldBe 400
    }

    /**
     * verifier r1 F-2 — `U+0000` 은 PostgreSQL `text` 가 담을 수 없어 바인딩에서 예외가 됐고
     * 계약이 400 을 선언한 입력이 500 으로 나갔다. 여기서는 **포트에 닿기 전에** 400 이 되는
     * 것을 잰다(실 DB 위의 회귀는 `NoticeSearchProductionE2ETest`).
     */
    @Test
    fun `제어 문자가 든 질의는 400 이고 포트에 닿지 않는다`() {
        val response = errorOf("?q=%00")

        response.statusCode.value() shouldBe 400
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
        errorOf("?agency=%01").statusCode.value() shouldBe 400
        notices.lastCriteria shouldBe null
    }

    /**
     * verifier r1 L-3 — Spring 은 `q=a&q=b` 를 값 하나 `"a,b"` 로 합쳐 넘긴다. 쉼표가 든
     * 리터럴 검색과 구별되지 않는 조용한 결합이라 계약에서 끊는다.
     */
    @Test
    fun `같은 매개변수를 두 번 보내면 400 이고 포트에 닿지 않는다`() {
        val response = errorOf("?q=a&q=b")

        response.statusCode.value() shouldBe 400
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
        errorOf("?page=0&page=1").statusCode.value() shouldBe 400
        notices.lastCriteria shouldBe null

        // 서로 다른 매개변수 둘은 반복이 아니다 — 술어가 과잉 차단하지 않는다.
        notices.page = NoticeSearchPage(emptyList(), 0L, NoticeSearchState.NO_MATCH)
        get("?q=a&agency=b").statusCode.value() shouldBe 200
        // 알 수 없는 매개변수의 반복은 그대로 무시한다(거동 무변경).
        get("?unknown=1&unknown=2").statusCode.value() shouldBe 200
    }

    @Test
    fun `숫자 칸의 공백과 더하기 부호는 거부다`() {
        errorOf("?amountMin=%205").statusCode.value() shouldBe 400
        errorOf("?amountMin=%2B5").statusCode.value() shouldBe 400
        errorOf("?page=%2B1").statusCode.value() shouldBe 400
        errorOf("?size=%2010").statusCode.value() shouldBe 400
    }

    @Test
    fun `거부된 질의는 포트에 닿지 않는다 — 읽기조차 일어나지 않는다`() {
        errorOf("?size=100000").statusCode.value() shouldBe 400

        notices.lastCriteria shouldBe null
    }
}
