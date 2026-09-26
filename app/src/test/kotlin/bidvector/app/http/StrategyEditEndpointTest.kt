package bidvector.app.http

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

private const val EDIT_SESSIONS = "/api/strategy/edit-sessions"

/**
 * 편집 endpoint 여섯의 **결과 → 상태 코드** 표(D-6A2b-6)와 설계 검토 우회 ②③⑦⑧ 을 HTTP
 * 층에서 잰다. use case 는 실물이고(상태 기계를 test 사본으로 다시 짓지 않는다) 바뀌는
 * 것은 커넥션 경계뿐이다 — 원자성·영속은 실 DB test 가 따로 잰다.
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
class StrategyEditEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @Autowired
    private lateinit var transaction: InMemoryStrategyEditTransaction

    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = freshStrategy()
        strategyRepository.loadFailure = null
        transaction.reset()
    }

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun post(
        path: String,
        json: String,
    ): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            url(path),
            HttpMethod.POST,
            HttpEntity(json, headers()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    private fun get(path: String): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            url(path),
            HttpMethod.GET,
            HttpEntity<Void>(headers()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    private fun beginSession(field: String = "CANDIDATE_LIMIT"): String {
        val response = post(EDIT_SESSIONS, """{"field":"$field"}""")
        response.statusCode.value() shouldBe 201
        return response.body?.get("sessionId") as String
    }

    @Test
    fun `begin 은 201 과 서버가 만든 세션 id 를 낸다 — 요청은 id 를 고르지 못한다(우회 4)`() {
        val first = post(EDIT_SESSIONS, """{"field":"CANDIDATE_LIMIT","sessionId":"chosen-by-client"}""")
        val second = post(EDIT_SESSIONS, """{"field":"CANDIDATE_LIMIT"}""")

        first.statusCode.value() shouldBe 201
        first.body?.get("sessionId") shouldNotBe "chosen-by-client"
        first.body?.get("state") shouldBe "WAITING_FOR_VALUE"
        first.body?.get("field") shouldBe "CANDIDATE_LIMIT"
        // 요청 본문이 id 를 나르지 못하므로 두 요청이 같은 세션을 겨냥할 수 없다.
        second.body?.get("sessionId") shouldNotBe first.body?.get("sessionId")
    }

    @Test
    fun `value → confirm 이 전략 revision 을 올리고 세션이 APPLIED 로 끝난다`() {
        val sessionId = beginSession()

        val provided = post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":7}""")
        provided.statusCode.value() shouldBe 200
        provided.body?.get("state") shouldBe "WAITING_FOR_CONFIRMATION"

        val confirmed = post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c2","seenRevision":1}""")
        confirmed.statusCode.value() shouldBe 200
        confirmed.body?.get("state") shouldBe "APPLIED"
        confirmed.body?.get("strategyRevision") shouldBe 2
        transaction.events.published.size shouldBe 1
    }

    @Test
    fun `같은 commandId 에 다른 본문을 재전달하면 409 IDEMPOTENCY_CONFLICT 다 (우회 2)`() {
        val sessionId = beginSession()
        post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":7}""")

        val replay = post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":9}""")

        replay.statusCode.value() shouldBe 409
        replay.body?.get("code") shouldBe ErrorCode.IDEMPOTENCY_CONFLICT
    }

    @Test
    fun `오래된 revision 으로 confirm 하면 409 STALE_REVISION 이다 (우회 3)`() {
        val sessionId = beginSession()
        post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":7}""")

        val stale = post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c2","seenRevision":0}""")

        stale.statusCode.value() shouldBe 409
        stale.body?.get("code") shouldBe ErrorCode.STALE_REVISION
        transaction.events.published.size shouldBe 0
    }

    @Test
    fun `값이 불변식을 어기면 400 이고 세션은 전진하지 않는다 (우회 7)`() {
        val sessionId = beginSession()

        val negative =
            post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":-3}""")

        negative.statusCode.value() shouldBe 400
        negative.body?.get("code") shouldBe ErrorCode.STRATEGY_VALUE_INVALID
        get("$EDIT_SESSIONS/$sessionId").body?.get("state") shouldBe "WAITING_FOR_VALUE"
        get("$EDIT_SESSIONS/$sessionId").body?.get("sessionVersion") shouldBe 0
    }

    @Test
    fun `임계 역전도 같은 자리에서 400 이다 — 판정은 validate 하나가 한다`() {
        val bidNow = beginSession("BID_NOW_THRESHOLD")
        post("$EDIT_SESSIONS/$bidNow/value", """{"commandId":"b1","field":"BID_NOW_THRESHOLD","number":0.4}""")
        post("$EDIT_SESSIONS/$bidNow/confirm", """{"commandId":"b2","seenRevision":1}""")

        val review = beginSession("REVIEW_THRESHOLD")
        val inverted = post("$EDIT_SESSIONS/$review/value", """{"commandId":"r1","field":"REVIEW_THRESHOLD","number":0.9}""")

        inverted.statusCode.value() shouldBe 400
        inverted.body?.get("code") shouldBe ErrorCode.STRATEGY_VALUE_INVALID
    }

    @Test
    fun `없는 세션에 보낸 command 는 404 SESSION_NOT_FOUND 다`() {
        val response = post("$EDIT_SESSIONS/no-such-session/cancel", """{"commandId":"c1"}""")

        response.statusCode.value() shouldBe 404
        response.body?.get("code") shouldBe ErrorCode.SESSION_NOT_FOUND
        get("$EDIT_SESSIONS/no-such-session").statusCode.value() shouldBe 404
    }

    @Test
    fun `확인 화면에서 다른 필드로 되돌아가면 200 이고 대기 필드가 바뀐다`() {
        val sessionId = beginSession()
        post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":7}""")

        val edited = post("$EDIT_SESSIONS/$sessionId/edit", """{"commandId":"c2","field":"MAX_ACTIVE_BIDS"}""")

        edited.statusCode.value() shouldBe 200
        edited.body?.get("state") shouldBe "WAITING_FOR_VALUE"
        edited.body?.get("field") shouldBe "MAX_ACTIVE_BIDS"
    }

    @Test
    fun `cancel 은 세션을 종단으로 옮기고 그 뒤 command 는 409 INVALID_TRANSITION 이다`() {
        val sessionId = beginSession()

        val cancelled = post("$EDIT_SESSIONS/$sessionId/cancel", """{"commandId":"c1"}""")
        cancelled.statusCode.value() shouldBe 200
        cancelled.body?.get("state") shouldBe "CANCELLED"

        val afterCancel =
            post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c2","field":"CANDIDATE_LIMIT","count":7}""")
        afterCancel.statusCode.value() shouldBe 409
        afterCancel.body?.get("code") shouldBe ErrorCode.INVALID_TRANSITION
    }

    @Test
    fun `행위자는 상수다 — 요청이 actor 를 실어도 System 이 되지 않는다 (우회 8)`() {
        val sessionId = beginSession()
        post(
            "$EDIT_SESSIONS/$sessionId/value",
            """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":7,"actor":"System"}""",
        )
        post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c2","seenRevision":1}""")

        transaction.events.actors.map { it::class.simpleName } shouldBe listOf("Operator")
    }

    @Test
    fun `필드가 이름하지 않은 값 칸이 오면 400 이다 — 조용히 무시하지 않는다`() {
        val sessionId = beginSession()

        val wrongSlot =
            post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","terms":["x"]}""")
        val extraSlot =
            post(
                "$EDIT_SESSIONS/$sessionId/value",
                """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":7,"terms":["x"]}""",
            )

        wrongSlot.statusCode.value() shouldBe 400
        wrongSlot.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
        extraSlot.statusCode.value() shouldBe 400
        extraSlot.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    @Test
    fun `강제 변환 없이 거부한다 — 문자열 정수·소수 count·알 수 없는 필드 토큰`() {
        val sessionId = beginSession()

        val quoted = post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":"7"}""")
        val fractional =
            post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":1.7}""")
        val unknownField = post(EDIT_SESSIONS, """{"field":"NO_SUCH_FIELD"}""")

        listOf(quoted, fractional, unknownField).forEach { response ->
            response.statusCode.value() shouldBe 400
            response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
        }
    }

    @Test
    fun `예산 한계는 운영자 선언·세금 포함으로 저장된다 — HTTP 는 통화·출처를 받지 않는다`() {
        val sessionId = beginSession("MIN_BUDGET")

        val provided =
            post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"MIN_BUDGET","amountWon":1000000}""")
        provided.statusCode.value() shouldBe 200
        post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c2","seenRevision":1}""").statusCode.value() shouldBe 200

        val strategy = get("/api/strategy").body
        strategy?.get("minBudgetWon") shouldBe 1000000
        strategy?.get("minBudgetCurrency") shouldBe "KRW"
        strategy?.get("minBudgetVatTreatment") shouldBe "INCLUSIVE"
        strategy?.get("minBudgetProvenance") shouldBe "OPERATOR_DECLARED"
    }

    @Test
    fun `관심 업종은 HTTP 로 바꿀 수 있다 — OPEN-6F9-STRATEGY-WRITE-ENDPOINT 닫힘 근거`() {
        val sessionId = beginSession("FOCUS_CATEGORY")

        post(
            "$EDIT_SESSIONS/$sessionId/value",
            """{"commandId":"c1","field":"FOCUS_CATEGORY","terms":["1234","5678"]}""",
        ).statusCode.value() shouldBe 200
        post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c2","seenRevision":1}""").statusCode.value() shouldBe 200

        get("/api/strategy").body?.get("focusCategories") shouldBe listOf("1234", "5678")
    }

    @Test
    fun `편집 경로도 자격증명 없이는 401 이다 — 필터 무편집으로 덮인다`() {
        val anonymous =
            restTemplate.exchange(
                url(EDIT_SESSIONS),
                HttpMethod.POST,
                HttpEntity("""{"field":"CANDIDATE_LIMIT"}""", HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        anonymous.statusCode.value() shouldBe 401
        anonymous.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
    }
}
