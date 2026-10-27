package bidvector.app.http

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.notificationinbox.MarkReadOutcome
import bidvector.workflow.notificationinbox.NoticeDisplay
import bidvector.workflow.notificationinbox.NoticeGroupCounts
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxItem
import bidvector.workflow.notificationinbox.NotificationInboxPage
import bidvector.workflow.notificationinbox.NotificationKind
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

/**
 * `GET /api/notifications` · `POST …/read` · `POST …/read-notice` 의 **HTTP 표면** —
 * r1 검토가 비어 있다고 지적한 자리다(verifier F1·F2 · CR M-2·M-3).
 *
 * **무엇을 재는가**: ⓐ 응답이 실제로 직렬화됐을 때의 **키 집합이 OpenAPI 문서와 같은가**,
 * ⓑ 목록 응답 헤더 둘을 구현이 **정말 싣는가**, ⓒ 본문 판독의 거부가 **400 으로 나오는가**.
 * 질의 거동(묶음 계수·정렬·페이지)의 근거는 여기가 아니라
 * `bidvector.adapters.notificationinbox.NotificationInboxQueryTest`(실 PostgreSQL)다.
 *
 * **왜 반사 열거로는 부족했나**(F1 의 요점): 앞 판은 문서의 키를 `declaredFields` 와 맞댔는데
 * Jackson 은 **필드가 아닌 것도 내보낸다** — getter 전용 속성(`val x get() = …`)이나
 * `@get:JsonProperty` 개명은 필드 목록에 없으면서 wire 에는 실린다. 그래서 등식의 한 끝을
 * **직렬화 결과**로 옮겼다. 여기서 쓰는 mapper 는 출하 조립과 같은 모듈 설정을 집는
 * `findAndAddModules()` 다(응답이 `Instant` 를 싣는다 — 모듈이 없으면 그 칸의 모양이 달라진다).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [HttpTestApplication::class],
    properties = [PROP_NO_MANAGEMENT_SERVER],
)
internal class NotificationInboxEndpointTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var store: FakeNotificationInboxStore

    private lateinit var session: SessionTestClient

    @BeforeEach
    fun signIn() {
        store.page = NotificationInboxPage(items = emptyList(), totalCount = 0L, unreadCount = 0L)
        store.outcome = MarkReadOutcome(markedCount = 0, unreadCount = 0L)
        session = SessionTestClient(restTemplate, ::url).signIn()
    }

    /**
     * ⓐ **wire 의 키 집합 == 문서의 키 집합.** 항목을 하나 꽂아 `NotificationItemResponse.from`
     * 이 실제로 돌게 한다 — 빈 목록이면 이 등식은 측정되지 않는다(F1 이 뚫은 자리).
     */
    @Test
    fun `목록 항목의 wire 키 집합이 문서의 NotificationItem 키 집합과 같다`() {
        store.page = NotificationInboxPage(items = listOf(sampleItem()), totalCount = 1L, unreadCount = 1L)

        val response = session.get(AccountScopedRoutes.NOTIFICATIONS)
        response.statusCode.value() shouldBe 200

        val item = MAPPER.readTree(response.body)[0]
        withClue("문서가 선언한 키와 wire 의 키가 갈리면 생성 타입을 쓰는 화면이 조용히 어긋난다") {
            item.propertyNames().toSet() shouldBe documentedItemKeys()
        }
    }

    /** ⓑ 헤더 둘 — 구현이 `.header(…)` 를 지워도 앞 판 test 는 초록이었다(CR M-3). */
    @Test
    fun `목록 응답이 전체 건수와 안읽음 수 헤더를 싣는다`() {
        store.page = NotificationInboxPage(items = listOf(sampleItem()), totalCount = 7L, unreadCount = 3L)

        val response = session.get(AccountScopedRoutes.NOTIFICATIONS)

        response.headers.getFirst(NOTIFICATION_TOTAL_COUNT_HEADER) shouldBe "7"
        response.headers.getFirst(NOTIFICATION_UNREAD_COUNT_HEADER) shouldBe "3"
    }

    /** 읽음 처리 응답의 키 집합도 같은 축으로 — 두 키뿐이다. */
    @Test
    fun `읽음 처리 응답의 wire 키 집합이 문서와 같다`() {
        store.outcome = MarkReadOutcome(markedCount = 2, unreadCount = 5L)

        val response = session.post(AccountScopedRoutes.NOTIFICATIONS + "/read", itemBody())
        response.statusCode.value() shouldBe 200

        MAPPER
            .readTree(response.body)
            .propertyNames()
            .toSet() shouldBe documentedKeys("MarkNotificationReadResponse")
    }

    /**
     * ⓒ **알려진 키 밖은 400.** 판독에서 `requireKnownInboxKeys` 를 지우는 변이가 앞 판에서
     * 전 suite 초록이었다(F2). 네 꼴을 한자리에서 잠근다 — 미지 키 · 비문자열 · 형식 위반 ·
     * 같은 질의 매개변수의 반복.
     */
    @Test
    fun `읽음 처리 본문의 알려진 키 밖은 400 이다`() {
        val response =
            session.post(
                AccountScopedRoutes.NOTIFICATIONS + "/read",
                """{"kind":"NEW_CANDIDATE","noticeNumber":"N-1","noticeRound":"000","accountId":9}""",
            )

        response.statusCode.value() shouldBe 400
    }

    @Test
    fun `읽음 처리 본문의 kind 가 문자열이 아니면 400 이다`() {
        val response =
            session.post(
                AccountScopedRoutes.NOTIFICATIONS + "/read",
                """{"kind":7,"noticeNumber":"N-1","noticeRound":"000"}""",
            )

        response.statusCode.value() shouldBe 400
    }

    @Test
    fun `읽음 처리 본문의 공고 차수 형식 위반은 400 이다`() {
        val response =
            session.post(
                AccountScopedRoutes.NOTIFICATIONS + "/read",
                """{"kind":"NEW_CANDIDATE","noticeNumber":"N-1","noticeRound":"1"}""",
            )

        response.statusCode.value() shouldBe 400
    }

    @Test
    fun `알 수 없는 kind 토큰은 400 이다`() {
        val response =
            session.post(
                AccountScopedRoutes.NOTIFICATIONS + "/read",
                """{"kind":"NOT_A_KIND","noticeNumber":"N-1","noticeRound":"000"}""",
            )

        response.statusCode.value() shouldBe 400
    }

    @Test
    fun `묶음 읽음 처리 본문에 종류 축이 들어오면 400 이다`() {
        val response =
            session.post(
                AccountScopedRoutes.NOTIFICATIONS + "/read-notice",
                """{"kind":"NEW_CANDIDATE","noticeNumber":"N-1","noticeRound":"000"}""",
            )

        response.statusCode.value() shouldBe 400
    }

    /** 같은 매개변수를 두 번 싣는 질의는 거부된다 — 어느 값이 이겼는지 묻지 않아도 되게. */
    @Test
    fun `목록 질의에 같은 매개변수가 반복되면 400 이다`() {
        val response = session.get(AccountScopedRoutes.NOTIFICATIONS + "?page=1&page=2")

        response.statusCode.value() shouldBe 400
    }

    /** 서버가 정한 상한을 넘기면 거부된다(요청이 상한을 올릴 수 없다). */
    @Test
    fun `목록 질의의 크기가 상한을 넘기면 400 이고 포트는 불리지 않는다`() {
        store.lastRequest = null

        val response = session.get(AccountScopedRoutes.NOTIFICATIONS + "?size=1000")

        response.statusCode.value() shouldBe 400
        withClue("거부된 요청이 포트까지 갔다면 판독이 경계가 아니다") { store.lastRequest shouldBe null }
    }

    private fun sampleItem(): NotificationInboxItem =
        NotificationInboxItem(
            notice = NoticeId(NoticeNumber.of("ntc endpoint 1"), NoticeRound.of("000")),
            fact = NotificationFact.OpeningCompleted(provisionallyFirst = true),
            occurredAt = Instant.parse("2026-10-20T01:00:00Z"),
            readAt = null,
            group = NoticeGroupCounts(itemCount = 2, unreadCount = 1),
            display =
                NoticeDisplay(
                    title = "표시 제목",
                    status = "OPEN",
                    deadlineAt = Instant.parse("2026-11-01T00:00:00Z"),
                ),
        )

    private fun itemBody(): String =
        """{"kind":"${NotificationKind.NEW_CANDIDATE.name}","noticeNumber":"N-1","noticeRound":"000"}"""

    /** 문서 쪽 키는 **공유 로더**로 읽는다(중복 키 거부판 — `OpenApiSpecSupport`). */
    private fun documentedItemKeys(): Set<String> = documentedKeys("NotificationItem")

    private fun documentedKeys(schema: String): Set<String> = loadOpenApiSpec().propertyKeys(schema)

    private companion object {
        /** 응답 본문을 **읽기만** 한다(직렬화는 서버가 했다) — `BidCaseRequestsTest` 와 같은 모양. */
        val MAPPER: JsonMapper = JsonMapper.builder().build()
    }
}
