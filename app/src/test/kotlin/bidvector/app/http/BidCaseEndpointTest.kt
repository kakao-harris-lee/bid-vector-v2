package bidvector.app.http

import bidvector.bidding.SkipReason
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
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

    @Autowired
    private lateinit var deadlines: MovableNoticeDeadlines

    @Autowired
    private lateinit var clock: FixedClock

    private lateinit var session: SessionTestClient

    /** 마감 이중체는 같은 Spring 문맥을 쓰는 다른 test class 와 공유된다 — 바꾼 값을 남기지 않는다. */
    @AfterEach
    fun restoreDeadlines() {
        deadlines.reset()
    }

    @BeforeEach
    fun signIn() {
        access.reset()
        deadlines.reset()
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

    // --- 마감 판정의 운반 층(검토 r2 C2-N6) — 이중체의 마감을 test 가 옮긴다 ---------------------------------

    @Test
    fun `마감이 지난 건에는 건너뜀 · 초안이 409 이다`() {
        val id = idOf(session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-6")).body.orEmpty())
        deadlines.set(clock.now().minusSeconds(1))

        val skipped = session.post("${AccountScopedRoutes.BID_CASES}/$id/skip", """{"reason":"늦었다"}""")
        skipped.statusCode.value() shouldBe CONFLICT
        session
            .post(
                "${AccountScopedRoutes.BID_CASES}/$id/draft",
                """{"amountWon":1000000,"vatTreatment":"INCLUSIVE","presentedAt":"2026-10-21T04:00:00Z"}""",
            ).statusCode
            .value() shouldBe CONFLICT
    }

    /** 결정 ⑦ — GET 이 시스템 판정을 쓴다(사실 선언). 타임라인에 시스템 주체 한 줄이 남는다. */
    @Test
    fun `마감이 지난 건을 GET 으로 읽으면 시스템 판정이 타임라인에 남는다`() {
        val id = idOf(session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-7")).body.orEmpty())
        deadlines.set(clock.now().minusSeconds(1))

        val read = session.get("${AccountScopedRoutes.BID_CASES}/$id").body.orEmpty()
        read shouldContain "\"state\":\"DEADLINE_PASSED_UNRECORDED\""

        commandsOf(session.get("${AccountScopedRoutes.BID_CASES}/$id/timeline").body.orEmpty()) shouldBe
            listOf("ADD_TO_BID_BOX", "MARK_DEADLINE_PASSED")
    }

    /** 결정 ⑤ — 마감이 다시 미래가 되면 다음 GET 이 판정을 거두고 원래 상태로 돌린다. */
    @Test
    fun `마감이 다시 미래가 되면 GET 이 판정을 거둔다`() {
        val id = idOf(session.post(AccountScopedRoutes.BID_CASES, openBody("R7B-ENDPOINT-8")).body.orEmpty())
        deadlines.set(clock.now().minusSeconds(1))
        session.get("${AccountScopedRoutes.BID_CASES}/$id")
        deadlines.set(clock.now().plusSeconds(HOUR))

        session.get("${AccountScopedRoutes.BID_CASES}/$id").body.orEmpty() shouldContain "\"state\":\"WATCHING\""
        commandsOf(session.get("${AccountScopedRoutes.BID_CASES}/$id/timeline").body.orEmpty()) shouldBe
            listOf("ADD_TO_BID_BOX", "MARK_DEADLINE_PASSED", "REVOKE_DEADLINE_PASSED")
    }

    private fun commandsOf(body: String): List<String> = COMMAND.findAll(body).map { it.groupValues[1] }.toList()

    private fun openBody(noticeNumber: String): String =
        """{"noticeNumber":"$noticeNumber","noticeRound":"000","bidKind":"REAL"}"""

    private fun idOf(body: String): Long =
        requireNotNull(ID.find(body)?.groupValues?.get(1)) { "응답에 caseId 가 없다: $body" }.toLong()

    private companion object {
        const val OK = 200
        const val CREATED = 201
        const val BAD_REQUEST = 400
        const val CONFLICT = 409
        const val HOUR = 3_600L
        val ID = Regex("\"caseId\"\\s*:\\s*(\\d+)")
        val COMMAND = Regex("\"command\"\\s*:\\s*\"([A-Z_]+)\"")
    }
}
