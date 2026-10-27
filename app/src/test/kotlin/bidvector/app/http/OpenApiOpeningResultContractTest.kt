package bidvector.app.http

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.opening.CanonicalOpeningFacts
import bidvector.workflow.opening.MAX_NOTICE_KEY_LENGTH
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OpeningProgress
import bidvector.workflow.opening.OpeningResultFigures
import bidvector.workflow.opening.OpeningResultView
import bidvector.workflow.opening.ProvisionalRankOne
import io.kotest.matchers.collections.shouldBeEmpty
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

private const val OPENING_RESULTS_PATH = "/api/opening-results"
private const val OPENING_RESULT_SCHEMA = "OpeningResultView"

/**
 * `GET /api/opening-results` 의 **문서 ↔ 구현** 대조(기존 계약 test 관례와 같은 자리).
 *
 * 네 축을 잰다.
 *  ① 응답의 **키 집합 등식** — 여분 필드가 생기면 깨진다. (2b) 의 「응답에 다른 업체 식별
 *    정보가 실리지 않는다」가 이 등식 + 어휘 교집합 공집합으로 닫힌다.
 *  ② 상태 어휘 **양방향 등식** — 문서의 enum 과 구현이 실제로 내는 토큰 전수가 같다. 한쪽만
 *    늘면 깨진다(구현 쪽 전수는 소진 `when` 이 컴파일로 지킨다).
 *  ③ 선언된 한계 수치와 Kotlin 상수의 등식.
 *  ④ **문서가 든 질의 매개변수 전수**가 실제로 효과를 낸다 — 문서에만 있고 구현이 무시하는
 *    매개변수가 조용히 남지 못한다.
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
class OpenApiOpeningResultContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var openingResults: FakeOpeningResultFactsPort

    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    private val sampleId = NoticeId(NoticeNumber.of("CONTRACT-OPEN-1"), NoticeRound.of("000"))

    @BeforeEach
    fun resetFixture() {
        openingResults.facts = NoticeOpeningFacts.NoticeAbsent
        openingResults.lastLookup = null
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun bodyOf(query: String): Map<String, Any?> =
        (
            restTemplate.exchange(
                URI.create(url("$OPENING_RESULTS_PATH$query")),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>
        ).body ?: error("본문이 없다")

    @Test
    fun `선언된 상태 코드 집합이 계약과 일치한다`() {
        spec.responseStatusCodes(OPENING_RESULTS_PATH, "get") shouldBe setOf("200", "400", "401", "500")
    }

    @Test
    fun `응답의 키 집합이 계약과 완전히 일치한다 — 여분 필드가 생기면 깨진다`() {
        openingResults.facts =
            NoticeOpeningFacts.Present(
                deadlineAt = null,
                canonical = CanonicalOpeningFacts("개찰완료", figures()),
                openingAxisObserved = false,
            )

        val body = bodyOf("?noticeNumber=CONTRACT-OPEN-1&noticeRound=000")

        body.keys shouldBe spec.propertyKeys(OPENING_RESULT_SCHEMA)
        spec.requiredKeys(OPENING_RESULT_SCHEMA) shouldBe
            setOf("noticeNumber", "noticeRound", "state", "provisionalRankOneState")
    }

    /**
     * (2b) — 응답에 **업체 식별·원시 관측·감사 축의 어휘가 없다.** 위 키 집합 등식은 문서와
     * 구현을 **함께** 바꾸면 지나간다. 이 단언이 그 편집도 어휘 수준에서 막는다.
     */
    @Test
    fun `응답에 업체 상호·사업자번호·대표자·원시 관측 어휘가 없다`() {
        val forbidden =
            listOf("company", "bidder", "corp", "bizno", "ceo", "representative", "name", "payload", "raw", "audit")

        val declared = spec.propertyKeys(OPENING_RESULT_SCHEMA)

        declared.filter { key -> forbidden.any { word -> key.lowercase().contains(word) } }.shouldBeEmpty()
    }

    @Test
    fun `상태 어휘가 구현이 내는 전수와 같다`() {
        val documented = spec.enumOf(OPENING_RESULT_SCHEMA, "state")

        documented shouldBe implementationStates()
    }

    @Test
    fun `잠정 1위 상태 어휘가 구현이 내는 전수와 같다`() {
        val documented = spec.enumOf(OPENING_RESULT_SCHEMA, "provisionalRankOneState")

        documented shouldBe implementationRankOneStates()
    }

    @Test
    fun `선언된 길이 상한이 Kotlin 상수와 같다`() {
        val parameters = spec.queryParametersOf(OPENING_RESULTS_PATH, "get")

        parameters.schemaFor("noticeNumber")["maxLength"] shouldBe MAX_NOTICE_KEY_LENGTH
        parameters.schemaFor("noticeRound")["maxLength"] shouldBe MAX_NOTICE_KEY_LENGTH
    }

    @Test
    fun `문서가 든 질의 매개변수 전수가 필수이고 실제로 효과를 낸다`() {
        val declared = spec.queryParametersOf(OPENING_RESULTS_PATH, "get")

        declared.map { it["name"] as String }.toSet() shouldBe setOf("noticeNumber", "noticeRound")
        declared.all { it["required"] == true } shouldBe true

        bodyOf("?noticeNumber=CONTRACT-OPEN-1&noticeRound=000")
        openingResults.lastLookup shouldBe sampleId
        bodyOf("?noticeNumber=CONTRACT-OPEN-2&noticeRound=001")
        openingResults.lastLookup shouldBe NoticeId(NoticeNumber.of("CONTRACT-OPEN-2"), NoticeRound.of("001"))
    }

    /** 구현이 **실제로 내는** 상태 토큰 전수 — 갈래가 늘면 소진 `when` 이 컴파일에서 막고, 여기서 문서와 대조된다. */
    private fun implementationStates(): Set<String> {
        val views =
            listOf(
                OpeningResultView.NoticeAbsent,
                OpeningResultView.NotOpenedYet,
                OpeningResultView.NotCollected,
                OpeningResultView.ObservedNotPromoted,
            ) + OpeningProgress.entries.map { OpeningResultView.Observed(it, figures()) }
        return views.map { OpeningResultResponse.of(sampleId, it).state }.toSet()
    }

    private fun implementationRankOneStates(): Set<String> =
        listOf(
            ProvisionalRankOne.NotObserved,
            ProvisionalRankOne.RankMissing(OBSERVED),
            ProvisionalRankOne.RankDuplicated(2, OBSERVED),
            ProvisionalRankOne.Determined(null, null, OBSERVED),
        ).map { rankOne ->
            OpeningResultResponse
                .of(
                    sampleId,
                    OpeningResultView.Observed(OpeningProgress.OPENED, figures(provisionalRankOne = rankOne)),
                ).provisionalRankOneState
        }.toSet()

    private fun figures(provisionalRankOne: ProvisionalRankOne = ProvisionalRankOne.NotObserved) =
        OpeningResultFigures(
            participantCount = 2,
            actualOpeningAt = OBSERVED,
            confirmedAward = null,
            provisionalRankOne = provisionalRankOne,
            observedAt = OBSERVED,
        )

    private companion object {
        private val OBSERVED: Instant = Instant.parse("2026-09-18T01:00:00Z")
    }
}

/** `components.schemas.<name>.properties.<property>.enum` 을 집합으로 — 문서 쪽 어휘의 유일한 독출 자리. */
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.enumOf(
    schemaName: String,
    property: String,
): Set<String> {
    val properties = schema(schemaName)["properties"] as Map<String, Any?>
    val definition = properties.getValue(property) as Map<String, Any?>
    return (definition["enum"] as List<String>).toSet()
}

/**
 * `paths.<path>.<method>.parameters` 의 `in: query` 항목들.
 *
 * 7C① 의 계약 test 에 같은 모양의 private 사본이 있다 — 공용 자리로 옮기는 편집은 그 파일을
 * 만지므로 「질의 읽기 자리 재사용」 결정과 함께 처리한다(evidence 보고 항목).
 */
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.queryParametersOf(
    path: String,
    method: String,
): List<Map<String, Any?>> {
    val paths = getValue("paths") as Map<String, Any?>
    val operation = (paths.getValue(path) as Map<String, Any?>).getValue(method) as Map<String, Any?>
    val parameters = operation["parameters"] as? List<Map<String, Any?>> ?: emptyList()
    return parameters.filter { it["in"] == "query" }
}

@Suppress("UNCHECKED_CAST")
private fun List<Map<String, Any?>>.schemaFor(name: String): Map<String, Any?> =
    (single { it["name"] == name }["schema"] as Map<String, Any?>)
