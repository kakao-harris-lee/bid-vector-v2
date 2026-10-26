package bidvector.adapters.notificationinbox

import bidvector.workflow.notificationinbox.NotificationDetail
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxLimits
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationKind
import bidvector.workflow.notificationinbox.NotificationPageRequest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * M7/7C③ — 알림함의 거동을 **실 DB** 에서 잰다(중복 억제·읽음 전이·계정 경계·묶음 계수·
 * 공고 합침). mock 을 쓰지 않는다: 재는 성질 대부분이 PK·CHECK·단일 문장 upsert 같은 **DB 의
 * 성질**이라 fake 저장소 위에서는 아무것도 증명되지 않는다.
 */
internal class NotificationInboxPersistenceTest : NotificationInboxTestSupport() {
    // =========================================================================
    // 중복 억제(NOTI-10) — 계약의 핵심
    // =========================================================================

    @Test
    fun `같은 키를 두 번 기록하면 항목이 하나다 — 둘째는 갱신이다`() {
        val account = insertAccount("dedupe")
        val notice = unknownNotice("N-1")

        inbox().record(record(account, notice, NotificationFact.NoticeRevised)) shouldBe
            NotificationInboxOutcome.Created
        inbox().record(
            record(account, notice, NotificationFact.NoticeRevised, LATER),
        ) shouldBe NotificationInboxOutcome.Updated

        rowCount() shouldBe 1L
    }

    @Test
    fun `재기록은 사실의 시각과 갱신 시각만 움직인다 — 적재 시각과 읽음은 그대로다`() {
        val account = insertAccount("timestamps")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))
        val first = rawRow(account, "NOTICE_REVISED", "N-1")

        inbox().record(record(account, notice, NotificationFact.NoticeRevised, LATER))
        val second = rawRow(account, "NOTICE_REVISED", "N-1")

        // 사실의 시각은 **호출자의 시계**가 정한다.
        second.occurredAt shouldBe LATER
        // **적재 시각은 보존 cutoff 의 기준**이다 — 재기록으로 밀리면 90일 약속이 거짓이 된다.
        second.createdAt shouldBe first.createdAt
    }

    /**
     * **두 시계가 갈라져 있다** — `occurred_at` 은 호출자가, `updated_at`·`created_at` 은 DB 가
     * 찍는다. 갱신 시각이 호출자의 값을 싣게 되면 아래 test(같은 시각 재기록)가 아무것도
     * 재지 못한다.
     */
    @Test
    fun `갱신 시각은 호출자의 시계가 아니라 DB 시계다`() {
        val account = insertAccount("clocks")
        val notice = unknownNotice("N-1")

        inbox().record(record(account, notice, NotificationFact.NoticeRevised, OBSERVED_AT))

        val row = rawRow(account, "NOTICE_REVISED", "N-1")
        row.occurredAt shouldBe OBSERVED_AT
        (row.updatedAt != OBSERVED_AT) shouldBe true
        (row.updatedAt >= row.createdAt) shouldBe true
    }

    /**
     * **같은 시각으로 다시 기록해도 갱신 시각은 움직인다** — 그것이 「재기록 증거」라는 이 열의
     * 일이다. 호출자 시계를 실었던 앞 판에서는 이 경우 아무 열도 움직이지 않아 그 주장이
     * 거짓이었다(이 test 가 그 회귀를 막는다).
     */
    @Test
    fun `같은 사실을 같은 시각으로 재기록해도 갱신 시각은 움직인다`() {
        val account = insertAccount("same-instant")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, OBSERVED_AT))
        val first = rawRow(account, "NOTICE_REVISED", "N-1")

        inbox().record(record(account, notice, NotificationFact.NoticeRevised, OBSERVED_AT)) shouldBe
            NotificationInboxOutcome.Updated
        val second = rawRow(account, "NOTICE_REVISED", "N-1")

        second.occurredAt shouldBe first.occurredAt
        (second.updatedAt >= first.updatedAt) shouldBe true
        second.createdAt shouldBe first.createdAt
        rowCount() shouldBe 1L
    }

    /**
     * **같은 사실의 재기록은 읽음을 되돌리지 않는다**(D-7C3-1 ④) — 되돌리면 운영자가 같은 것을
     * 반복해 읽는다.
     */
    @Test
    fun `detail 이 같은 재기록은 읽음 상태를 유지한다`() {
        val account = insertAccount("reread")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))
        inbox().markRead(account, NotificationItemKey(NotificationKind.NOTICE_REVISED, notice), READ_AT)

        inbox().record(record(account, notice, NotificationFact.NoticeRevised, LATER))

        rawRow(account, "NOTICE_REVISED", "N-1").readAt shouldBe READ_AT
    }

    /** **사실이 바뀌면 다시 봐야 한다** — 잠정 1위였다가 아니게 된 것은 다른 사실이다. */
    @Test
    fun `detail 이 바뀐 재기록은 읽음을 해제한다`() {
        val account = insertAccount("detail-change")
        val notice = unknownNotice("N-1")
        val key = NotificationItemKey(NotificationKind.OPENING_COMPLETED, notice)
        inbox().record(record(account, notice, NotificationFact.OpeningCompleted(provisionallyFirst = true)))
        inbox().markRead(account, key, READ_AT)

        inbox().record(
            record(account, notice, NotificationFact.OpeningCompleted(provisionallyFirst = false), LATER),
        )

        val row = rawRow(account, "OPENING_COMPLETED", "N-1")
        row.readAt.shouldBeNull()
        row.detailCode shouldBe NotificationDetail.NOT_PROVISIONAL_FIRST.name
    }

    /**
     * **제목이 바뀌어도 중복 억제가 흔들리지 않는다**(계약 test 항목) — 이 표에 제목이 없으므로
     * 공고 제목을 **실제로 바꾼 뒤** 재기록해도 키가 같다. 「저장하지 않는다」가 구조라는 증거다.
     */
    @Test
    fun `공고 제목이 바뀐 뒤 재기록해도 항목은 하나다`() {
        val account = insertAccount("title-change")
        val notice = insertNotice("N-TITLE", title = "원래 제목")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))

        updateNoticeTitle("N-TITLE", "정정된 제목")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, LATER)) shouldBe
            NotificationInboxOutcome.Updated

        rowCount() shouldBe 1L
        inbox().list(account, page()).items.single().display?.title shouldBe "정정된 제목"
    }

    @Test
    fun `종류가 다르면 같은 공고에 두 항목이다 — 마감 D-1 과 D-0 이 하나로 수렴하지 않는다`() {
        val account = insertAccount("two-kinds")
        val notice = unknownNotice("N-1")

        inbox().record(record(account, notice, NotificationFact.BidDeadlineImminent)) shouldBe
            NotificationInboxOutcome.Created
        inbox().record(record(account, notice, NotificationFact.BidDeadlineToday)) shouldBe
            NotificationInboxOutcome.Created

        rowCount() shouldBe 2L
    }

    // =========================================================================
    // 계정 경계(R-2) — 인가를 구조로
    // =========================================================================

    @Test
    fun `목록은 자기 계정 항목만 낸다`() {
        val mine = insertAccount("mine")
        val other = insertAccount("other")
        inbox().record(record(mine, unknownNotice("N-MINE"), NotificationFact.NoticeRevised))
        inbox().record(record(other, unknownNotice("N-OTHER"), NotificationFact.NoticeRevised))

        val page = inbox().list(mine, page())

        page.items.map { it.notice.number.value } shouldContainExactly listOf("N-MINE")
        page.totalCount shouldBe 1L
    }

    @Test
    fun `남의 항목은 키를 정확히 알아도 읽음 처리되지 않는다`() {
        val mine = insertAccount("mine")
        val other = insertAccount("other")
        val notice = unknownNotice("N-OTHER")
        inbox().record(record(other, notice, NotificationFact.NoticeRevised))

        val outcome =
            inbox().markRead(mine, NotificationItemKey(NotificationKind.NOTICE_REVISED, notice), READ_AT)

        outcome.markedCount shouldBe 0
        rawRow(other, "NOTICE_REVISED", "N-OTHER").readAt.shouldBeNull()
    }

    @Test
    fun `남의 묶음도 읽음 처리되지 않는다`() {
        val mine = insertAccount("mine")
        val other = insertAccount("other")
        val notice = unknownNotice("N-OTHER")
        inbox().record(record(other, notice, NotificationFact.NoticeRevised))

        inbox().markNoticeRead(mine, notice, READ_AT).markedCount shouldBe 0
        rawRow(other, "NOTICE_REVISED", "N-OTHER").readAt.shouldBeNull()
    }

    @Test
    fun `안읽음 계수는 자기 계정 것만 센다`() {
        val mine = insertAccount("mine")
        val other = insertAccount("other")
        inbox().record(record(mine, unknownNotice("N-1"), NotificationFact.NoticeRevised))
        repeat(3) { index ->
            inbox().record(record(other, unknownNotice("N-O$index"), NotificationFact.NoticeRevised))
        }

        inbox().list(mine, page()).unreadCount shouldBe 1L
    }

    // =========================================================================
    // 읽음 전이
    // =========================================================================

    @Test
    fun `읽음 처리는 그 항목만 바꾸고 안읽음 계수를 함께 낸다`() {
        val account = insertAccount("read-one")
        val first = unknownNotice("N-1")
        inbox().record(record(account, first, NotificationFact.NoticeRevised))
        inbox().record(record(account, unknownNotice("N-2"), NotificationFact.NoticeRevised))

        val outcome =
            inbox().markRead(account, NotificationItemKey(NotificationKind.NOTICE_REVISED, first), READ_AT)

        outcome.markedCount shouldBe 1
        outcome.unreadCount shouldBe 1L
    }

    /** 멱등 — 처음 읽은 시각이 정본이다. */
    @Test
    fun `이미 읽은 항목을 다시 처리하면 아무것도 바뀌지 않는다`() {
        val account = insertAccount("read-twice")
        val notice = unknownNotice("N-1")
        val key = NotificationItemKey(NotificationKind.NOTICE_REVISED, notice)
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))
        inbox().markRead(account, key, READ_AT)

        inbox().markRead(account, key, LATER_READ_AT).markedCount shouldBe 0

        rawRow(account, "NOTICE_REVISED", "N-1").readAt shouldBe READ_AT
    }

    @Test
    fun `묶음 읽음 처리는 그 공고의 안읽음 항목 전부를 바꾼다 — 다른 공고는 그대로다`() {
        val account = insertAccount("read-group")
        val target = unknownNotice("N-1")
        inbox().record(record(account, target, NotificationFact.BidDeadlineImminent))
        inbox().record(record(account, target, NotificationFact.BidDeadlineToday))
        inbox().record(record(account, unknownNotice("N-2"), NotificationFact.NoticeRevised))

        val outcome = inbox().markNoticeRead(account, target, READ_AT)

        outcome.markedCount shouldBe 2
        outcome.unreadCount shouldBe 1L
        rawRow(account, "NOTICE_REVISED", "N-2").readAt.shouldBeNull()
    }

    @Test
    fun `존재하지 않는 항목의 읽음 처리는 영향 행 0 이다 — 예외가 아니다`() {
        val account = insertAccount("missing")

        inbox()
            .markRead(
                account,
                NotificationItemKey(NotificationKind.NOTICE_REVISED, unknownNotice("N-NONE")),
                READ_AT,
            ).markedCount shouldBe 0
    }

    // =========================================================================
    // 시계 비의존 (운영자 결정 D-7C3-3 ② · 회귀 방어)
    // =========================================================================

    /**
     * **이 표의 거동은 벽시계에 기대지 않는다.** 앞 판에는 `read_at >= created_at` CHECK 가
     * 있었고, `created_at` 은 DB 의 `now()` 인데 `read_at` 은 앱 시계가 실어서 **두 시계를
     * 비교**했다. 그래서 고정 과거 시각을 쓰는 test 가 **쓴 날은 통과하고 사흘 뒤 붉어졌다** —
     * 코드가 아니라 달력이 바뀌어서다.
     *
     * 그 제약을 걷어낸 뒤의 성질을 여기서 못박는다: 호출자가 싣는 시각이 **실행 시각보다 한참
     * 과거든 한참 미래든** 기록과 읽음 처리가 모두 성공한다. 이 test 가 붉어지면 어딘가에서
     * 앱 시계와 DB 시계를 다시 묶은 것이다.
     *
     * 변이 ⑬(걷어낸 CHECK 복원)이 이 test 를 **RED 로 만든다** — 그 짝이 없으면 이것은
     * 「늘 통과하는 test」와 구별되지 않는다.
     */
    @Test
    fun `호출자의 시각이 실행 시각보다 멀어도 기록과 읽음 처리가 성공한다 — 시계 비의존`() {
        val account = insertAccount("clock-free")
        val farPast = Instant.parse("2001-01-01T00:00:00Z")
        val farFuture = Instant.parse("2099-12-31T23:59:59Z")

        listOf(farPast to unknownNotice("N-PAST"), farFuture to unknownNotice("N-FUTURE"))
            .forEach { (at, notice) ->
                inbox().record(record(account, notice, NotificationFact.NoticeRevised, at)) shouldBe
                    NotificationInboxOutcome.Created
                // 읽음 시각도 같은 극단으로 — 적재 시각(DB `now()`)보다 과거·미래 둘 다 받는다.
                inbox()
                    .markRead(
                        account,
                        NotificationItemKey(NotificationKind.NOTICE_REVISED, notice),
                        at,
                    ).markedCount shouldBe 1
            }

        rowCount() shouldBe 2L
        inbox().list(account, page()).unreadCount shouldBe 0L
    }

    private fun page(unreadOnly: Boolean = false) =
        NotificationPageRequest(
            page = 0,
            size = NotificationInboxLimits.DEFAULT_PAGE_SIZE,
            unreadOnly = unreadOnly,
        )

    /** 소유자 연결로 제목을 바꾼다 — 앱 역할은 `notice` 를 UPDATE 하지 못한다. */
    private fun updateNoticeTitle(
        number: String,
        title: String,
    ) = execute("UPDATE notice SET notice_title = '$title' WHERE notice_number = '$number'")

    private fun execute(sql: String) =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
        }

    private companion object {
        val LATER: Instant = Instant.parse("2026-10-24T00:00:00Z")
        val READ_AT: Instant = Instant.parse("2026-10-23T12:00:00Z")
        val LATER_READ_AT: Instant = Instant.parse("2026-10-25T12:00:00Z")
    }
}
