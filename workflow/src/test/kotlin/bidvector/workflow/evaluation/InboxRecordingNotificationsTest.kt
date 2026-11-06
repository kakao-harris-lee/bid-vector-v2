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
import bidvector.workflow.notificationinbox.NotificationAudience
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

/** 청중 조회가 저장 층 장애로 끝난 값 — 예외 **클래스 이름만** 나른다(cr r2 MEDIUM). */
private val AUDIENCE_UNAVAILABLE = NotificationAudience.Unavailable("SQLException")

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

/**
 * **질의 계수를 세는 청중** — 계정 전수는 후보에 따라 달라지지 않으므로 run 당 한 번이어야 한다
 * (cr r1 low). 「One query, not N」.
 */
private class CountingAudience(
    private val answer: NotificationAudience = NotificationAudience.Accounts(listOf(AccountId(7))),
) : NotificationAudiencePort {
    var calls: Int = 0
        private set

    override fun accounts(): NotificationAudience {
        calls++
        return answer
    }
}

/** 한 계정의 청중 — 거동이 계정 수에 의존하지 않는 판들이 쓴다. */
private fun oneAccount(id: Long = 7): NotificationAudiencePort =
    NotificationAudiencePort { NotificationAudience.Accounts(listOf(AccountId(id))) }

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
            InboxRecordingNotifications(RecordingChannel(), inbox, oneAccount(), CountingClock())

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
            InboxRecordingNotifications(channel, inbox, oneAccount(), CountingClock())

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
            InboxRecordingNotifications(channel, inbox, oneAccount(), CountingClock())

        val outcome = recording.request(requestFor())

        channel.requested shouldHaveSize 1
        outcome shouldBe NotificationRequestOutcome.Requested
    }

    @Test
    fun `계정이 여럿이면 각 알림함에 한 건씩이고 사실의 시각은 하나다`() {
        val inbox = RecordingInbox()
        val clock = CountingClock()
        val audience = CountingAudience(NotificationAudience.Accounts(listOf(AccountId(1), AccountId(2))))
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
        val audience = CountingAudience(NotificationAudience.Accounts(emptyList()))
        val recording = InboxRecordingNotifications(channel, inbox, audience, CountingClock())

        recording.request(requestFor())

        inbox.records.shouldBeEmpty()
        channel.requested shouldHaveSize 1
    }

    /**
     * **청중을 못 읽어도 채널 요청은 간다**(cr r1 MEDIUM · v M-2). 앞 판은 이 자리에서 예외가
     * 올라가 평가 커밋 run 이 멈췄고, 그래서 그 판정의 **outbox 흔적까지** 함께 사라졌다 —
     * 「기록 실패가 채널 요청을 막지 않는다」와 어긋나는 유일한 경로였다. 빈 청중과 **다른 값**
     * 이라는 것도 함께 잰다(둘을 빈 목록으로 접으면 장애가 조용한 0 건이 된다).
     */
    @Test
    fun `청중 조회가 실패를 돌려줘도 기록 0 건에 그치고 채널 요청은 나간다`() {
        val inbox = RecordingInbox()
        val channel = RecordingChannel()
        val audience = CountingAudience(AUDIENCE_UNAVAILABLE)
        val recording = InboxRecordingNotifications(channel, inbox, audience, CountingClock())

        val outcome = recording.request(requestFor())

        inbox.records.shouldBeEmpty()
        channel.requested shouldHaveSize 1
        outcome shouldBe NotificationRequestOutcome.Requested
    }

    /**
     * **기록이 채널보다 먼저다** — 그 순서가 이 클래스의 계약인데 앞 판에서는 「채널 실패에도 기록
     * 1건」·「기록 실패에도 채널 1회」 둘이 그것을 **잠그지 못했다**(두 포트를 뒤집은 변이가 초록으로
     * 남았다 — v L-1 ①). 두 대역이 **호출 시점에** 상대의 상태를 단언한다: 채널은 기록이 이미 있음을,
     * 알림함은 채널이 아직 안 불렸음을 본다. 순서를 뒤집으면 둘이 함께 붉어진다.
     */
    @Test
    fun `알림함 기록이 채널 요청보다 먼저 일어난다 — 양쪽이 호출 시점에 단언한다`() {
        val seen = mutableListOf<String>()
        val inbox =
            NotificationInboxPort {
                seen += "inbox"
                seen shouldContainExactly listOf("inbox")
                NotificationInboxOutcome.Created
            }
        val channel =
            NotificationRequestPort {
                seen += "channel"
                seen shouldContainExactly listOf("inbox", "channel")
                NotificationRequestOutcome.Requested
            }

        InboxRecordingNotifications(channel, inbox, oneAccount(), CountingClock()).request(requestFor())

        seen shouldContainExactly listOf("inbox", "channel")
    }

    /**
     * **장애도 run 당 한 번만 묻는다**(cr r2 low) — `Unavailable` 까지 캐시하는 것은 **고른 쪽**이다:
     * 연결이 고갈된 날 후보마다 다시 물으면 풀의 연결 대기(기본 수십 초)가 후보 수만큼 쌓여 평가 run
     * 자체가 매달린다. 그 대가로 한 번 깜빡인 연결이 그 run 의 기록을 전부 0 건으로 만든다(알려진
     * 제한 ⑨). 요청 둘로 그 선택을 고정한다 — 앞 판에는 요청이 하나뿐이라 「캐시한다」와 「다시
     * 묻는다」가 같은 값이었다.
     */
    @Test
    fun `청중 조회가 실패해도 그 run 에서 다시 묻지 않는다`() {
        val inbox = RecordingInbox()
        val channel = RecordingChannel()
        val audience = CountingAudience(AUDIENCE_UNAVAILABLE)
        val recording = InboxRecordingNotifications(channel, inbox, audience, CountingClock())

        recording.request(requestFor("M9-NOTI-1"))
        recording.request(requestFor("M9-NOTI-2"))

        audience.calls shouldBe 1
        inbox.records.shouldBeEmpty()
        channel.requested shouldHaveSize 2
    }

    /**
     * **청중 질의는 run 당 한 번**이다(cr r1 low) — 계정 전수는 후보에 따라 달라지지 않으므로 후보
     * 마다 같은 질의를 내는 것은 비용만 늘린다(「One query, not N」). 기록은 여전히 후보마다 난다.
     */
    @Test
    fun `후보가 둘이어도 청중 질의는 한 번이고 기록은 둘이다`() {
        val inbox = RecordingInbox()
        val audience = CountingAudience()
        val recording = InboxRecordingNotifications(RecordingChannel(), inbox, audience, CountingClock())

        recording.request(requestFor("M9-NOTI-1"))
        recording.request(requestFor("M9-NOTI-2"))

        audience.calls shouldBe 1
        inbox.records.map { it.notice.number.value } shouldContainExactly listOf("M9-NOTI-1", "M9-NOTI-2")
    }
}
