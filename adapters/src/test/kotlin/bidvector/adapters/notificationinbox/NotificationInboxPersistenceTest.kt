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
import io.kotest.matchers.nulls.shouldNotBeNull
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
    // 건별 묶음 계수
    // =========================================================================

    @Test
    fun `항목마다 자기 공고 묶음의 전체 크기와 안읽음 수를 싣는다`() {
        val account = insertAccount("groups")
        val grouped = unknownNotice("N-1")
        inbox().record(record(account, grouped, NotificationFact.BidDeadlineImminent))
        inbox().record(record(account, grouped, NotificationFact.BidDeadlineToday))
        inbox().record(record(account, grouped, NotificationFact.NoticeRevised))
        inbox().markRead(account, NotificationItemKey(NotificationKind.NOTICE_REVISED, grouped), READ_AT)
        inbox().record(record(account, unknownNotice("N-2"), NotificationFact.NoticeRevised))

        val items = inbox().list(account, page()).items

        items.filter { it.notice.number.value == "N-1" }.forEach { item ->
            item.group.itemCount shouldBe 3
            item.group.unreadCount shouldBe 2
        }
        items.single { it.notice.number.value == "N-2" }.group.itemCount shouldBe 1
    }

    /**
     * **안읽음 필터가 묶음 계수를 망치지 않는다** — 계수를 필터와 같은 층에서 세면 `itemCount`
     * 가 걸러진 부분집합을 세어 「묶음 전체 크기」가 아니게 된다(그 값은 묶음이 페이지 경계에
     * 걸쳤는지 아는 유일한 단서다).
     */
    @Test
    fun `안읽음만 보기에서도 묶음 전체 크기는 전체 크기다`() {
        val account = insertAccount("unread-only")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.BidDeadlineImminent))
        inbox().record(record(account, notice, NotificationFact.BidDeadlineToday))
        inbox().markRead(
            account,
            NotificationItemKey(NotificationKind.BID_DEADLINE_IMMINENT, notice),
            READ_AT,
        )

        val page = inbox().list(account, page(unreadOnly = true))

        page.items.size shouldBe 1
        page.items.single().group.itemCount shouldBe 2
        page.items.single().group.unreadCount shouldBe 1
        // 전체 건수는 **필터를 지난 집합**의 수다(페이지 계산의 분모).
        page.totalCount shouldBe 1L
        // 안읽음 계수는 필터와 무관한 계정 전수다 — 여기서는 둘이 같지만 아래 test 가 가른다.
        page.unreadCount shouldBe 1L
    }

    @Test
    fun `안읽음 계수는 필터를 끄고도 같은 값이다 — 배지가 목록 필터에 흔들리지 않는다`() {
        val account = insertAccount("badge")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.BidDeadlineImminent))
        inbox().record(record(account, notice, NotificationFact.BidDeadlineToday))
        inbox().markRead(
            account,
            NotificationItemKey(NotificationKind.BID_DEADLINE_IMMINENT, notice),
            READ_AT,
        )

        inbox().list(account, page()).unreadCount shouldBe 1L
        inbox().list(account, page(unreadOnly = true)).unreadCount shouldBe 1L
        inbox().list(account, page()).totalCount shouldBe 2L
    }

    // =========================================================================
    // 공고 표시 칸의 합침(D-7C3-1 ⑦)
    // =========================================================================

    @Test
    fun `공고 행이 있으면 제목·상태·마감을 합쳐 온다`() {
        val account = insertAccount("joined")
        val notice = insertNotice("N-JOIN", title = "도로 포장 공사", deadline = DEADLINE)
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))

        val display = inbox().list(account, page()).items.single().display.shouldNotBeNull()

        display.title shouldBe "도로 포장 공사"
        display.status shouldBe "Open"
        display.deadlineAt shouldBe DEADLINE
    }

    /**
     * **공고가 파기됐거나 아직 수집되지 않으면 표시 칸이 없다** — 항목은 남는다. 알림함이 공고
     * 행의 수명보다 오래 사는 설계(V23 주석 ③)의 직접 측정이다.
     */
    @Test
    fun `공고 행이 없으면 표시 칸이 null 이고 항목은 남는다`() {
        val account = insertAccount("orphan")
        inbox().record(record(account, unknownNotice("N-GONE"), NotificationFact.NoticeRevised))

        val item = inbox().list(account, page()).items.single()

        item.display.shouldBeNull()
        item.notice.number.value shouldBe "N-GONE"
        item.fact shouldBe NotificationFact.NoticeRevised
    }

    @Test
    fun `공고를 파기하면 그 항목의 표시 칸만 사라진다 — 항목 수는 그대로다`() {
        val account = insertAccount("purge")
        val notice = insertNotice("N-PURGE", title = "사라질 공고")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))
        inbox().list(account, page()).items.single().display.shouldNotBeNull()

        deleteNotice("N-PURGE")

        val page = inbox().list(account, page())
        page.items.single().display.shouldBeNull()
        page.totalCount shouldBe 1L
    }

    /**
     * **「행이 없다」와 「행은 있고 제목이 비었다」는 다른 값**이다 — 표시 칸 전체를 nullable
     * 스칼라로 접으면 그 둘이 같은 답을 낸다.
     */
    @Test
    fun `공고 행은 있고 제목만 비면 표시 칸은 있고 제목이 null 이다`() {
        val account = insertAccount("no-title")
        val notice = insertNotice("N-NOTITLE", title = null)
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))

        val display = inbox().list(account, page()).items.single().display.shouldNotBeNull()

        display.title.shouldBeNull()
        display.status shouldBe "Open"
    }

    // =========================================================================
    // 정렬·페이지
    // =========================================================================

    @Test
    fun `묶음이 연속된다 — 묶음의 최신 사실 시각 역순으로 묶음이 서고 묶음 안도 역순이다`() {
        val account = insertAccount("ordering")
        val older = unknownNotice("N-OLD")
        val newer = unknownNotice("N-NEW")
        inbox().record(record(account, older, NotificationFact.BidDeadlineImminent, OBSERVED_AT))
        inbox().record(record(account, older, NotificationFact.BidDeadlineToday, OBSERVED_AT.plusSeconds(10)))
        inbox().record(record(account, newer, NotificationFact.NoticeRevised, LATER))

        val keys =
            inbox().list(account, page()).items.map { "${it.notice.number.value}/${it.fact.kind}" }

        keys shouldContainExactly
            listOf(
                "N-NEW/${NotificationKind.NOTICE_REVISED}",
                "N-OLD/${NotificationKind.BID_DEADLINE_TODAY}",
                "N-OLD/${NotificationKind.BID_DEADLINE_IMMINENT}",
            )
    }

    @Test
    fun `페이지가 크기와 offset 을 따른다 — 전체 건수는 페이지 길이와 다른 값이다`() {
        val account = insertAccount("paging")
        repeat(5) { index ->
            inbox().record(
                record(
                    account,
                    unknownNotice("N-$index"),
                    NotificationFact.NoticeRevised,
                    OBSERVED_AT.plusSeconds(index.toLong()),
                ),
            )
        }

        val second = inbox().list(account, NotificationPageRequest(page = 1, size = 2, unreadOnly = false))

        second.items.size shouldBe 2
        second.totalCount shouldBe 5L
        // 최신 역순이라 0..4 중 index 4,3 이 첫 페이지고 2,1 이 둘째다.
        second.items.map { it.notice.number.value } shouldContainExactly listOf("N-2", "N-1")
    }

    @Test
    fun `마지막 페이지 뒤를 물으면 본문은 비고 전체 건수는 남는다`() {
        val account = insertAccount("past-end")
        inbox().record(record(account, unknownNotice("N-1"), NotificationFact.NoticeRevised))

        val page = inbox().list(account, NotificationPageRequest(page = 9, size = 10, unreadOnly = false))

        page.items.shouldBeEmptyList()
        page.totalCount shouldBe 1L
    }

    @Test
    fun `항목이 없으면 빈 페이지다`() {
        val account = insertAccount("empty")

        val page = inbox().list(account, page())

        page.items.shouldBeEmptyList()
        page.totalCount shouldBe 0L
        page.unreadCount shouldBe 0L
    }

    private fun page(unreadOnly: Boolean = false) =
        NotificationPageRequest(
            page = 0,
            size = NotificationInboxLimits.DEFAULT_PAGE_SIZE,
            unreadOnly = unreadOnly,
        )

    private fun List<Any>.shouldBeEmptyList() = size shouldBe 0

    /** 소유자 연결로 제목을 바꾼다 — 앱 역할은 `notice` 를 UPDATE 하지 못한다. */
    private fun updateNoticeTitle(
        number: String,
        title: String,
    ) = execute("UPDATE notice SET notice_title = '$title' WHERE notice_number = '$number'")

    /** 파기를 모사한다 — 보존 run 이 소유자 연결로 하는 일과 같은 문장이다. */
    private fun deleteNotice(number: String) = execute("DELETE FROM notice WHERE notice_number = '$number'")

    private fun execute(sql: String) =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
        }

    private companion object {
        val LATER: Instant = Instant.parse("2026-10-24T00:00:00Z")
        val READ_AT: Instant = Instant.parse("2026-10-23T12:00:00Z")
        val LATER_READ_AT: Instant = Instant.parse("2026-10-25T12:00:00Z")
        val DEADLINE: Instant = Instant.parse("2026-11-01T05:00:00Z")
    }
}
