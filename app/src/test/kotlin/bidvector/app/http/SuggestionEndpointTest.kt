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
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
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

/**
 * `GET /api/suggestions` 의 HTTP 층 거동 — 카드 형태(평탄·위임)·면허 축 값·신선도 헤더·표시 상한·인증.
 * 감시 게이트는 실 구현(`NoticeWatchSubjectPort`)이라 전략의 감시 조건이 실제로 거른다. 실 DB 의
 * 전 편집 경로 거동은 `SuggestionProductionE2ETest` 가 잰다.
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
class SuggestionEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var candidates: FakeSuggestionCandidates

    @Autowired
    private lateinit var licenseGate: FakeSuggestionLicenseGate

    @Autowired
    private lateinit var strategies: TestStrategyRepository

    private val deadline = Instant.parse("2026-11-01T05:00:00Z")
    private val paving = notice("PAVE-1", "도로 포장 공사")
    private val sewer = notice("SEWER-1", "하수관로 정비")
    private val paving2 = notice("PAVE-2", "보도 포장 보수")

    @BeforeEach
    fun resetFixture() {
        strategies.strategy = keywordStrategy(revision = 4)
        candidates.notices = emptyList()
        licenseGate.verdictFor = { LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared) }
    }

    private fun keywordStrategy(
        revision: Int,
        candidateLimit: Int? = null,
    ): OperatorStrategy {
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val draft = StrategyDraft(requiredKeywordTerms = listOf("포장"), candidateLimit = candidateLimit)
        return (validate(draft, StrategyRevision(revision), policy) as StrategyValidation.Valid).strategy
    }

    private fun notice(
        number: String,
        title: String,
    ): Notice {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
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
                deadlineAt = deadline,
                openingScheduledAt = null,
                raw = observation,
                title = NoticeTitle.of(title),
            ),
        )
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun get(query: String = ""): ResponseEntity<List<Map<String, Any?>>> =
        restTemplate.exchange(
            URI.create(url("/api/suggestions$query")),
            HttpMethod.GET,
            HttpEntity<Void>(authorizedHeaders()),
            List::class.java,
        ) as ResponseEntity<List<Map<String, Any?>>>

    @Test
    fun `자격증명이 없으면 401 이고 본문은 ErrorBody 다`() {
        val response =
            restTemplate.getForEntity(url("/api/suggestions"), Map::class.java) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 401
        response.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
    }

    @Test
    fun `감시를 통과한 공고만 평탄한 카드로 내고 신선도 헤더를 싣는다`() {
        candidates.notices = listOf(paving, sewer)

        val response = get()

        response.statusCode.value() shouldBe 200
        response.headers.getFirst(STRATEGY_REVISION_HEADER) shouldBe "4"
        response.headers.getFirst(COMPUTED_AT_HEADER) shouldBe "2026-09-19T00:00:00Z"
        response.headers.getFirst(TOTAL_COUNT_HEADER) shouldBe "1"
        response.headers.getFirst(RESULT_STATE_HEADER) shouldBe "MATCHED"
        val row = response.body?.single() ?: error("본문이 비었다")
        // 위임한 카드 속성이 **최상위**에 평탄하게 나온다 — 요약을 품은 중첩 키가 없다.
        row.keys shouldBe SUGGESTED_NOTICE_KEYS
        row["noticeNumber"] shouldBe "PAVE-1"
        row["title"] shouldBe "도로 포장 공사"
        row["deadlineAt"] shouldBe deadline.toString()
        row["licenseVerdict"] shouldBe "UNCERTAIN"
        row["licenseUncertainReason"] shouldBe "OPERATOR_LICENSES_NOT_DECLARED"
        row["unjudgedAxes"] shouldBe listOf("QUAL_08_REGION", "QUAL_11_CONSTRUCTION_CAPACITY")
    }

    @Test
    fun `면허 충족이면 미결 사유 키는 남고 값은 null 이다`() {
        candidates.notices = listOf(paving)
        licenseGate.verdictFor = { LicenseVerdict.Eligible(emptySet()) }

        val row = get().body?.single() ?: error("본문이 비었다")

        row["licenseVerdict"] shouldBe "ELIGIBLE"
        row.containsKey("licenseUncertainReason") shouldBe true
        row["licenseUncertainReason"] shouldBe null
    }

    @Test
    fun `표시 상한은 전략 값이고 총수 헤더는 자르기 전 건수다`() {
        strategies.strategy = keywordStrategy(revision = 5, candidateLimit = 1)
        candidates.notices = listOf(sewer, paving, paving2)

        val response = get()

        response.body?.map { it["noticeNumber"] } shouldBe listOf("PAVE-1")
        response.headers.getFirst(TOTAL_COUNT_HEADER) shouldBe "2"
    }

    @Test
    fun `저장 뒤 조회는 새 전략으로 계산된다 — 옛 결과가 보이지 않는다`() {
        candidates.notices = listOf(paving, sewer)
        get().body?.map { it["noticeNumber"] } shouldBe listOf("PAVE-1")

        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val sewerDraft = StrategyDraft(requiredKeywordTerms = listOf("하수"))
        strategies.strategy = (validate(sewerDraft, StrategyRevision(6), policy) as StrategyValidation.Valid).strategy

        val after = get()
        after.headers.getFirst(STRATEGY_REVISION_HEADER) shouldBe "6"
        after.body?.map { it["noticeNumber"] } shouldBe listOf("SEWER-1")
    }

    @Test
    fun `빈 결과의 뜻이 헤더로 갈린다`() {
        get().headers.getFirst(RESULT_STATE_HEADER) shouldBe "NO_OPEN_NOTICES"

        candidates.notices = listOf(sewer)
        get().headers.getFirst(RESULT_STATE_HEADER) shouldBe "NO_MATCH"

        strategies.strategy = keywordStrategyWithoutWatch()
        get().headers.getFirst(RESULT_STATE_HEADER) shouldBe "WATCH_NOT_CONFIGURED"
    }

    private fun keywordStrategyWithoutWatch(): OperatorStrategy {
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        return (validate(StrategyDraft(), StrategyRevision(2), policy) as StrategyValidation.Valid).strategy
    }

    @Test
    fun `질의를 읽지 않으므로 계약 밖 매개변수나 틀린 이스케이프도 거동을 바꾸지 않는다`() {
        candidates.notices = listOf(paving)

        get("?candidateLimit=999").body?.size shouldBe 1
        rawGet(port, "/api/suggestions?foo=%G1", TEST_CREDENTIAL).status shouldBe 200
    }
}

/** 제안 카드의 키 전수 — 요약 12 + 면허 축 3. */
private val SUGGESTED_NOTICE_KEYS =
    setOf(
        "noticeNumber",
        "noticeRound",
        "status",
        "title",
        "demandAgencyName",
        "noticeAgencyName",
        "baseAmountWon",
        "baseAmountCurrency",
        "baseAmountBasis",
        "baseAmountVatTreatment",
        "baseAmountProvenance",
        "deadlineAt",
        "licenseVerdict",
        "licenseUncertainReason",
        "unjudgedAxes",
    )
