package bidvector.app.http

import bidvector.bidding.SkipReason
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 입찰함 operation 의 **HTTP 거동**(계약 D-7B-3 이 바꾼 것들) — 세션으로 실제로 부른다.
 *
 * - 담기는 새로 담았으면 201, 이미 담긴 건이면 200 이고 같은 건이다(검토 C-M11).
 * - 담은 직후 타임라인에 **생성 전이 한 줄**이 있다 — 이중체가 실 어댑터와 같은 모양이다(검토 C-M7).
 * - `resumeBidCase` 는 **본문 없이** 부른다(결정 ④ — 문서가 정본, 앞 판은 400).
 * - 초안 요청은 `snapshotTakenAt` 을 받지 않는다(결정 ③ — 알려진 키 밖은 400).
 * - 건너뜀 사유가 상한을 넘으면 400 이다(500 이 아니다).
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
class BidCaseEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    private lateinit var session: SessionTestClient

    @BeforeEach
    fun signIn() {
        access.reset()
        session = SessionTestClient(restTemplate, ::url).signIn()
    }

    @Test
    fun `새로 담으면 201 이고 다시 담으면 같은 건으로 200 이다`() {
        val first = session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-1"))
        val second = session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-1"))

        first.statusCode.value() shouldBe CREATED
        second.statusCode.value() shouldBe OK
        idOf(second.body.orEmpty()) shouldBe idOf(first.body.orEmpty())
    }

    @Test
    fun `담은 직후 타임라인에는 생성 전이 한 줄이 있다`() {
        val id = idOf(session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-2")).body.orEmpty())

        val timeline = session.get("${AccountScopedRoutes.BID_CASES}/$id/timeline")

        timeline.statusCode.value() shouldBe OK
        COMMAND.findAll(timeline.body.orEmpty()).map { it.groupValues[1] }.toList() shouldBe listOf("ADD_TO_BID_BOX")
    }

    @Test
    fun `나중에 해제는 본문 없이 부른다`() {
        val id = idOf(session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-3")).body.orEmpty())
        session.post("${AccountScopedRoutes.BID_CASES}/$id/defer", """{"remindAt":"2099-01-01T00:00:00Z"}""")

        val resumed = session.post("${AccountScopedRoutes.BID_CASES}/$id/resume", null)

        resumed.statusCode.value() shouldBe OK
        resumed.body.orEmpty() shouldContain "\"state\":\"WATCHING\""
    }

    @Test
    fun `초안 요청은 snapshotTakenAt 을 받지 않는다 — 알려진 키 밖은 400`() {
        val id = idOf(session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-4")).body.orEmpty())

        val draft =
            session.post(
                "${AccountScopedRoutes.BID_CASES}/$id/draft",
                """{"amountWon":1000000,"vatTreatment":"INCLUSIVE","presentedAt":"2026-10-21T04:00:00Z",""" +
                    """"snapshotTakenAt":"2026-10-21T04:00:00Z"}""",
            )

        draft.statusCode.value() shouldBe BAD_REQUEST
    }

    @Test
    fun `건너뜀 사유가 상한을 넘으면 400 이다`() {
        val id = idOf(session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-5")).body.orEmpty())
        val tooLong = "r".repeat(SkipReason.MAX_LENGTH + 1)

        val skipped = session.post("${AccountScopedRoutes.BID_CASES}/$id/skip", """{"reason":"$tooLong"}""")

        skipped.statusCode.value() shouldBe BAD_REQUEST
    }

    private fun openBody(noticeNumber: String): String =
        """{"noticeNumber":"$noticeNumber","noticeRound":"000","bidKind":"REAL"}"""

    private fun idOf(body: String): Long =
        requireNotNull(ID.find(body)?.groupValues?.get(1)) { "응답에 caseId 가 없다: $body" }.toLong()

    private companion object {
        const val OK = 200
        const val CREATED = 201
        const val BAD_REQUEST = 400
        val ID = Regex("\"caseId\"\\s*:\\s*(\\d+)")
        val COMMAND = Regex("\"command\"\\s*:\\s*\"([A-Z_]+)\"")
    }
}
