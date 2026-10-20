package bidvector.app.http

import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.notice.NoticeSearchLimits
import bidvector.workflow.notice.NoticeSearchPage
import bidvector.workflow.notice.NoticeSearchState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

private const val NOTICES_PATH = "/api/notices"

/**
 * `GET /api/notices` 의 **문서 ↔ 구현** 대조(D-6A1-8 수작성 단일 출처, 기존 계약 test 관례 —
 * `OpenApiDryRunContractTest`·`OpenApiEditSessionContractTest` 와 같은 자리).
 *
 * 세 축을 잰다.
 *  ① 응답 요약의 **키 집합 등식** — 여분 필드가 생기면 깨진다. (2b) 값 획득 축의 「응답에
 *    원시 관측·PII 가 실리지 않는다」가 이 등식 + 어휘 교집합 공집합으로 닫힌다.
 *  ② 선언된 한계 수치와 Kotlin 상수의 **양방향 등식** — 상한이 두 자리에서 갈라지지 않는다.
 *  ③ **문서가 든 질의 매개변수 전수**가 실제로 조건에 효과를 낸다 — 문서에만 있고 구현이
 *    무시하는 매개변수(「상한을 적어 두고 안 거는」 형태)가 조용히 남지 못한다. 새 매개변수를
 *    문서에 더하면 탐침 표에 그 이름이 없어 이 test 가 먼저 붉어진다(fail-closed).
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
class OpenApiNoticeSearchContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var notices: FakeNoticeSearchPort

    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    @BeforeEach
    fun resetFixture() {
        notices.page = NoticeSearchPage(emptyList(), 0L, NoticeSearchState.NOTHING_COLLECTED)
        notices.lastCriteria = null
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun rowsOf(query: String): List<Map<String, Any?>> =
        (
            restTemplate.exchange(
                URI.create(url("$NOTICES_PATH$query")),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                List::class.java,
            ) as ResponseEntity<List<Map<String, Any?>>>
        ).body ?: emptyList()

    private fun sampleNotice(): Notice {
        val id = NoticeId(NoticeNumber.of("CONTRACT-1"), NoticeRound.of("000"))
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
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            ),
        )
    }

    @Test
    fun `선언된 상태 코드 집합이 계약과 일치한다`() {
        spec.responseStatusCodes(NOTICES_PATH, "get") shouldBe setOf("200", "400", "401", "500")
    }

    @Test
    fun `응답 요약의 키 집합이 계약과 완전히 일치한다 — 여분 필드가 생기면 깨진다`() {
        notices.page = NoticeSearchPage(listOf(sampleNotice()), 1L, NoticeSearchState.MATCHED)

        val row = rowsOf("").single()

        row.keys shouldBe spec.propertyKeys("NoticeSummary")
        spec.requiredKeys("NoticeSummary") shouldBe setOf("noticeNumber", "noticeRound", "status")
    }

    /**
     * (2b) — 응답 요약에 **원시 관측·감사·투찰자 축의 어휘가 없다.** 위 키 집합 등식이 이미
     * 여분 필드를 막지만, 그 등식은 문서와 구현을 **함께** 바꾸면 지나간다. 이 단언은 그
     * 「함께 바꾸는」 편집도 어휘 수준에서 막는다.
     */
    @Test
    fun `응답 요약에 원시 관측·감사·투찰자 어휘가 없다`() {
        val forbidden = listOf("payload", "observation", "raw", "bidder", "company", "participant", "audit")

        val declared = spec.propertyKeys("NoticeSummary")

        declared.filter { key -> forbidden.any { word -> key.lowercase().contains(word) } }.shouldBeEmpty()
    }

    @Test
    fun `선언된 한계 수치가 Kotlin 상수와 같다 — 상한이 두 자리에서 갈라지지 않는다`() {
        val parameters = spec.queryParameters(NOTICES_PATH, "get")

        parameters.schemaOf("q")["maxLength"] shouldBe NoticeSearchLimits.MAX_TEXT_LENGTH
        parameters.schemaOf("agency")["maxLength"] shouldBe NoticeSearchLimits.MAX_TEXT_LENGTH
        parameters.schemaOf("size")["maximum"] shouldBe NoticeSearchLimits.MAX_PAGE_SIZE
        parameters.schemaOf("size")["default"] shouldBe NoticeSearchLimits.DEFAULT_PAGE_SIZE
        parameters.schemaOf("size")["minimum"] shouldBe 1
        parameters.schemaOf("page")["default"] shouldBe 0
        parameters.schemaOf("page")["minimum"] shouldBe 0
        parameters.schemaOf("amountMin")["minimum"] shouldBe 0
        parameters.schemaOf("amountMax")["minimum"] shouldBe 0
    }

    @Test
    fun `200 응답이 선언한 헤더 둘을 실제로 낸다 — 어휘도 구현의 전수와 같다`() {
        val headers = spec.responseHeaders(NOTICES_PATH, "get", "200")

        headers.keys shouldBe setOf(TOTAL_COUNT_HEADER, RESULT_STATE_HEADER)
        val stateSchema = (headers.getValue(RESULT_STATE_HEADER) as Map<String, Any?>)["schema"] as Map<String, Any?>
        (stateSchema["enum"] as List<String>).toSet() shouldBe NoticeSearchState.entries.map { it.name }.toSet()
    }

    /**
     * ③ 문서가 든 매개변수 전수가 효과를 낸다. 탐침 값은 **기본값과 다른** 값이라, 조건이
     * 기본과 같게 나오면 그 매개변수는 무시된 것이다.
     */
    @Test
    fun `문서가 든 질의 매개변수 전수가 조건에 효과를 낸다`() {
        val probes =
            mapOf(
                "q" to "%EA%B3%B5%EA%B3%A0",
                "agency" to "%EC%A1%B0%EB%8B%AC%EC%B2%AD",
                "amountMin" to "1000",
                "amountMax" to "2000",
                "deadlineFrom" to "2026-11-01T00:00:00Z",
                "deadlineTo" to "2026-11-02T00:00:00Z",
                "page" to "3",
                "size" to "7",
            )
        val declared = spec.queryParameters(NOTICES_PATH, "get").map { it["name"] as String }.toSet()

        declared shouldBe probes.keys

        rowsOf("")
        val base = notices.lastCriteria ?: error("기본 조건이 전달되지 않았다")
        declared.forEach { name ->
            val probe = probes[name] ?: error("탐침 값이 없는 매개변수: $name")
            notices.lastCriteria = null
            rowsOf("?$name=$probe")
            val probed = notices.lastCriteria ?: error("조건이 전달되지 않았다: $name")
            probed shouldNotBe base
        }
    }
}

/** `paths.<path>.<method>.parameters` 의 `in: query` 항목들. */
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.queryParameters(
    path: String,
    method: String,
): List<Map<String, Any?>> {
    val pathItem = (this["paths"] as Map<String, Any?>)[path] as Map<String, Any?>
    val operation = pathItem[method] as Map<String, Any?>
    val parameters = operation["parameters"] as List<Map<String, Any?>>
    return parameters.filter { it["in"] == "query" }
}

@Suppress("UNCHECKED_CAST")
private fun List<Map<String, Any?>>.schemaOf(name: String): Map<String, Any?> =
    single { it["name"] == name }["schema"] as Map<String, Any?>

/** `paths.<path>.<method>.responses.<status>.headers`. */
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.responseHeaders(
    path: String,
    method: String,
    status: String,
): Map<String, Any?> {
    val pathItem = (this["paths"] as Map<String, Any?>)[path] as Map<String, Any?>
    val operation = pathItem[method] as Map<String, Any?>
    val response = (operation["responses"] as Map<String, Any?>)[status] as Map<String, Any?>
    return response["headers"] as Map<String, Any?>
}
