package bidvector.adapters.bidding

import bidvector.adapters.notificationinbox.JdbcNotificationInbox
import bidvector.adapters.notificationinbox.JdbcNotificationInboxStore
import bidvector.adapters.notificationinbox.JdbcNotificationSweepCandidates
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.NOTIFICATION_WINDOW_POLICY
import bidvector.workflow.bidding.NotificationSweepCase
import bidvector.workflow.bidding.NotificationWindowPolicyData
import bidvector.workflow.bidding.SweepNotifications
import bidvector.workflow.notificationinbox.NotificationInboxItem
import bidvector.workflow.notificationinbox.NotificationKind
import bidvector.workflow.notificationinbox.NotificationPageRequest
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 훑기 후보 질의의 실 DB 축(M7/7C③-b · D-7C3B-8) — 창 판정은 순수 test 가 재고, 여기서 재는 것은
 * **질의가 무엇을 읽어 오는가**다: 전 계정 · 실투찰만 · 유효 마감의 정의 · 공고 개정 축.
 *
 * `bidvector.adapters.bidding` 에 두는 이유는 모집단이 `bid_case` 이고 [BidCaseDbFixture] 의 출하
 * 조립(건 저장소 · 마감 질의 · use case)을 그대로 쓰기 때문이다 — 어댑터 클래스의 패키지가 아니라
 * **읽는 표**를 따라간다.
 */
internal class NotificationSweepCandidatesTest : BidCaseDbFixture() {
    private val policy: NotificationWindowPolicyData =
        (NOTIFICATION_WINDOW_POLICY.resolve(LocalDate.ofInstant(now, ZoneOffset.UTC)) as Resolution.Resolved).value

    /** 마감이 KST 로 내일인 시각 — 출하 창(D-1)의 한가운데다. */
    private val tomorrow: Instant = Instant.parse("2026-10-22T05:00:00Z")
    private val farFuture: Instant = Instant.parse("2026-12-01T05:00:00Z")

    private fun candidates(horizon: Instant = now.plus(Duration.ofDays(policy.horizonDays + 1))) =
        JdbcNotificationSweepCandidates(dataSource()).candidates(now, horizon)

    /** 건 하나를 담는다 — 공고를 먼저 심어 유효 마감이 **공고 행의 값**이 되게 한다. */
    private fun openCase(
        account: AccountId,
        key: NoticeKey,
        deadline: Instant,
        kind: BidKind = BidKind.REAL,
    ): Long {
        seedNotice(key, deadline)
        return workflow().open(account, key, kind).view.case.id.value
    }

    @Test
    fun `훑기는 전 계정을 보고 계정을 행에서 낸다`() {
        val first = account("sweep-one")
        val second = account("sweep-two")
        openCase(first, notice("SWEEP-A"), tomorrow)
        openCase(second, notice("SWEEP-B"), tomorrow)

        // `AccountId` 는 내용 비교를 제공하지 않는다(그 선택의 사유는 그 타입의 KDoc) — 값으로 본다.
        candidates().map { it.account.value } shouldContainExactly listOf(first.value, second.value)
    }

    /**
     * **페이퍼는 모집단 밖이다** — 알림 키에 투찰 종류 축이 없어 같은 공고의 페이퍼 건이
     * 실투찰 건의 항목을 덮어쓴다.
     */
    @Test
    fun `페이퍼 건은 후보가 아니다`() {
        val account = account("sweep-paper")
        openCase(account, notice("SWEEP-P"), tomorrow, kind = BidKind.PAPER)

        candidates().shouldBeEmpty()
    }

    @Test
    fun `창 밖의 마감은 후보가 아니다`() {
        val account = account("sweep-far")
        openCase(account, notice("SWEEP-F"), farFuture)

        candidates().shouldBeEmpty()
    }

    /**
     * 유효 마감의 정의가 **마감 판정과 같다**: 공고 행이 있으면 그 값, 파기돼 행이 없으면 담을 때의
     * 사본. 둘이 갈리면 마감 판정과 마감 알림이 서로 다른 마감을 본다.
     */
    @Test
    fun `유효 마감은 공고 행의 값이고 파기 뒤에는 담을 때의 사본이다`() {
        val account = account("sweep-eff")
        val key = notice("SWEEP-E")
        openCase(account, key, tomorrow)

        candidates().single().deadlineAt shouldBe tomorrow

        // 공고가 정정되면 **라이브 값**이 따라 움직인다 — 마감이 창 밖으로 나갔는데도 후보로
        // 남는 것은 **개정 축**이 집기 때문이고, 그 건이 보는 마감은 이제 공고 행의 새 값이다.
        correctNoticeDeadline(key, farFuture)
        candidates().single().deadlineAt shouldBe farFuture

        // 공고가 파기되면 담을 때의 **사본**이 남는다(건은 FK 가 없어 그대로 있다). 개정 축은
        // 공고 행과 함께 사라지므로 이 건은 다시 마감 축으로만 후보가 된다.
        purgeNotice(key)
        val afterPurge = candidates().single()
        afterPurge.deadlineAt shouldBe tomorrow
        afterPurge.noticeRevision shouldBe null
        afterPurge.noticeUpdatedAt shouldBe null
    }

    @Test
    fun `적격심사 건은 서류 기한으로 후보가 된다`() {
        val account = account("sweep-doc")
        val key = notice("SWEEP-D")
        val id = openCase(account, key, farFuture)
        adminExecute(
            "UPDATE bid_case SET state = 'UNDER_QUALIFICATION_REVIEW', document_due_at = '$tomorrow' " +
                "WHERE id = $id",
        )

        val found = candidates().single()
        found.state shouldBe BidStateTag.UNDER_QUALIFICATION_REVIEW
        found.documentDueAt shouldBe tomorrow
    }

    /** 개정 축은 **V2 트리거가 올린 값**이다 — test 가 숫자를 심지 않는다. */
    @Test
    fun `담은 뒤 정정된 공고는 개정 번호와 개정 시각을 싣고 온다`() {
        val account = account("sweep-rev")
        val key = notice("SWEEP-R")
        openCase(account, key, farFuture)
        candidates().shouldBeEmpty()

        correctNoticeDeadline(key, farFuture.plusSeconds(3_600))

        val found = candidates().single()
        found.noticeRevision shouldBe 2L
        requireNotNull(found.noticeUpdatedAt)
        found.noticeUpdatedAt!!.isAfter(found.openedAt) shouldBe true
    }

    // --- 출하 조립 전체 ---------------------------------------------------------

    /**
     * 질의 → 순수 판정 → 알림함 쓰기 → 읽기까지 **출하 어댑터만으로** 한 번(D-7C3B-8). 이 test 가
     * 초록이면 러너가 하는 일이 실 DB 위에서 선다는 뜻이다 — 창 판정의 전수는 순수 test 가 진다.
     */
    @Test
    fun `훑기 한 번이 마감 임박 항목을 실 DB 알림함에 세운다`() {
        val account = account("sweep-e2e")
        openCase(account, notice("SWEEP-X"), tomorrow)

        val sweep =
            SweepNotifications(
                JdbcNotificationSweepCandidates(dataSource()),
                JdbcNotificationInbox(dataSource()),
                { now },
                policy,
            )
        val first = sweep.run()

        first.casesRead shouldBe 1
        first.created shouldBe 1
        first.failed shouldBe 0
        itemsOf(account).map { it.fact.kind } shouldContainExactly listOf(NotificationKind.BID_DEADLINE_IMMINENT)

        // **멱등하다** — 두 번째 실행은 같은 키를 갱신할 뿐 항목을 늘리지 않는다(NOTI-10 의 PK).
        val second = sweep.run()
        second.created shouldBe 0
        second.updated shouldBe 1
        itemsOf(account).size shouldBe 1
    }

    private fun itemsOf(account: AccountId): List<NotificationInboxItem> =
        JdbcNotificationInboxStore(dataSource())
            .list(account, NotificationPageRequest(page = 0, size = 20, unreadOnly = false))
            .items
}
