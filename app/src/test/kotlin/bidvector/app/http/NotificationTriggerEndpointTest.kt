package bidvector.app.http

import bidvector.workflow.notificationinbox.NotificationKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 트리거가 **표면에서 실제로 발화하는가**(M7/7C③-b) — 두 경로다: 투찰가 요청(7C②)과 결과 전이(7B).
 *
 * 여기서 재는 것은 「요청 하나가 포트를 **어떤 주체·어떤 사실**로 부르는가」다. 중복 억제와 읽기
 * 왕복은 실 DB 축(`adapters`)이 따로 잰다 — 이 조립의 쓰기 이중체는 키만 진다.
 *
 * **계정 주체는 세션에서만 온다** — 자격 헤더 경로의 답(적지 않음)은 `BidPriceRequestEndpointTest` ·
 * `BidPriceRequestE2ETest` 가 잰다. 여기는 그 반대쪽이다.
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
class NotificationTriggerEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @Autowired
    private lateinit var notices: MutableNoticeByIdPort

    @Autowired
    private lateinit var judgements: MutableLicenseJudgementPort

    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    @Autowired
    private lateinit var inbox: InMemoryNotificationInbox

    private lateinit var session: SessionTestClient

    @BeforeEach
    fun signIn() {
        access.reset()
        strategyRepository.strategy = bidPriceStrategy()
        strategyRepository.loadFailure = null
        judgements.judgement = licenseJudgementFixture()
        inbox.records.clear()
        session = SessionTestClient(restTemplate, ::url).signIn()
    }

    private fun requestBidPrice(number: String): String {
        notices.notice = bidPriceNotice(bidPriceNoticeId(number))
        val response =
            session.post(
                "/api/bid-price-requests",
                """{"noticeNumber":"$number","noticeRound":"000","currentActiveBids":0}""",
            )
        response.statusCode.value() shouldBe OK_STATUS
        return response.body.orEmpty()
    }

    /**
     * **세션으로 부르면 적힌다.** 같은 공고를 다시 물으면 항목이 늘지 않고 갱신된다 — 그 사실이
     * 응답 어휘에서 보인다(NOTI-10 중복 억제가 표면에 닿는 자리).
     */
    @Test
    fun `세션으로 부른 투찰가 요청은 알림함에 적히고 다시 부르면 갱신된다`() {
        val first = requestBidPrice("BP-INBOX-1")
        val second = requestBidPrice("BP-INBOX-1")

        first.contains(""""inboxRecordState":"RECORDED_NEW"""") shouldBe true
        second.contains(""""inboxRecordState":"RECORDED_UPDATED"""") shouldBe true
        // 적힌 응답에는 사유 칸이 서지 않는다.
        first.contains("NO_ACCOUNT_SUBJECT") shouldBe false
        second.contains("INBOX_WRITE_FAILED") shouldBe false

        inbox.records.map { it.fact.kind } shouldBe
            listOf(NotificationKind.BID_PRICE_REQUEST_RECORDED, NotificationKind.BID_PRICE_REQUEST_RECORDED)
        inbox.records.map { it.notice.number.value }.toSet() shouldBe setOf("BP-INBOX-1")
    }

    /** 주체는 **세션이 낸 계정**이다 — 본문이나 헤더가 고를 자리가 없다. */
    @Test
    fun `적히는 주체는 모두 같은 세션 계정이다`() {
        requestBidPrice("BP-INBOX-2")

        inbox.records
            .map { it.account.value }
            .toSet()
            .size shouldBe 1
    }

    /**
     * **결과 전이도 같은 알림함에 적힌다**(D-7C3B-7) — 운영자 기록의 되비춤이다. 개찰 결과를
     * 기록하면 그 사실이 한 항목으로 남는다.
     */
    @Test
    fun `개찰 결과를 기록하면 그 사실이 알림함에 적힌다`() {
        val caseId = openAndAwaitOpening("R7C3B-TRIGGER-1")

        val recorded = session.post("${AccountScopedRoutes.BID_CASES}/$caseId/opening", """{"provisionalFirst":true}""")

        recorded.statusCode.value() shouldBe OK_STATUS
        inbox.records.map { it.fact.kind } shouldBe listOf(NotificationKind.OPENING_COMPLETED)
    }

    /** 담기·초안 같은 **앞 단계 전이는 되비추지 않는다** — 알림 어휘에 대응하는 종류가 없다. */
    @Test
    fun `개찰 전 전이는 알림을 남기지 않는다`() {
        openAndAwaitOpening("R7C3B-TRIGGER-2")

        inbox.records.shouldBeEmpty()
    }

    /**
     * 건을 담고 **실제 전이로** 개찰 대기까지 민다(상태를 저장소에 심지 않는다) — 되비춤이 서는
     * 바로 앞 상태다. 이 경로를 지나는 동안 알림이 하나도 서지 않는다는 것이 위 음성 단언이다.
     */
    private fun openAndAwaitOpening(number: String): Long {
        val opened =
            session.post(
                AccountScopedRoutes.BID_CASES,
                """{"noticeNumber":"$number","noticeRound":"000","bidKind":"REAL"}""",
            )
        opened.statusCode.value() shouldBe CREATED_STATUS
        val caseId = requireNotNull(CASE_ID.find(opened.body.orEmpty())) { opened.body.orEmpty() }.groupValues[1]
        step(caseId, "draft", """{"amountWon":1000000,"vatTreatment":"INCLUSIVE","presentedAt":"$MOMENT"}""")
        step(caseId, "record", """{"amountWon":1000000,"vatTreatment":"INCLUSIVE","submittedAt":"$MOMENT"}""")
        step(caseId, "await-opening", """{"openingAt":"$MOMENT"}""")
        return caseId.toLong()
    }

    private fun step(
        caseId: String,
        path: String,
        body: String,
    ) {
        val response = session.post("${AccountScopedRoutes.BID_CASES}/$caseId/$path", body)
        response.statusCode.value() shouldBe OK_STATUS
    }

    private companion object {
        const val OK_STATUS = 200
        const val CREATED_STATUS = 201

        /** 고정 시계의 「지금」 — 하네스의 `FixedClock` 이 이 값을 쓴다. */
        const val MOMENT = "2026-10-21T04:00:00Z"
        val CASE_ID = Regex(""""caseId":(\d+)""")
    }
}
