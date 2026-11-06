package bidvector.adapters.notificationinbox

import bidvector.workflow.notificationinbox.AwardResult
import bidvector.workflow.notificationinbox.NotificationBidKind
import bidvector.workflow.notificationinbox.NotificationDetail
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationKind
import bidvector.workflow.notificationinbox.NotificationPageRequest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M9/paper-bid — **투찰 종류 축의 거동**을 실 DB 에서 잰다(D-M9P-1~D-M9P-3).
 *
 * `NotificationInboxPersistenceTest` 에서 **책임으로 갈라 나왔다**(크기 회피용 기계적 분할이
 * 아니다 — `sizeGate` 500줄이 계기였지만 경계는 의미가 정한다. 7C③ 이 같은 자리에서
 * `NotificationInboxQueryTest` 를 가른 것과 같은 선례다). 그쪽은 **쓰기와 전이 일반**을 지고,
 * 이쪽은 **키에 축이 하나 늘어서 생긴 성질**을 진다: 같은 공고·같은 알림 종류의 두 연습이 두
 * 항목이고, 읽음 처리가 축을 가르고, 건이 아닌 원천이 자기 묶음에 선다.
 *
 * 한 파일에 있으면 「중복 억제가 깨졌다」와 「서면과 실투찰이 섞였다」가 같은 실패로 보인다 —
 * 이 slice 가 고치는 것이 후자이므로 그 신호가 따로 서야 한다.
 */
internal class NotificationInboxBidKindTest : NotificationInboxTestSupport() {
    /**
     * **이 slice 의 acceptance 다.** 같은 계정 · 같은 공고 · **같은 알림 종류**인데 투찰 종류만
     * 다른 두 기록이 **두 항목**이 되고 서로를 덮지 않는다.
     *
     * 앞 판에서는 키에 그 축이 없어 둘째가 첫째를 **갱신**했고(행 하나), 그래서 원천 양쪽의
     * 가드가 서면 건을 아예 버렸다. 「덮지 않는다」를 행 수만으로 재면 공허하다 — 각 행이
     * **자기 detail** 을 그대로 들고 있는지까지 본다(갱신이 섞이면 둘 다 마지막 값이 된다).
     *
     * 음성 대조가 같은 등식 안에 있다: **같은 투찰 종류**의 재기록은 여전히 갱신이고 행이 늘지
     * 않는다(모든 기록을 새 행으로 만드는 꼴도 위 단언만으로는 초록이다).
     */
    @Test
    fun `같은 공고 같은 종류라도 투찰 종류가 다르면 두 항목이다 — 덮어쓰기 0`() {
        val account = insertAccount("bid-kinds")
        val notice = unknownNotice("N-1")

        inbox().record(
            record(account, notice, NotificationFact.AwardDecided(AwardResult.AWARDED), bidKind = REAL),
        ) shouldBe NotificationInboxOutcome.Created
        inbox().record(
            record(account, notice, NotificationFact.AwardDecided(AwardResult.NOT_AWARDED), bidKind = PAPER),
        ) shouldBe NotificationInboxOutcome.Created
        // 음성 대조 — 같은 축의 재기록은 갱신이다(행이 늘지 않는다).
        inbox().record(
            record(account, notice, NotificationFact.AwardDecided(AwardResult.AWARDED), bidKind = PAPER),
        ) shouldBe NotificationInboxOutcome.Updated

        rowCount() shouldBe 2L
        rawRow(account, "AWARD_DECIDED", "N-1", bidKind = REAL).detailCode shouldBe
            NotificationDetail.AWARDED.name
        rawRow(account, "AWARD_DECIDED", "N-1", bidKind = PAPER).detailCode shouldBe
            NotificationDetail.AWARDED.name
    }

    /**
     * **단건 읽음 처리가 축을 가른다.** 같은 공고 · 같은 알림 종류의 두 항목 가운데 **하나만**
     * 읽힌다 — 축이 술어에서 빠지면 영향 행이 둘이 되어 보지 않은 소식이 읽음으로 넘어간다.
     */
    @Test
    fun `단건 읽음 처리는 그 투찰 종류의 항목만 바꾼다`() {
        val account = insertAccount("read-one-kind")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, bidKind = REAL))
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, bidKind = PAPER))

        val outcome =
            store().markRead(account, itemKey(NotificationKind.NOTICE_REVISED, notice, PAPER), READ_AT)

        outcome.markedCount shouldBe 1
        rawRow(account, "NOTICE_REVISED", "N-1", bidKind = PAPER).readAt shouldBe READ_AT
        rawRow(account, "NOTICE_REVISED", "N-1", bidKind = REAL).readAt.shouldBeNull()
    }

    /**
     * **묶음 읽음 처리도 축을 가른다** — 가장 날카로운 자리다. 공고 쌍만으로 읽으면 서면 묶음을
     * 읽는 한 번의 요청이 같은 공고의 **실투찰 소식 전부**를 읽음으로 바꾼다(이 slice 가 막으려는
     * 일이 읽음 축에서 그대로 되살아난다).
     *
     * 계수도 함께 본다: `markedCount` 가 그 묶음의 안읽음이었던 수여야 하고, 처리 뒤 계정
     * 안읽음 전수는 **다른 축의 둘**이 남는다.
     */
    @Test
    fun `묶음 읽음 처리는 그 투찰 종류의 묶음만 바꾼다`() {
        val account = insertAccount("read-group-kind")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.BidDeadlineImminent, bidKind = PAPER))
        inbox().record(record(account, notice, NotificationFact.BidDeadlineToday, bidKind = PAPER))
        inbox().record(record(account, notice, NotificationFact.BidDeadlineImminent, bidKind = REAL))
        inbox().record(record(account, notice, NotificationFact.BidDeadlineToday, bidKind = REAL))

        val outcome = store().markNoticeRead(account, groupKey(notice, PAPER), READ_AT)

        outcome.markedCount shouldBe 2
        outcome.unreadCount shouldBe 2L
        rawRow(account, "BID_DEADLINE_IMMINENT", "N-1", bidKind = REAL).readAt.shouldBeNull()
        rawRow(account, "BID_DEADLINE_TODAY", "N-1", bidKind = REAL).readAt.shouldBeNull()
    }

    /**
     * **공고 축 종류는 투찰 종류를 갖지 않는다** — 투찰가 요청 기록은 건이 아니라 공고에 붙는다.
     * 그 행이 `NONE` 으로 서고, 같은 공고의 건 축 항목과 **다른 묶음**이다(계수가 섞이지 않는다).
     */
    @Test
    fun `공고 축 알림은 NONE 묶음에 서고 건 축 항목과 섞이지 않는다`() {
        val account = insertAccount("notice-axis")
        val notice = unknownNotice("N-1")
        inbox().record(record(account, notice, NotificationFact.BidPriceRequestRecorded))
        inbox().record(record(account, notice, NotificationFact.NoticeRevised, bidKind = REAL))

        rowCount() shouldBe 2L
        val noticeAxisRow =
            rawRow(account, "BID_PRICE_REQUEST_RECORDED", "N-1", bidKind = NotificationBidKind.NONE)
        noticeAxisRow.detailCode.shouldBeNull()

        val items = store().list(account, NotificationPageRequest(page = 0, size = 10, unreadOnly = false)).items
        items.map { it.group.bidKind } shouldContainExactly
            listOf(NotificationBidKind.NONE, REAL)
        items.forEach { it.groupCounts.itemCount shouldBe 1 }
    }

    private companion object {
        /** 축 어휘 둘의 짧은 이름 — 이 class 의 단언이 한 줄에 서게 한다. */
        val REAL = NotificationBidKind.REAL
        val PAPER = NotificationBidKind.PAPER
    }
}
