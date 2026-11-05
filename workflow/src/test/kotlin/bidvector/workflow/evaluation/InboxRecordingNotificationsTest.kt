package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyRevision
import bidvector.workflow.access.AccountId
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.notificationinbox.NotificationAudiencePort
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val RECORDED_AT: Instant = Instant.parse("2026-11-06T02:00:00Z")

private class RecordingInbox(
    private val answer: NotificationInboxOutcome = NotificationInboxOutcome.Created,
) : NotificationInboxPort {
    val records = mutableListOf<NotificationInboxRecord>()

    override fun record(record: NotificationInboxRecord): NotificationInboxOutcome {
        records += record
        return answer
    }
}

private class RecordingChannel(
    private val answer: NotificationRequestOutcome = NotificationRequestOutcome.Requested,
) : NotificationRequestPort {
    val requested = mutableListOf<NotificationRequest>()

    override fun request(notification: NotificationRequest): NotificationRequestOutcome {
        requested += notification
        return answer
    }
}

/** 시계가 **몇 번 읽혔는지**도 센다 — 계정마다 다시 읽으면 같은 사실의 시각이 계정마다 달라진다. */
private class CountingClock(
    private val at: Instant = RECORDED_AT,
) : Clock {
    var reads: Int = 0
        private set

    override fun now(): Instant {
        reads++
        return at
    }
}

private fun requestFor(notice: String = "M9-NOTI-1"): NotificationRequest =
    NotificationRequest(
        noticeId = NoticeId(NoticeNumber(notice), NoticeRound("000")),
        correlationId = CorrelationId("corr-m9-1"),
        verdict = bidNowVerdict(),
        evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.CircuitOpen),
        ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "test-ladder-policy"),
        strategyRevision = StrategyRevision(1),
    )

/**
 * **NOTI-01 — 가치 게이트를 통과한 후보가 알림함에 1건을 남긴다**(M9/ops-code).
 *
 * 7C③ 이 표(V23)와 어휘(`NEW_CANDIDATE`)를 세웠지만 그 값을 **낳는 자리**가 production 에 없었다.
 * 이 자리가 그 생산자이고, 「채널 발송과 무관하다」가 계약이다.
 */
class InboxRecordingNotificationsTest {
    @Test
    fun `가치 게이트를 통과하면 그 공고로 NEW_CANDIDATE 한 건이 남는다`() {
        val inbox = RecordingInbox()
        val recording =
            InboxRecordingNotifications(RecordingChannel(), inbox, { listOf(AccountId(7)) }, CountingClock())

        recording.request(requestFor())

        inbox.records shouldHaveSize 1
        val record = inbox.records.single()
        // `AccountId` 는 값 비교를 제공하지 않는다(`equals` 미재정의) — 칸 값으로 잰다.
        record.account.value shouldBe 7L
        record.notice.number.value shouldBe "M9-NOTI-1"
        record.fact shouldBe NotificationFact.NewCandidate
        record.occurredAt shouldBe RECORDED_AT
    }

    /**
     * **채널 발송과 무관하다**(NOTI-01 「억제는 채널 억제이지 기록 억제가 아니다」) — 채널 요청이
     * 실패해도 기록은 이미 났다. 기록을 채널 뒤에 두면 채널 실패가 기록까지 함께 지운다.
     */
    @Test
    fun `채널 요청이 실패해도 기록은 남고 채널 결말은 그대로 전달된다`() {
        val inbox = RecordingInbox()
        val channel = RecordingChannel(NotificationRequestOutcome.Failed)
        val recording =
            InboxRecordingNotifications(channel, inbox, { listOf(AccountId(7)) }, CountingClock())

        val outcome = recording.request(requestFor())

        inbox.records shouldHaveSize 1
        outcome shouldBe NotificationRequestOutcome.Failed
        channel.requested shouldHaveSize 1
    }

    /** 기록 실패가 채널 요청을 막지 않는다 — 두 축은 서로의 전제가 아니다. */
    @Test
    fun `기록이 실패해도 채널 요청은 그대로 나가고 그 결말이 답이다`() {
        val inbox = RecordingInbox(NotificationInboxOutcome.Failed)
        val channel = RecordingChannel()
        val recording =
            InboxRecordingNotifications(channel, inbox, { listOf(AccountId(7)) }, CountingClock())

        val outcome = recording.request(requestFor())

        channel.requested shouldHaveSize 1
        outcome shouldBe NotificationRequestOutcome.Requested
    }

    @Test
    fun `계정이 여럿이면 각 알림함에 한 건씩이고 사실의 시각은 하나다`() {
        val inbox = RecordingInbox()
        val clock = CountingClock()
        val audience = NotificationAudiencePort { listOf(AccountId(1), AccountId(2)) }
        val recording = InboxRecordingNotifications(RecordingChannel(), inbox, audience, clock)

        recording.request(requestFor())

        inbox.records.map { it.account.value } shouldContainExactly listOf(1L, 2L)
        inbox.records.map { it.occurredAt }.toSet() shouldHaveSize 1
        clock.reads shouldBe 1
    }

    /** 계정이 없으면(부트스트랩 전) 기록 0 건이다 — 실패가 아니고 채널 경로도 그대로 돈다. */
    @Test
    fun `청중이 비면 기록 0 건이고 채널 요청은 그대로 나간다`() {
        val inbox = RecordingInbox()
        val channel = RecordingChannel()
        val recording = InboxRecordingNotifications(channel, inbox, { emptyList() }, CountingClock())

        recording.request(requestFor())

        inbox.records.shouldBeEmpty()
        channel.requested shouldHaveSize 1
    }
}
