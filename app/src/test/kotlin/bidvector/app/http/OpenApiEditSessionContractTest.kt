package bidvector.app.http

import io.kotest.matchers.shouldBe
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
 * D-6A2b-12 — 편집 endpoint 여섯의 수작성 계약(D-6A1-8 단일 출처)과 구현을 대조한다.
 * 경로 이름이 아니라 **키 집합·상태 코드 집합·필드 어휘**를 `Set.equals` 로 완전 일치
 * 비교한다(부분 포함이 아니다 — 한쪽에만 생긴 값이 곧 RED 다).
 *
 * 평탄성 순회(`OpenApiContractTest` 의 D-6A1-44)는 문서 **전체**를 훑으므로 이 slice 가
 * 더한 스키마도 그 test 가 이미 덮는다 — 여기서 다시 재지 않는다.
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
class OpenApiEditSessionContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @Autowired
    private lateinit var transaction: InMemoryStrategyEditTransaction

    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

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

    /**
     * 문서의 `field` enum ↔ 구현의 필드 어휘 전수가 **양방향** 등식이다. 코드에만 필드를
     * 더하면 HTTP 로 부를 수 없는 필드가 생기고, 문서에만 더하면 있지도 않은 필드를
     * 약속한다 — 둘 다 이 단언이 잡는다.
     */
    @Test
    fun `문서의 field enum 과 구현의 필드 어휘가 양방향으로 같다`() {
        val documented =
            (spec.schema("EditSessionValueRequest")["properties"] as Map<String, Any?>)
                .let { it["field"] as Map<String, Any?> }
                .let { (it["enum"] as List<String>).toSet() }

        documented shouldBe EDITABLE_FIELD_TOKENS
    }

    @Test
    fun `201 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response = post(EDIT_SESSIONS, """{"field":"CANDIDATE_LIMIT"}""")

        response.statusCode.value() shouldBe 201
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("EditSessionResponse")
        response.body?.values?.none(::containsNestedObject) shouldBe true
    }

    @Test
    fun `409 응답의 최상위 키 집합이 ErrorBody 계약과 일치한다`() {
        val sessionId = post(EDIT_SESSIONS, """{"field":"CANDIDATE_LIMIT"}""").body?.get("sessionId") as String
        post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"c1","field":"CANDIDATE_LIMIT","count":7}""")

        val stale = post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c2","seenRevision":0}""")

        stale.statusCode.value() shouldBe 409
        (stale.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        stale.body?.get("code") shouldBe ErrorCode.STALE_REVISION
    }

    @Test
    fun `404 응답의 최상위 키 집합이 ErrorBody 계약과 일치한다`() {
        val response =
            restTemplate.exchange(
                url("$EDIT_SESSIONS/no-such-session"),
                HttpMethod.GET,
                HttpEntity<Void>(headers()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 404
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.SESSION_NOT_FOUND
    }

    @Test
    fun `각 편집 path 의 상태 코드 선언 집합이 기대값과 같다`() {
        val commandCodes = setOf("200", "400", "401", "404", "409", "415", "500")
        spec.responseStatusCodes(EDIT_SESSIONS, "post") shouldBe setOf("201", "400", "401", "409", "415", "500")
        spec.responseStatusCodes("$EDIT_SESSIONS/{sessionId}", "get") shouldBe setOf("200", "401", "404", "500")
        listOf("value", "confirm", "edit", "cancel").forEach { command ->
            spec.responseStatusCodes("$EDIT_SESSIONS/{sessionId}/$command", "post") shouldBe commandCodes
        }
    }

    @Test
    fun `편집 요청 스키마의 필수 키가 구현이 요구하는 것과 같다`() {
        spec.requiredKeys("EditSessionBeginRequest") shouldBe setOf("field")
        spec.requiredKeys("EditSessionValueRequest") shouldBe setOf("commandId", "field")
        spec.requiredKeys("EditSessionConfirmRequest") shouldBe setOf("commandId", "seenRevision")
        spec.requiredKeys("EditSessionEditRequest") shouldBe setOf("commandId", "field")
        spec.requiredKeys("EditSessionCancelRequest") shouldBe setOf("commandId")

        // 필수 키를 하나씩 뺀 본문이 실제로 400 인지 — 문서의 `required` 가 장식이 아님을 잰다.
        post(EDIT_SESSIONS, """{}""").statusCode.value() shouldBe 400
        val sessionId = post(EDIT_SESSIONS, """{"field":"CANDIDATE_LIMIT"}""").body?.get("sessionId") as String
        post("$EDIT_SESSIONS/$sessionId/value", """{"field":"CANDIDATE_LIMIT","count":7}""")
            .statusCode
            .value() shouldBe 400
        post("$EDIT_SESSIONS/$sessionId/confirm", """{"commandId":"c9"}""").statusCode.value() shouldBe 400
        post("$EDIT_SESSIONS/$sessionId/cancel", """{}""").statusCode.value() shouldBe 400
    }

    /** 값 칸 넷의 배정이 문서와 구현에서 같은지 — 각 칸을 실제로 한 번씩 태워 본다. */
    @Test
    fun `값 칸 넷이 각각 그 칸을 읽는 필드에서 200 이다`() {
        val probes =
            listOf(
                "FOCUS_CATEGORY" to """"terms":["1234"]""",
                "MIN_BUDGET" to """"amountWon":1000""",
                "BID_NOW_THRESHOLD" to """"number":0.9""",
                "CANDIDATE_LIMIT" to """"count":5""",
            )

        val statuses =
            probes.map { (field, slot) ->
                val sessionId = post(EDIT_SESSIONS, """{"field":"$field"}""").body?.get("sessionId") as String
                post("$EDIT_SESSIONS/$sessionId/value", """{"commandId":"v-$field","field":"$field",$slot}""")
                    .statusCode
                    .value()
            }

        statuses shouldBe listOf(200, 200, 200, 200)
    }
}
