package bidvector.app.http

import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.SubmittedBidAmount
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.settlement.AwardWinnerTradeName
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.opening.CanonicalOpeningFacts
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OpeningResultFigures
import bidvector.workflow.opening.ProvisionalRankOne
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.NaraReadiness
import bidvector.workflow.profile.TradeName
import bidvector.workflow.access.AccountId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import java.time.Instant

/** 심어 두고 **전문 어디에도** 나오면 안 되는 표지 — 다른 업체 상호의 자리를 wire 에서 잰다. */
private const val WINNER_SENTINEL = "ZZ-SENTINEL-WINNER-ZZ"

/**
 * 결과 조회 operation 의 **HTTP 거동**(M7/7C⑤-b) — 세션으로 실제로 부른다.
 *
 * 여기서 잠그는 것:
 *  · 계정 없는 인증(자격 헤더 경로)은 **401** 이다 — 계정 범위 뿌리이기 때문이다.
 *  · **낙찰자 상호가 응답 전문에 없다** — 칸 이름이 아니라 **값**으로 잰다(이름을 바꿔 숨기는
 *    변경을 계약 test 가 아니라 이 단언이 잡는다). 양성 대조로 그 상호가 실제로 판정에
 *    닿았음을 함께 보인다(닿지 않았다면 이 단언은 아무것도 재지 않는다).
 *  · 분모 0 이 **0% 가 아니라 상태**로 나간다.
 *  · 운영자 기록과 대사가 **다른 칸**으로 함께 나간다.
 */
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND,
        PROP_NO_STATIC_RESOURCE_MAPPINGS,
        PROP_NO_MANAGEMENT_SERVER,
    ],
)
class MyBidResultEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    @Autowired
    private lateinit var bids: FakeRecordedBids

    @Autowired
    private lateinit var openingFacts: FakeBatchOpeningFacts

    @Autowired
    private lateinit var winnerNames: FakeAwardWinnerNames

    @Autowired
    private lateinit var profiles: InMemoryBusinessProfileStore

    private lateinit var session: SessionTestClient

    private val notice = NoticeKey("R26BK-RESULT-1", NoticeRound.of("000"))
    private val noticeId = NoticeId(NoticeNumber.of(notice.noticeNumber), notice.noticeRound)

    @BeforeEach
    fun signIn() {
        access.reset()
        bids.reset()
        openingFacts.reset()
        winnerNames.reset()
        profiles.reset()
        session = SessionTestClient(restTemplate, ::url).signIn()
    }

    private fun amount(won: Long) =
        SubmittedBidAmount(won, Currency.KRW, VatTreatment.INCLUSIVE, BidAmountOrigin.OperatorEntered)

    private fun seedBid(
        accountId: Long = 1L,
        recordedState: BidStateTag = BidStateTag.BID_RECORDED,
        won: Long = 1_200_000L,
        kind: BidKind = BidKind.REAL,
    ) {
        val bid = fakeRecordedBid(10L, notice, amount(won), recordedState, kind)
        bids.put(accountId, listOfNotNull(bid))
    }

    private fun seedOpening(
        progressLabel: String? = "개찰완료",
        award: AwardAmount? = null,
    ) {
        openingFacts.facts =
            mapOf(
                noticeId to
                    NoticeOpeningFacts.Present(
                        deadlineAt = Instant.parse("2026-09-18T00:00:00Z"),
                        canonical =
                            CanonicalOpeningFacts(
                                progressLabel = progressLabel,
                                figures =
                                    OpeningResultFigures(
                                        participantCount = 5,
                                        actualOpeningAt = Instant.parse("2026-09-18T05:00:00Z"),
                                        confirmedAward = award,
                                        provisionalRankOne = ProvisionalRankOne.NotObserved,
                                        observedAt = Instant.parse("2026-09-18T06:00:00Z"),
                                    ),
                            ),
                        openingAxisObserved = false,
                    ),
            )
    }

    private fun declareTradeName(raw: String) {
        profiles.replaceDetails(
            AccountId(1L),
            BusinessDetails(TradeName(raw), null, NaraReadiness.Unanswered),
            Instant.parse("2026-09-19T00:00:00Z"),
        )
    }

    private fun body(): String = session.get(AccountScopedRoutes.BID_RESULTS).body.orEmpty()

    @Test
    fun `계정 없는 인증은 401 이다 — 자격 헤더만으로는 결과에 닿지 못한다`() {
        val headers = HttpHeaders().apply { set("X-Operator-Credential", TEST_CREDENTIAL) }

        val response =
            restTemplate.exchange(
                url(AccountScopedRoutes.BID_RESULTS),
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )

        response.statusCode.value() shouldBe 401
    }

    @Test
    fun `투찰 기록이 없으면 빈 목록과 부재 상태가 나간다`() {
        val body = body()
        body shouldContain "\"sampleSize\":0"
        body shouldContain "ABSENT_NO_RECORDED_BIDS"
        body shouldNotContain "\"reconciledAwardRate\":\"0"
    }

    /**
     * **표지 글자가 전문에 없다** — 상호가 판정에 실제로 닿았음을 같은 요청의 판정 결과로
     * 보인다(양성 대조). 닿지 않았다면 결말이 `LOST` 가 아니라 `UNDECIDABLE` 이었을 것이다.
     */
    @Test
    fun `낙찰자 상호가 응답 전문에 나오지 않는다`() {
        seedBid()
        seedOpening()
        declareTradeName("(주)가상해양기술")
        winnerNames.names = mapOf(noticeId to AwardWinnerTradeName.Published(WINNER_SENTINEL))

        val body = body()
        body shouldNotContain WINNER_SENTINEL
        body shouldContain "\"reconciledOutcome\":\"LOST\""
    }

    @Test
    fun `상호가 같으면 낙찰이고 차이가 함께 나간다`() {
        seedBid(won = 1_200_000L)
        seedOpening(award = AwardAmount(1_000_000L, Currency.KRW, Provenance.DerivedFromOpening))
        declareTradeName("주식회사 가상해양기술")
        winnerNames.names = mapOf(noticeId to AwardWinnerTradeName.Published("(주)가상해양기술"))

        val body = body()
        body shouldContain "\"reconciledOutcome\":\"WON\""
        body shouldContain "\"gapState\":\"COMPUTED\""
        body shouldContain "\"gapWon\":200000"
        body shouldContain "\"reconciledAwardRate\":\"1\""
    }

    /** 확정 낙찰가가 없으면 차이가 **부재**다 — 0 으로 채우지 않는다. */
    @Test
    fun `확정 낙찰가가 없으면 차이가 부재로 나간다`() {
        seedBid()
        seedOpening()
        declareTradeName("주식회사 가상해양기술")
        winnerNames.names = mapOf(noticeId to AwardWinnerTradeName.Published("(주)가상해양기술"))

        val body = body()
        body shouldContain "\"gapState\":\"AWARD_ABSENT\""
        body shouldContain "\"gapWon\":null"
    }

    /**
     * **오늘 production 의 답** — 낙찰자 상호가 적재되지 않아 전건 판정 불가이고, 그래서 대사
     * 분모가 0 이다. 비율 칸은 `null` 이고 상태가 사유를 든다(0% 가 아니다).
     */
    @Test
    fun `낙찰자 상호가 없으면 판정 불가이고 대사 분모가 0 이다`() {
        seedBid()
        seedOpening()
        declareTradeName("주식회사 가상해양기술")

        val body = body()
        body shouldContain "\"reconciledOutcome\":\"UNDECIDABLE\""
        body shouldContain "\"reconciliationBlocker\":\"WINNER_NAME_NOT_PUBLISHED\""
        body shouldContain "\"reconciledState\":\"ABSENT_NO_DECIDED_CASES\""
        body shouldContain "\"reconciledAwardRate\":null"
        body shouldContain "\"reconciliationBlockers\":[\"WINNER_NAME_NOT_PUBLISHED\"]"
    }

    /** 운영자 기록과 대사가 **다른 칸**으로 함께 나가고, 어긋남이 표지로 선다. */
    @Test
    fun `운영자 기록과 대사가 다른 칸으로 함께 나간다`() {
        seedBid(recordedState = BidStateTag.WON)
        seedOpening()
        declareTradeName("주식회사 가상해양기술")
        winnerNames.names = mapOf(noticeId to AwardWinnerTradeName.Published("(주)가상토목건설"))

        val body = body()
        body shouldContain "\"recordedState\":\"WON\""
        body shouldContain "\"reconciledOutcome\":\"LOST\""
        body shouldContain "\"outcomeAgreement\":\"DISAGREE\""
        body shouldContain "\"recordedWonCount\":1"
        body shouldContain "\"reconciledWonCount\":0"
    }

    /** 상호 미설정은 판정 **안 함**이다 — 패찰이 아니다(SET-01 관문이 wire 까지 산다). */
    @Test
    fun `상호 미설정이면 판정하지 않는다`() {
        seedBid()
        seedOpening()
        winnerNames.names = mapOf(noticeId to AwardWinnerTradeName.Published("(주)가상토목건설"))

        val body = body()
        body shouldContain "\"reconciliationBlocker\":\"OPERATOR_NAME_NOT_DECLARED\""
        body shouldNotContain "\"reconciledOutcome\":\"LOST\""
    }

    /** 메모·증빙 칸이 전문에 없다 — 포트 값에 자리가 없으므로 조립이 실을 것이 없다. */
    @Test
    fun `메모와 증빙 칸이 전문에 없다`() {
        seedBid()
        seedOpening()

        val body = body()
        body shouldNotContain "memo"
        body shouldNotContain "evidence"
    }
}
