package bidvector.adapters.notificationinbox

import bidvector.workflow.notificationinbox.NotificationBidKind
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxLimits
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
        store().markRead(account, itemKey(NotificationKind.NOTICE_REVISED, grouped), READ_AT)
        inbox().record(record(account, unknownNotice("N-2"), NotificationFact.NoticeRevised))

        val items = store().list(account, page()).items

        items.filter { it.notice.number.value == "N-1" }.forEach { item ->
            item.groupCounts.itemCount shouldBe 3
            item.groupCounts.unreadCount shouldBe 2
        }
        items.single { it.notice.number.value == "N-2" }.groupCounts.itemCount shouldBe 1
    }

    /**
     * **투찰 종류가 묶음 계수의 축이다**(M9/paper-bid). 같은 공고에 서면 둘 · 실투찰 하나를 심고
     * 각 항목이 **자기 축의** 계수를 싣는지 본다 — 창 파티션에서 그 축이 빠지면 세 항목이 모두
     * `itemCount = 3` 을 싣고, 화면은 두 블록 각각에 「항목 3」을 보이면서 행은 둘·하나만 그린다.
     *
     * 정렬 축은 **따로 잰다**(아래 test) — 이 모양에서는 두 축의 묶음 최신 시각이 달라
     * `group_recent` 가 이미 둘을 갈라 놓으므로, 여기서 연속을 단언해도 `ORDER BY` 의 축을
     * 지우는 변이가 **초록이다**(실측).
     */
    @Test
    fun `묶음 계수와 정렬의 축에 투찰 종류가 든다`() {
        val account = insertAccount("group-bid-kinds")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.BidDeadlineImminent, bidKind = PAPER))
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, LATER, bidKind = PAPER))
        inbox().record(record(account, notice, NotificationFact.BidDeadlineToday, bidKind = REAL))

        val items = store().list(account, page()).items

        items.filter { it.group.bidKind == PAPER }.forEach { it.groupCounts.itemCount shouldBe 2 }
        items.single { it.group.bidKind == REAL }.groupCounts.itemCount shouldBe 1
    }

    /**
     * **정렬이 두 축의 묶음을 연속시킨다**(M9/paper-bid). 화면의 묶음 접기는 연속 접기라, 섞이면
     * 한 묶음이 여럿으로 쪼개지고 서버가 정한 최신순이 화면에서 무너진다.
     *
     * **두 축의 묶음 최신 시각을 같게 둔다** — 그러지 않으면 `group_recent DESC` 가 이미 둘을
     * 갈라 놓아 `ORDER BY` 의 축을 지워도 연속이 유지된다(그 모양에서 변이가 초록인 것을
     * 실측했다). 같으면 다음 축이 사실 시각이 되어 네 항목이 **축을 넘나들며** 선다:
     * `PAPER·REAL·REAL·PAPER`. 축이 들어 있으면 `PAPER·PAPER·REAL·REAL` 이다.
     *
     * 종류를 넷 다르게 두는 것은 **같은 시각의 동점을 없애려는** 것이다 — 동점이 남으면 마지막
     * 축(종류 이름)이 순서를 정하고 그 답이 변이와 무관하게 흔들린다.
     */
    @Test
    fun `정렬이 두 축의 묶음을 연속시킨다 — 묶음 최신 시각이 같을 때도`() {
        val account = insertAccount("group-order")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.BidDeadlineImminent, LATER, PAPER))
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, OBSERVED_AT, PAPER))
        inbox().record(record(account, notice, NotificationFact.BidDeadlineToday, LATER, REAL))
        inbox().record(record(account, notice, NotificationFact.DocumentDeadlineImminent, MIDDLE, REAL))

        val items = store().list(account, page()).items

        items.map { it.group.bidKind } shouldContainExactly listOf(PAPER, PAPER, REAL, REAL)
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
            itemKey(NotificationKind.BID_DEADLINE_IMMINENT, notice),
            READ_AT,
        )

        val page = store().list(account, page(unreadOnly = true))

        page.items.size shouldBe 1
        page.items
            .single()
            .groupCounts.itemCount shouldBe 2
        page.items
            .single()
            .groupCounts.unreadCount shouldBe 1
        // 전체 건수는 **필터를 지난 집합**의 수다(페이지 계산의 분모).
        page.totalCount shouldBe 1L
        // 안읽음 계수는 필터와 무관한 계정 전수다 — 여기서는 둘이 같지만 아래 test 가 가른다.
        page.unreadCount shouldBe 1L
    }

    /**
     * **배지는 투찰 종류를 합쳐 센다**(M9/paper-bid 의 알려진 제한). 계정 안읽음 전수는 한 수이고
     * 서면 연습의 안읽음도 그 수에 **든다** — 축별로 가르지 않는 것이 결정이라, 「서면은 세지
     * 않는다」로 조용히 바뀌는 것을 이 등식이 막는다. 축의 구분은 묶음 계수와 묶음 라벨이 진다.
     */
    @Test
    fun `안읽음 전수는 투찰 종류 둘을 합쳐 센다`() {
        val account = insertAccount("badge-bid-kinds")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, bidKind = REAL))
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, bidKind = PAPER))

        store().list(account, page()).unreadCount shouldBe 2L

        store().markNoticeRead(account, groupKey(notice, PAPER), READ_AT)

        store().list(account, page()).unreadCount shouldBe 1L
    }

    @Test
    fun `안읽음 계수는 필터를 끄고도 같은 값이다 — 배지가 목록 필터에 흔들리지 않는다`() {
        val account = insertAccount("badge")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.BidDeadlineImminent))
        inbox().record(record(account, notice, NotificationFact.BidDeadlineToday))
        store().markRead(
            account,
            itemKey(NotificationKind.BID_DEADLINE_IMMINENT, notice),
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

        val display =
            store()
                .list(account, page())
                .items
                .single()
                .display
                .shouldNotBeNull()

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
        store()
            .list(account, page())
            .items
            .single()
            .display
            .shouldNotBeNull()

        deleteNotice("N-PURGE")

        val page = store().list(account, page())
        page.items
            .single()
            .display
            .shouldBeNull()
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

        val display =
            store()
                .list(account, page())
                .items
                .single()
                .display
                .shouldNotBeNull()

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
        /** 축 어휘 둘의 짧은 이름 — 투찰 종류 test 들이 한 줄에 서게 한다. */
        val REAL = NotificationBidKind.REAL
        val PAPER = NotificationBidKind.PAPER

        val DEADLINE: Instant = Instant.parse("2026-11-01T05:00:00Z")

        /** `OBSERVED_AT` 과 `LATER` **사이**의 사실 시각 — 정렬 test 가 축을 넘나드는 모양을 만든다. */
        val MIDDLE: Instant = Instant.parse("2026-10-23T18:00:00Z")
    }
}
