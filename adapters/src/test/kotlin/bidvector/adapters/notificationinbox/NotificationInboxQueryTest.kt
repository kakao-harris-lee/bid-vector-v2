package bidvector.adapters.notificationinbox

import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxLimits
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
 * M7/7C③ — 목록이 **내는 모양**을 실 DB 에서 잰다: 건별 묶음 계수 · 공고 표시 칸의 합침 ·
 * 정렬과 페이지.
 *
 * `NotificationInboxPersistenceTest` 에서 **책임으로 갈라 나왔다**(크기 회피용 기계적 분할이
 * 아니다 — `sizeGate` 500줄이 계기였지만 경계는 의미가 정한다). 그쪽은 **쓰기와 전이**
 * (중복 억제 · 재기록의 시각 거동 · 읽음 전이 · 계정 경계 · 시계 비의존)를 지고, 이쪽은
 * **읽기 투영**을 진다. 한 파일에 있으면 「중복 억제가 깨졌다」와 「목록 모양이 바뀌었다」가
 * 같은 실패로 보인다(`CleanMigrationTest` 가 축별로 갈린 것과 같은 이유).
 */
internal class NotificationInboxQueryTest : NotificationInboxTestSupport() {
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
        store().markRead(account, NotificationItemKey(NotificationKind.NOTICE_REVISED, grouped), READ_AT)
        inbox().record(record(account, unknownNotice("N-2"), NotificationFact.NoticeRevised))

        val items = store().list(account, page()).items

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
        store().markRead(
            account,
            NotificationItemKey(NotificationKind.BID_DEADLINE_IMMINENT, notice),
            READ_AT,
        )

        val page = store().list(account, page(unreadOnly = true))

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
        store().markRead(
            account,
            NotificationItemKey(NotificationKind.BID_DEADLINE_IMMINENT, notice),
            READ_AT,
        )

        store().list(account, page()).unreadCount shouldBe 1L
        store().list(account, page(unreadOnly = true)).unreadCount shouldBe 1L
        store().list(account, page()).totalCount shouldBe 2L
    }

    // =========================================================================
    // 공고 표시 칸의 합침(D-7C3-1 ⑦)
    // =========================================================================

    @Test
    fun `공고 행이 있으면 제목·상태·마감을 합쳐 온다`() {
        val account = insertAccount("joined")
        val notice = insertNotice("N-JOIN", title = "도로 포장 공사", deadline = DEADLINE)
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))

        val display = store().list(account, page()).items.single().display.shouldNotBeNull()

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

        val item = store().list(account, page()).items.single()

        item.display.shouldBeNull()
        item.notice.number.value shouldBe "N-GONE"
        item.fact shouldBe NotificationFact.NoticeRevised
    }

    @Test
    fun `공고를 파기하면 그 항목의 표시 칸만 사라진다 — 항목 수는 그대로다`() {
        val account = insertAccount("purge")
        val notice = insertNotice("N-PURGE", title = "사라질 공고")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised))
        store().list(account, page()).items.single().display.shouldNotBeNull()

        deleteNotice("N-PURGE")

        val page = store().list(account, page())
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

        val display = store().list(account, page()).items.single().display.shouldNotBeNull()

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
            store().list(account, page()).items.map { "${it.notice.number.value}/${it.fact.kind}" }

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

        val second = store().list(account, NotificationPageRequest(page = 1, size = 2, unreadOnly = false))

        second.items.size shouldBe 2
        second.totalCount shouldBe 5L
        // 최신 역순이라 0..4 중 index 4,3 이 첫 페이지고 2,1 이 둘째다.
        second.items.map { it.notice.number.value } shouldContainExactly listOf("N-2", "N-1")
    }

    @Test
    fun `마지막 페이지 뒤를 물으면 본문은 비고 전체 건수는 남는다`() {
        val account = insertAccount("past-end")
        inbox().record(record(account, unknownNotice("N-1"), NotificationFact.NoticeRevised))

        val page = store().list(account, NotificationPageRequest(page = 9, size = 10, unreadOnly = false))

        page.items.shouldBeEmptyList()
        page.totalCount shouldBe 1L
    }

    @Test
    fun `항목이 없으면 빈 페이지다`() {
        val account = insertAccount("empty")

        val page = store().list(account, page())

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

    /** 소유자 연결로 파기를 모사한다 — 보존 run 이 하는 일과 같은 문장이다. */
    private fun deleteNotice(number: String) =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DELETE FROM notice WHERE notice_number = '$number'")
            }
        }

    private companion object {
        val LATER: Instant = Instant.parse("2026-10-24T00:00:00Z")
        val READ_AT: Instant = Instant.parse("2026-10-23T12:00:00Z")
        val DEADLINE: Instant = Instant.parse("2026-11-01T05:00:00Z")
    }
}
