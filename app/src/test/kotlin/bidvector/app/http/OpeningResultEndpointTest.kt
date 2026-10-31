package bidvector.app.http

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservedBidAmount
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.workflow.opening.CanonicalOpeningFacts
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OpeningResultFigures
import bidvector.workflow.opening.ProvisionalRankOne
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.net.URI
import java.time.Instant

private const val OPENING_RESULTS_PATH = "/api/opening-results"

/** `FixedClock` 이 고정한 지금(`HttpTestApplication`) 기준 — 마감 비교의 양쪽을 만든다. */
private val NOW: Instant = Instant.parse("2026-09-19T00:00:00Z")
private val OBSERVED_AT: Instant = Instant.parse("2026-09-18T01:00:00Z")

/**
 * `GET /api/opening-results` 의 HTTP 층 거동 — 상태 아홉의 응답 토큰, 잠정·확정 칸 분리,
 * 질의 거부. 실 질의 거동은 `bidvector.adapters.opening.JdbcOpeningResultViewTest`(실
 * PostgreSQL)가 잰다.
 *
 * 우회 ①(자격증명 없이 호출)은 `OperatorAuthenticationTest` 의 기계 전수가 이 경로를 포함하지만
 * (등록된 모든 매핑을 거둔다) 여기서 한 번 더 직접 잰다 — 전수가 공허하게 참이 되는 회귀를 두
 * 자리에서 막는다(7C① 과 같은 관례).
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
class OpeningResultEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var openingResults: FakeOpeningResultFactsPort

    @BeforeEach
    fun resetFixture() {
        openingResults.facts = NoticeOpeningFacts.NoticeAbsent
        openingResults.lastLookup = null
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    private fun bodyOf(query: String = "?noticeNumber=NTC-1&noticeRound=001"): Map<String, Any?> =
        (
            restTemplate.exchange(
                URI.create(url("$OPENING_RESULTS_PATH$query")),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>
        ).body ?: error("본문이 없다")

    /** 상태 코드는 **정수로** 본다(7C① 과 같은 관례) — `HttpStatusCode` 구현 타입에 기대지 않는다. */
    private fun statusOf(
        query: String,
        headers: HttpHeaders = authorizedHeaders(),
    ): Int =
        restTemplate
            .exchange(
                URI.create(url("$OPENING_RESULTS_PATH$query")),
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Map::class.java,
            ).statusCode
            .value()

    private fun stateOf(facts: NoticeOpeningFacts): String? {
        openingResults.facts = facts
        return bodyOf()["state"] as String?
    }

    private fun figures(
        participantCount: Int? = 4,
        confirmedAward: AwardAmount? = null,
        provisionalRankOne: ProvisionalRankOne = ProvisionalRankOne.NotObserved,
    ) = OpeningResultFigures(
        participantCount = participantCount,
        actualOpeningAt = Instant.parse("2026-09-17T06:00:00Z"),
        confirmedAward = confirmedAward,
        provisionalRankOne = provisionalRankOne,
        observedAt = OBSERVED_AT,
    )

    private fun canonical(
        progressLabel: String?,
        figures: OpeningResultFigures = figures(),
    ) = NoticeOpeningFacts.Present(
        deadlineAt = NOW.minusSeconds(86_400),
        canonical = CanonicalOpeningFacts(progressLabel, figures),
        openingAxisObserved = false,
    )

    @Test
    fun `우회 ① — 자격증명 없이 부르면 401 이고 포트에 닿지 않는다`() {
        statusOf("?noticeNumber=NTC-1&noticeRound=001", HttpHeaders()) shouldBe 401
        openingResults.lastLookup shouldBe null
    }

    @Test
    fun `상태 아홉이 서로 다른 토큰으로 나간다`() {
        stateOf(NoticeOpeningFacts.NoticeAbsent) shouldBe "NOTICE_ABSENT"
        stateOf(NoticeOpeningFacts.Present(NOW.plusSeconds(86_400), null, false)) shouldBe "NOT_OPENED_YET"
        stateOf(NoticeOpeningFacts.Present(NOW.minusSeconds(86_400), null, false)) shouldBe "NOT_COLLECTED"
        stateOf(NoticeOpeningFacts.Present(NOW.minusSeconds(86_400), null, true)) shouldBe "OBSERVED_NOT_PROMOTED"
        stateOf(canonical("개찰완료")) shouldBe "OPENED"
        stateOf(canonical("유찰")) shouldBe "FAILED_BID"
        stateOf(canonical("재입찰")) shouldBe "REBID"
        stateOf(canonical("개찰취소")) shouldBe "PROGRESS_UNRECOGNIZED"
        stateOf(canonical(null)) shouldBe "PROGRESS_ABSENT"
    }

    @Test
    fun `공고 키는 응답에 그대로 실리고 데이터가 없는 갈래는 값 칸이 전부 빈다`() {
        openingResults.facts = NoticeOpeningFacts.NoticeAbsent

        val body = bodyOf("?noticeNumber=NTC-7&noticeRound=002")

        body["noticeNumber"] shouldBe "NTC-7"
        body["noticeRound"] shouldBe "002"
        body["participantCount"] shouldBe null
        body["observedAt"] shouldBe null
        body["confirmedAwardAmountWon"] shouldBe null
        body["provisionalRankOneState"] shouldBe "NOT_OBSERVED"
    }

    @Test
    fun `우회 ③ — 잠정 1위만 있으면 확정 칸은 전부 비고 잠정 칸만 찬다`() {
        openingResults.facts =
            canonical(
                "개찰완료",
                figures(
                    confirmedAward = null,
                    provisionalRankOne =
                        ProvisionalRankOne.Determined(
                            bidAmount = ObservedBidAmount(912_000_000L, Currency.KRW),
                            bidRate = Rate.ofFraction(BigDecimal("0.87995")),
                            observedAt = OBSERVED_AT,
                        ),
                ),
            )

        val body = bodyOf()

        body["confirmedAwardAmountWon"] shouldBe null
        body["confirmedAwardAmountCurrency"] shouldBe null
        body["confirmedAwardAmountProvenance"] shouldBe null
        body["provisionalRankOneState"] shouldBe "DETERMINED"
        body["provisionalRankOneBidAmountWon"] shouldBe 912_000_000L.toInt()
        body["provisionalRankOneBidAmountCurrency"] shouldBe "KRW"
        body["provisionalRankOneBidAmountVatTreatment"] shouldBe "UNKNOWN"
        body["provisionalRankOneObservedAt"] shouldBe OBSERVED_AT.toString()
        withClue("투찰율은 표기 그대로의 문자열이다 — 배정밀도로 접지 않는다") {
            body["provisionalRankOneBidRateFraction"] shouldBe "0.87995"
        }
    }

    @Test
    fun `확정 낙찰금액은 통화·basis·과세·provenance 와 함께 나간다`() {
        openingResults.facts =
            canonical(
                "개찰완료",
                figures(
                    confirmedAward =
                        AwardAmount(880_000_000L, Currency.KRW, Provenance.Published(NoticeRound.of("001"))),
                ),
            )

        val body = bodyOf()

        body["confirmedAwardAmountWon"] shouldBe 880_000_000L.toInt()
        body["confirmedAwardAmountCurrency"] shouldBe "KRW"
        body["confirmedAwardAmountBasis"] shouldBe "AWARD"
        body["confirmedAwardAmountVatTreatment"] shouldBe "UNKNOWN"
        body["confirmedAwardAmountProvenance"] shouldBe "PUBLISHED"
    }

    @Test
    fun `순위 중복은 건수와 함께 나가고 투찰 칸은 비어 있다`() {
        openingResults.facts =
            canonical(
                "개찰완료",
                figures(provisionalRankOne = ProvisionalRankOne.RankDuplicated(3, OBSERVED_AT)),
            )

        val body = bodyOf()

        body["provisionalRankOneState"] shouldBe "RANK_DUPLICATED"
        body["provisionalRankOneDuplicateCount"] shouldBe 3
        body["provisionalRankOneBidAmountWon"] shouldBe null
        body["provisionalRankOneBidRateFraction"] shouldBe null
    }

    @Test
    fun `공고번호는 수집이 쓰는 정규화를 지나 포트로 간다`() {
        bodyOf("?noticeNumber=ntc%20abc&noticeRound=000")

        openingResults.lastLookup shouldBe NoticeId(NoticeNumber.of("ntc abc"), NoticeRound.of("000"))
        openingResults.lastLookup
            ?.number
            ?.value shouldBe "NTC-ABC"
    }

    @Test
    fun `필수 칸이 없으면 400 이고 포트에 닿지 않는다`() {
        statusOf("") shouldBe 400
        statusOf("?noticeNumber=NTC-1") shouldBe 400
        statusOf("?noticeRound=001") shouldBe 400
        openingResults.lastLookup shouldBe null
    }

    @Test
    fun `차수 형식·제어 문자·길이 상한 위반은 조용히 고치지 않고 400 이다`() {
        statusOf("?noticeNumber=NTC-1&noticeRound=1") shouldBe 400
        statusOf("?noticeNumber=NTC-1&noticeRound=0001") shouldBe 400
        statusOf("?noticeNumber=NTC%001&noticeRound=001") shouldBe 400
        statusOf("?noticeNumber=${"N".repeat(201)}&noticeRound=001") shouldBe 400
        openingResults.lastLookup shouldBe null
    }

    @Test
    fun `우회 ⑨ — 같은 매개변수를 두 번 보내면 400 이고 조용히 합치지 않는다`() {
        statusOf("?noticeNumber=NTC-1&noticeNumber=NTC-2&noticeRound=001") shouldBe 400
        openingResults.lastLookup shouldBe null
    }

    /**
     * **소켓으로 보낸다** — `URI.create` 가 `%G1`(이스케이프 **문법** 오류)을 서버에 닿기 전에
     * 거부한다(7C① 이 같은 자리에서 실측한 사실). 재려는 것은 서버가 그 바이트열에 무슨 상태를
     * 내는가다. 계약 **밖 이름**이 깨진 꼴도 같은 거부로 간다 — 질의 해석은 전체 단위다.
     */
    @Test
    fun `우회 ⑨ — 이스케이프 문법이 틀린 질의도 500 이 아니라 400 이다`() {
        rawGet(port, "$OPENING_RESULTS_PATH?noticeNumber=%G1&noticeRound=001", TEST_CREDENTIAL).status shouldBe 400
        rawGet(
            port,
            "$OPENING_RESULTS_PATH?noticeNumber=NTC-1&noticeRound=001&foo=%G1",
            TEST_CREDENTIAL,
        ).status shouldBe 400
        openingResults.lastLookup shouldBe null
    }

    @Test
    fun `알 수 없는 매개변수는 무시한다 — 과잉 차단 대조`() {
        statusOf("?noticeNumber=NTC-1&noticeRound=001&unknown=x&unknown=y") shouldBe 200
    }
}
