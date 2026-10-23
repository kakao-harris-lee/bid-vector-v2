package bidvector.app.http

import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeTitle
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.suggestion.SUGGESTION_UNJUDGED_AXES
import bidvector.workflow.suggestion.SuggestionState
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
import java.time.LocalDate

private const val SUGGESTIONS_PATH = "/api/suggestions"

/** 제안 카드가 요약 카드에 **더하는** 속성 — 면허 축 판정. 이 밖의 차이는 카드 형태의 분기다. */
private val LICENSE_AXIS_KEYS = setOf("licenseVerdict", "licenseUncertainReason", "unjudgedAxes")

/**
 * `GET /api/suggestions` 의 **문서 ↔ 구현** 대조(D-6A1-8 수작성 단일 출처, `OpenApiNoticeSearchContractTest` 와
 * 같은 자리).
 *
 *  ① **같은 카드 형태** — `SuggestedNotice` 를 `NoticeSummary` 키로 제한하면 **정의까지** `NoticeSummary` 와
 *    같다. `$ref`·`allOf` 는 평탄 계약(D-6A1-38)이 거부하므로 같음을 이 등식이 진다. 둘의 차이는 면허 축 셋뿐이다.
 *  ② 응답 키 집합 등식(여분 필드가 생기면 깨진다) + 원시 관측 어휘 부재.
 *  ③ 헤더 넷과 어휘(`X-Result-State`·면허 판정·미결 사유·미판정 축)가 구현의 전수와 같다.
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
class OpenApiSuggestionContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var candidates: FakeSuggestionCandidates

    @Autowired
    private lateinit var licenseGate: FakeSuggestionLicenseGate

    @Autowired
    private lateinit var strategies: TestStrategyRepository

    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    @BeforeEach
    fun resetFixture() {
        // 감시 조건(필수 키워드 「표본」)을 지나는 표본 하나 — 카드 하나가 나와야 키를 잰다.
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val draft = StrategyDraft(requiredKeywordTerms = listOf("표본"))
        strategies.strategy = (validate(draft, StrategyRevision(1), policy) as StrategyValidation.Valid).strategy
        candidates.notices = listOf(sampleNotice())
        licenseGate.verdictFor = { LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared) }
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun get(): ResponseEntity<List<Map<String, Any?>>> =
        restTemplate.exchange(
            URI.create(url(SUGGESTIONS_PATH)),
            HttpMethod.GET,
            HttpEntity<Void>(authorizedHeaders()),
            List::class.java,
        ) as ResponseEntity<List<Map<String, Any?>>>

    private fun sampleNotice(): Notice {
        val id = NoticeId(NoticeNumber.of("CONTRACT-S1"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-01T00:00:00Z"),
            )
        return Notice.collected(
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = Instant.parse("2026-11-01T05:00:00Z"),
                openingScheduledAt = null,
                raw = observation,
                title = NoticeTitle.of("계약 표본"),
            ),
        )
    }

    @Test
    fun `선언된 상태 코드 집합이 계약과 일치한다`() {
        spec.responseStatusCodes(SUGGESTIONS_PATH, "get") shouldBe setOf("200", "401", "409", "500")
    }

    @Test
    fun `제안 카드는 요약 카드와 같은 속성을 같은 정의로 갖고 차이는 면허 축 셋뿐이다`() {
        val summary = spec.schema("NoticeSummary")["properties"] as Map<String, Any?>
        val suggested = spec.schema("SuggestedNotice")["properties"] as Map<String, Any?>

        suggested.filterKeys { it in summary.keys } shouldBe summary
        suggested.keys - summary.keys shouldBe LICENSE_AXIS_KEYS
        spec.requiredKeys("SuggestedNotice") - LICENSE_AXIS_KEYS shouldBe spec.requiredKeys("NoticeSummary")
    }

    @Test
    fun `응답 키 집합이 계약과 완전히 일치한다 — 여분 필드가 생기면 깨진다`() {
        val row = get().body?.single() ?: error("감시를 통과한 표본이 없다 — 표본·전략 fixture 를 확인하라")

        row.keys shouldBe spec.propertyKeys("SuggestedNotice")
    }

    @Test
    fun `제안 카드에 원시 관측·감사·투찰자 어휘가 없다`() {
        val forbidden = listOf("payload", "observation", "raw", "bidder", "company", "participant", "audit")

        val declared = spec.propertyKeys("SuggestedNotice")

        declared.filter { key -> forbidden.any { key.lowercase().contains(it) } }.shouldBeEmpty()
    }

    @Test
    fun `200 응답이 선언한 헤더 넷을 실제로 낸다 — 결과 상태 어휘가 구현의 전수와 같다`() {
        val declared = spec.suggestionResponseHeaders()
        val response = get()

        declared.keys shouldBe
            setOf(STRATEGY_REVISION_HEADER, COMPUTED_AT_HEADER, TOTAL_COUNT_HEADER, RESULT_STATE_HEADER)
        declared.keys.forEach { name -> (response.headers.getFirst(name) != null) shouldBe true }
        val stateSchema = (declared.getValue(RESULT_STATE_HEADER) as Map<String, Any?>)["schema"] as Map<String, Any?>
        (stateSchema["enum"] as List<String>).toSet() shouldBe SuggestionState.entries.map { it.name }.toSet()
    }

    @Test
    fun `면허 축 어휘가 구현이 실제로 내는 값의 전수와 같다`() {
        val properties = spec.schema("SuggestedNotice")["properties"] as Map<String, Any?>
        // 사유 목록은 sealed 계층에서 도출한다 — 여섯째 사유가 생기면 이 test 가 따라온다(손 목록 금지, D-7C1B-4).
        val reasons =
            UncertainReason::class.sealedSubclasses.map { it.objectInstance ?: error("사유는 data object 다: $it") }
        val emittedByReason =
            reasons.associateWith { reason ->
                licenseGate.verdictFor = { LicenseVerdict.Uncertain(reason) }
                get().body?.single()?.get("licenseUncertainReason")
            }
        licenseGate.verdictFor = { LicenseVerdict.Eligible(emptySet()) }
        val eligible = get().body?.single() ?: error("본문이 비었다")

        // 사유별 **쌍** 등식 — 집합 등식은 라벨 둘을 맞바꿔도 초록이다(verifier r2 F-1). 기대 라벨은 컨트롤러의
        // `when` 이 아니라 사유 이름에서 따로 만든다(SCREAMING_SNAKE).
        emittedByReason shouldBe reasons.associateWith { it::class.simpleName.orEmpty().screamingSnake() }
        // `null` 은 `ELIGIBLE` 카드가 실제로 내는 값이다 — 어휘 등식은 그것까지 포함해 잰다(cr L-2).
        eligible["licenseUncertainReason"] shouldBe null
        properties.enumOf("licenseUncertainReason") shouldBe
            emittedByReason.values.toSet() + eligible["licenseUncertainReason"]
        properties.enumOf("licenseVerdict") shouldBe setOf("UNCERTAIN", eligible["licenseVerdict"])
        (properties["unjudgedAxes"] as Map<String, Any?>).let { (it["items"] as Map<String, Any?>)["enum"] } shouldBe
            SUGGESTION_UNJUDGED_AXES.map { it.name }
    }
}

/** `RequirementDataAbsent` → `REQUIREMENT_DATA_ABSENT`. */
private fun String.screamingSnake(): String = replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").uppercase()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.enumOf(property: String): Set<Any?> =
    ((this[property] as Map<String, Any?>)["enum"] as List<Any?>).toSet()

/** `paths./api/suggestions.get.responses.200.headers`. */
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.suggestionResponseHeaders(): Map<String, Any?> {
    val pathItem = (this["paths"] as Map<String, Any?>)[SUGGESTIONS_PATH] as Map<String, Any?>
    val operation = pathItem["get"] as Map<String, Any?>
    val response = (operation["responses"] as Map<String, Any?>)["200"] as Map<String, Any?>
    return response["headers"] as Map<String, Any?>
}
