package bidvector.workflow.notification

import bidvector.workflow.event.ConsumerLeasePort
import bidvector.workflow.event.ConsumerTransactionPort
import bidvector.workflow.event.OutboxConsumerKind
import bidvector.workflow.event.OutboxEntryId
import bidvector.workflow.event.OutboxPort
import bidvector.workflow.event.OutboxTransition
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

private const val RELAY_LIMIT = 10

/**
 * relay 의 어휘 해석표(D-6F10-12)와 순서(D-6F10-3)를 fake port 로 잰다 — mock framework 0.
 *
 * 이 test 가 재는 것은 **처분의 사상**이다: 발송 결과 네 갈래와 중복·미지 payload 가 각각
 * 어느 종단으로 가는가, inbox 는 언제 기록되는가, 억제·Busy 에서 무엇을 **하지 않는가**.
 * 실 DB 의 경합·격리는 `adapters` 쪽 test 가 잰다(여기서는 port 가 fake 라 잴 수 없다).
 */
class RelayOutboxNotificationsTest {
    @Test
    fun `Delivered 는 DELIVERED 로 가고 inbox 는 발송 뒤에 기록된다`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("e1")))
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(delivered())

        val report = relay(outbox, inbox, sender).relay(RELAY_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.claimed shouldBe 1
        report.delivered shouldBe 1
        outbox.delivered shouldBe listOf(OutboxEntryId("e1"))
        outbox.failed.shouldBeEmpty()
        outbox.isolated.shouldBeEmpty()
        inbox.recorded.map { it.value } shouldBe listOf("key-e1")
        sender.requests.map { it.owner } shouldBe listOf(RELAY_OWNER)
    }

    @Test
    fun `Rejected 는 FAILED 로 가고 inbox 행은 생기지 않는다 — 키 소진 방지`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("e2")))
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(DeliveryResult.Rejected(RejectionReason.ProviderDeclined))

        val report = relay(outbox, inbox, sender).relay(RELAY_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.failed shouldBe 1
        report.delivered shouldBe 0
        outbox.failed shouldBe listOf(OutboxEntryId("e2"))
        // 선기록이면 여기 키가 남아 다음 run 이 그 entry 를 「중복」으로 읽는다(발송은 없었는데).
        inbox.recorded.shouldBeEmpty()
    }

    @Test
    fun `Unknown 은 ISOLATED 로 간다 — 모호는 재시도하지 않는다`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("e3")))
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(DeliveryResult.Unknown(RELAY_NOW))

        val report = relay(outbox, inbox, sender).relay(RELAY_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.isolated shouldBe 1
        report.unknownPayload shouldBe 0
        outbox.isolated shouldBe listOf(OutboxEntryId("e3"))
        inbox.recorded.shouldBeEmpty()
    }

    /**
     * D-6F10-12 — 「같은 멱등 키가 이미 전달됨」도 `DELIVERED` 다. 이 entry 의 의무가 이미
     * 이행됐기 때문이고, 그 처분이 없으면 행이 `CLAIMED` 에 좌초한다(6D-1 test relay 실측).
     * sender 는 **부르지 않는다**.
     */
    @Test
    fun `이미 처리된 키는 발송 없이 DELIVERED 로 간다 — SkipDuplicate`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("e4", idempotencyKey = "dup")))
        val inbox = FakeInboxPort(processed = setOf("dup"))
        val sender = ScriptedSender(delivered())

        val report = relay(outbox, inbox, sender).relay(RELAY_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.skippedDuplicates shouldBe 1
        report.delivered shouldBe 0
        outbox.delivered shouldBe listOf(OutboxEntryId("e4"))
        sender.requests.shouldBeEmpty()
        inbox.recorded.shouldBeEmpty()
    }

    /**
     * route 수준 억제(채널 비활성)는 `FAILED` 다 — 이 entry 로는 전달이 일어나지 않았고
     * 일어나지 않을 것이다(설정을 고치면 **다음** 판정이 새 행을 낳는다). 환경 수준 억제와
     * 다른 처분이고, 그 차이가 D-6F10-12 표의 요점이다.
     */
    @Test
    fun `채널이 비활성이면 Suppressed 가 FAILED 로 간다`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("e5")))
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(delivered())

        val report =
            relay(outbox, inbox, sender, routes = SingleRouteDirectory(enabled = false))
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.Completed>()

        report.failed shouldBe 1
        outbox.failed shouldBe listOf(OutboxEntryId("e5"))
        sender.requests.shouldBeEmpty()
    }

    /**
     * 미지 payload 는 격리 + **계수**다 — 조용히 건너뛰면 행이 `CLAIMED` 에 좌초하고 사유가
     * 사라진다. kind 필터(D-6F10-13)가 들어온 뒤 어댑터는 이런 행을 만들 수 없어 이 분기는
     * 심층 방어이고, 그래서 port 경계에서만 잴 수 있다.
     */
    @Test
    fun `payload 가 알림 요청이 아니면 ISOLATED 로 가고 사유가 계수된다`() {
        val outbox = FakeOutboxPort(pending = listOf(foreignPayloadRow("e6")))
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(delivered())

        val report = relay(outbox, inbox, sender).relay(RELAY_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.isolated shouldBe 1
        report.unknownPayload shouldBe 1
        outbox.isolated shouldBe listOf(OutboxEntryId("e6"))
        sender.requests.shouldBeEmpty()
    }

    /**
     * D-6F10-11 — 고아는 **첫 claim 전에** 격리된다. 순서가 뒤집히면 이 run 이 방금 집은
     * 행까지 「고아」로 보여 자기 in-flight 를 태운다.
     */
    @Test
    fun `lease 를 새로 쥔 relay 는 claim 전에 보이는 CLAIMED 를 격리한다`() {
        val outbox =
            FakeOutboxPort(
                pending = listOf(notificationRow("fresh")),
                orphans = listOf(notificationRow("orphan")),
            )
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(delivered())

        val report = relay(outbox, inbox, sender).relay(RELAY_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.orphansIsolated shouldBe 1
        report.delivered shouldBe 1
        outbox.isolated shouldBe listOf(OutboxEntryId("orphan"))
        outbox.delivered shouldBe listOf(OutboxEntryId("fresh"))
    }

    @Test
    fun `lease 를 못 쥐면 claim 도 격리도 0 이다 — LEASE_BUSY`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("e7")), orphans = listOf(notificationRow("o")))
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(delivered())

        val report =
            relay(outbox, inbox, sender, leases = BusyLease())
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.Skipped>()

        report.reason shouldBe RelaySkipReason.LeaseBusy
        outbox.claimedKinds.shouldBeEmpty()
        outbox.claimedEntriesKinds.shouldBeEmpty()
        outbox.isolated.shouldBeEmpty()
        sender.requests.shouldBeEmpty()
    }

    /**
     * ADR 0005 D-4 「억제는 기록 억제가 아니다」 — 억제 환경에서는 claim 자체가 없다. 행이
     * `PENDING` 에 보존되므로 상태 분포가 전후로 불변이고, 고아 격리도 하지 않는다(보낼 수
     * 없는 환경에서 남의 run 이 남긴 행을 단방향 종단으로 태울 이유가 없다).
     */
    @Test
    fun `환경이 Live 가 아니면 claim 도 격리도 0 이다 — ENV_SUPPRESSED`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("e8")), orphans = listOf(notificationRow("o")))
        val inbox = FakeInboxPort()
        val sender = ScriptedSender(delivered())

        val report =
            relay(outbox, inbox, sender, policy = relayPolicy(DeliveryMode.DryRun))
                .relay(RELAY_LIMIT)
                .shouldBeInstanceOf<RelayReport.Skipped>()

        report.reason shouldBe RelaySkipReason.EnvironmentSuppressed
        outbox.claimedKinds.shouldBeEmpty()
        outbox.claimedEntriesKinds.shouldBeEmpty()
        outbox.isolated.shouldBeEmpty()
    }

    /** D-6F10-13 — relay 는 **자기 종류만** 묻는다(claim·고아 조회·lease 키 셋 다). */
    @Test
    fun `relay 는 자기 종류로만 claim 하고 고아를 묻고 lease 를 쥔다`() {
        val outbox = FakeOutboxPort(pending = listOf(notificationRow("e9")))
        val leases = GrantingLease()

        relay(outbox, FakeInboxPort(), ScriptedSender(delivered()), leases = leases).relay(RELAY_LIMIT)

        outbox.claimedKinds shouldBe listOf(OutboxConsumerKind.NotificationRequested)
        outbox.claimedEntriesKinds shouldBe listOf(OutboxConsumerKind.NotificationRequested)
        leases.kinds shouldBe listOf(OutboxConsumerKind.NotificationRequested)
    }

    /**
     * **T1/T2 가 갈려 있다**(D-6F10-3) — 경계 호출이 행마다 늘어난다. 한 트랜잭션으로 묶는
     * 변이는 이 계수를 1 로 떨어뜨린다. 행 하나의 run 은 claim 1 + 고아 조회 1 + inbox 조회
     * 1 + T2 1 = 4 다.
     */
    @Test
    fun `커밋 경계는 claim 과 종단 전이가 따로다 — 경계 호출 계수`() {
        val transactions = CountingTransactions()

        relay(
            FakeOutboxPort(pending = listOf(notificationRow("e10"))),
            FakeInboxPort(),
            ScriptedSender(delivered()),
            transactions = transactions,
        ).relay(RELAY_LIMIT)

        transactions.count shouldBe 4
    }

    /**
     * 전이표가 거부하면 **조용히 넘기지 않는다** — 그 거부는 업무 분기가 아니라 표가 바뀌었다는
     * 뜻이다. `claimedEntries` 가 `Claimed` 아닌 행을 돌려주는 정직하지 않은 어댑터를 흉내 내
     * 그 자리가 던지는 것을 잰다.
     */
    @Test
    fun `종단 전이가 port 에서 실패하면 예외가 run 밖으로 나간다`() {
        val outbox = ThrowingOutboxPort()

        shouldThrow<IllegalStateException> {
            relay(outbox, FakeInboxPort(), ScriptedSender(delivered())).relay(RELAY_LIMIT)
        }
    }
}

private fun delivered(): DeliveryResult.Delivered =
    DeliveryResult.Delivered(RELAY_NOW, MaskedTarget.mask("01012345678", relayPolicy(DeliveryMode.Live)))

/**
 * `markDelivered` 가 던지는 port — 계수 계약(D-6F10-2)의 실패가 relay 를 지나 run 밖으로
 * 올라오는지 잰다. 나머지 동작은 [FakeOutboxPort] 에 위임한다(그 한 메서드만 바꿔치운다 —
 * 「더하기만 한 변이는 초록」의 반대로, 바꿔치운 행동이 test 에 보이게).
 */
private class ThrowingOutboxPort(
    private val delegate: FakeOutboxPort = FakeOutboxPort(pending = listOf(notificationRow("boom"))),
) : OutboxPort by delegate {
    override fun markDelivered(transition: OutboxTransition.ToDelivered): Unit = error("전이가 행을 옮기지 못했다")
}

private fun relay(
    outbox: OutboxPort,
    inbox: FakeInboxPort,
    sender: ScriptedSender,
    leases: ConsumerLeasePort = GrantingLease(),
    transactions: ConsumerTransactionPort = CountingTransactions(),
    routes: RouteDirectory = SingleRouteDirectory(),
    policy: NotificationDeliveryPolicyData = relayPolicy(DeliveryMode.Live),
): RelayOutboxNotifications =
    RelayOutboxNotifications(
        outbox = outbox,
        inbox = inbox,
        dispatcher =
            DispatchNotification(
                routes = routes,
                renderer = EchoRenderer(),
                sender = sender,
                policyData = policy,
                environment = RuntimeEnvironment.Production,
            ),
        leases = leases,
        transactions = transactions,
        target = RELAY_TARGET,
        environment = RuntimeEnvironment.Production,
        policy = policy,
    )
