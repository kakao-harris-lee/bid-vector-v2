package bidvector.workflow.bidding

import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 시간 기반 트리거의 훑기(M7/7C③-b · D-7C3B-8) — **창 판정 전수가 DB 없이 돈다**(어댑터는
 * 「무엇을 읽었는가」만 나른다).
 *
 * 날짜 경계는 **KST 달력일**이다. 그래서 이 파일의 시각은 전부 KST 로 짓는다 — UTC 로 지으면
 * 「내일」이 테스트가 도는 시각에 따라 흔들린다.
 */
class NotificationSweepTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val mine = AccountId(1L)
    private val notice = NoticeKey("R00SWEEP000001", NoticeRound.of("000"))

    /** KST 기준 「오늘 15시」 — 자정 경계에서 멀어 창 판정이 하루 단위로만 움직인다. */
    private val now = kst(2026, 10, 31, 15, 0)

    /** 출하 정책 그대로 — 창 값을 test 가 다시 적으면 정책 표와 갈린다. */
    private val policy = resolvedOrFail(NOTIFICATION_WINDOW_POLICY.resolve(LocalDate.of(2026, 10, 31)))

    private fun kst(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Instant = LocalDate.of(year, month, day).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun case(
        state: BidStateTag = BidStateTag.WATCHING,
        deadlineAt: Instant? = null,
        documentDueAt: Instant? = null,
        openedAt: Instant = now.minusSeconds(86_400),
        noticeRevision: Long? = null,
        noticeUpdatedAt: Instant? = null,
        notice: NoticeKey = this.notice,
    ) = NotificationSweepCase(
        account = mine,
        notice = notice,
        state = state,
        deadlineAt = deadlineAt,
        documentDueAt = documentDueAt,
        openedAt = openedAt,
        noticeRevision = noticeRevision,
        noticeUpdatedAt = noticeUpdatedAt,
    )

    private fun factsOf(case: NotificationSweepCase): List<NotificationFact> =
        sweepNotificationsFor(case, now, policy).map { it.fact }

    private fun resolvedOrFail(resolution: Resolution<NotificationWindowPolicyData>): NotificationWindowPolicyData =
        (resolution as Resolution.Resolved).value

    // --- 창 정책 ---------------------------------------------------------------

    /** 출하 창 셋 — 요구표의 「마감 D-1/D-0 · 서류 기한 D-2」가 이 값이다. */
    @Test
    fun `출하 창은 마감 하루 전과 당일 그리고 서류 기한 이틀 전부터다`() {
        policy.bidDeadlineImminent shouldBe DaysBeforeWindow(1, 1)
        policy.bidDeadlineToday shouldBe DaysBeforeWindow(0, 0)
        policy.documentDeadline shouldBe DaysBeforeWindow(0, 2)
    }

    /**
     * 마감 두 창이 겹치면 **값을 만들 수 없다** — 겹치면 한 건이 같은 날 「마감 임박」과 「마감
     * 당일」 두 항목을 받는다(종류가 둘이라 키도 둘이다).
     */
    @Test
    fun `마감 임박과 마감 당일의 창은 겹칠 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            NotificationWindowPolicyData(
                bidDeadlineImminent = DaysBeforeWindow(0, 1),
                bidDeadlineToday = DaysBeforeWindow(0, 0),
                documentDeadline = DaysBeforeWindow(0, 2),
            )
        }
    }

    @Test
    fun `창의 상한은 세 창 가운데 가장 먼 날이다`() {
        policy.horizonDays shouldBe 2L
    }

    // --- 마감 축 ---------------------------------------------------------------

    @Test
    fun `마감이 내일이면 마감 임박이고 오늘이면 마감 당일이다`() {
        factsOf(case(deadlineAt = kst(2026, 11, 1, 11, 0))) shouldBe
            listOf(NotificationFact.BidDeadlineImminent)
        factsOf(case(deadlineAt = kst(2026, 10, 31, 23, 0))) shouldBe
            listOf(NotificationFact.BidDeadlineToday)
    }

    /** 창 **밖**은 조용하다 — 모레 마감은 아직 알리지 않는다(창이 하루씩이다). */
    @Test
    fun `모레 마감은 아직 알리지 않는다`() {
        factsOf(case(deadlineAt = kst(2026, 11, 2, 11, 0))).shouldBeEmpty()
    }

    /** **자정 직후도 같은 날이다** — 경과 시간이 아니라 달력일로 세기 때문이다. */
    @Test
    fun `내일 00시 10분 마감은 경과 시간이 9시간이어도 마감 임박이다`() {
        factsOf(case(deadlineAt = kst(2026, 11, 1, 0, 10))) shouldBe
            listOf(NotificationFact.BidDeadlineImminent)
    }

    @Test
    fun `이미 지난 마감은 알리지 않는다`() {
        factsOf(case(deadlineAt = now.minusSeconds(60))).shouldBeEmpty()
    }

    @Test
    fun `마감을 모르는 건은 알리지 않는다`() {
        factsOf(case(deadlineAt = null)).shouldBeEmpty()
    }

    /**
     * **투찰 미기록인 건만**이다. 모집단은 손 목록이 아니라 [DEADLINE_JUDGED_STATES] —
     * 전이표에서 시스템의 마감 판정이 열린 출발 상태 집합이다.
     */
    @Test
    fun `마감 알림은 투찰 미기록 상태에서만 선다`() {
        val tomorrow = kst(2026, 11, 1, 11, 0)
        val firing =
            BidStateTag.entries
                .filter { factsOf(case(state = it, deadlineAt = tomorrow)).isNotEmpty() }
                .toSet()

        firing shouldBe DEADLINE_JUDGED_STATES
    }

    @Test
    fun `제출함으로 기록된 건은 마감을 알리지 않는다`() {
        factsOf(
            case(state = BidStateTag.BID_RECORDED, deadlineAt = kst(2026, 11, 1, 11, 0)),
        ).shouldBeEmpty()
    }

    // --- 서류 기한 축 -----------------------------------------------------------

    @Test
    fun `적격심사 중이면 서류 기한 D-2 부터 당일까지 알린다`() {
        val review = BidStateTag.UNDER_QUALIFICATION_REVIEW
        listOf(
            kst(2026, 11, 2, 11, 0),
            kst(2026, 11, 1, 11, 0),
            kst(2026, 10, 31, 23, 0),
        ).forEach { due ->
            factsOf(case(state = review, documentDueAt = due)) shouldBe
                listOf(NotificationFact.DocumentDeadlineImminent)
        }
    }

    @Test
    fun `서류 기한이 사흘 뒤면 아직 알리지 않는다`() {
        factsOf(
            case(state = BidStateTag.UNDER_QUALIFICATION_REVIEW, documentDueAt = kst(2026, 11, 3, 11, 0)),
        ).shouldBeEmpty()
    }

    /** 서류 기한은 **그 상태만** 싣는다 — 다른 상태에 값이 실려 와도 읽지 않는다. */
    @Test
    fun `적격심사 중이 아니면 서류 기한을 알리지 않는다`() {
        factsOf(
            case(state = BidStateTag.BID_RECORDED, documentDueAt = kst(2026, 11, 1, 11, 0)),
        ).shouldBeEmpty()
    }

    // --- 공고 개정 축 -----------------------------------------------------------

    private fun revised(
        revision: Long? = 2L,
        updatedAt: Instant? = now.minusSeconds(3_600),
        openedAt: Instant = now.minusSeconds(86_400),
        deadlineAt: Instant? = kst(2026, 11, 5, 11, 0),
    ) = case(
        deadlineAt = deadlineAt,
        openedAt = openedAt,
        noticeRevision = revision,
        noticeUpdatedAt = updatedAt,
    )

    @Test
    fun `담은 뒤에 개정된 공고는 알린다`() {
        factsOf(revised()) shouldBe listOf(NotificationFact.NoticeRevised)
    }

    /** **사실의 시각은 개정 시각**이다 — 훑은 시각이 아니다. */
    @Test
    fun `공고 개정 알림의 시각은 개정이 일어난 시각이다`() {
        val changedAt = now.minusSeconds(7_200)
        sweepNotificationsFor(revised(updatedAt = changedAt), now, policy).single().occurredAt shouldBe changedAt
    }

    @Test
    fun `첫 적재뿐인 공고는 개정이 아니다`() {
        factsOf(revised(revision = 1L)).shouldBeEmpty()
    }

    @Test
    fun `담기 전의 개정은 알리지 않는다`() {
        factsOf(revised(openedAt = now.minusSeconds(60))).shouldBeEmpty()
    }

    @Test
    fun `공고 행이 없으면 개정을 알 수 없다`() {
        factsOf(revised(revision = null)).shouldBeEmpty()
        factsOf(revised(updatedAt = null)).shouldBeEmpty()
    }

    @Test
    fun `마감이 지난 공고의 개정은 할 일을 바꾸지 않는다`() {
        factsOf(revised(deadlineAt = now.minusSeconds(60))).shouldBeEmpty()
    }

    /** 시간 기반 하나 + 개정 하나는 **함께 선다** — 서로 다른 종류라 항목도 둘이다. */
    @Test
    fun `마감 임박과 공고 개정은 한 건에서 함께 설 수 있다`() {
        factsOf(revised(deadlineAt = kst(2026, 11, 1, 11, 0))) shouldBe
            listOf(NotificationFact.BidDeadlineImminent, NotificationFact.NoticeRevised)
    }

    // --- 키 -----------------------------------------------------------------

    /**
     * 「번호가 서지 않는 건」은 **이 타입 위에서 표현되지 않는다** — `NoticeKey` 가 막는다.
     * 그래서 훑기에 그 갈래도, 그것을 세는 계수도 없다. 방어는 행을 읽는 질의가 진다.
     */
    @Test
    fun `공백 공고번호로는 건의 공고 키 자체가 서지 않는다`() {
        shouldThrow<IllegalArgumentException> { NoticeKey(" ", NoticeRound.of("000")) }
    }

    /** 키는 **정규화된** 공고번호다 — 같은 공고를 다르게 적은 건 둘이 한 항목으로 모인다. */
    @Test
    fun `건의 공고 키는 정규형으로 알림 키가 된다`() {
        val loose = NoticeKey("r00 sweep 1", NoticeRound.of("000"))
        loose.asNotificationNoticeId().number.value shouldBe "R00-SWEEP-1"
    }

    // --- 훑기 한 번 -------------------------------------------------------------

    @Test
    fun `훑기는 계정을 행에서 받고 계수를 보고한다`() {
        val inbox = CountingInbox()
        val cases =
            listOf(
                case(deadlineAt = kst(2026, 11, 1, 11, 0)),
                case(state = BidStateTag.BID_RECORDED, deadlineAt = kst(2026, 11, 1, 11, 0)),
            )

        val report = SweepNotifications({ _, _ -> cases }, inbox, { now }, policy).run()

        report.casesRead shouldBe 2
        report.created shouldBe 1
        report.updated shouldBe 0
        report.failed shouldBe 0
        inbox.records.single().account shouldBe mine
    }

    @Test
    fun `기록 실패는 훑기를 멈추지 않고 계수로 남는다`() {
        val inbox = CountingInbox(NotificationInboxOutcome.Failed)
        val cases = List(2) { case(deadlineAt = kst(2026, 11, 1, 11, 0)) }

        val report = SweepNotifications({ _, _ -> cases }, inbox, { now }, policy).run()

        report.failed shouldBe 2
        report.created shouldBe 0
        inbox.records.size shouldBe 2
    }

    /** 질의 상한은 창의 가장 먼 날 **더하기 하루**다(달력일 창을 순간으로 옮기는 여유). */
    @Test
    fun `훑기가 저장소에 묻는 상한은 창의 끝보다 하루 뒤다`() {
        var asked: Instant? = null
        SweepNotifications(
            { _, horizon ->
                asked = horizon
                emptyList()
            },
            CountingInbox(),
            { now },
            policy,
        ).run()

        asked shouldBe now.plusSeconds(3 * 86_400L)
    }

    private class CountingInbox(
        private val outcome: NotificationInboxOutcome = NotificationInboxOutcome.Created,
    ) : NotificationInboxPort {
        val records = mutableListOf<NotificationInboxRecord>()

        override fun record(record: NotificationInboxRecord): NotificationInboxOutcome {
            records += record
            return outcome
        }
    }
}
