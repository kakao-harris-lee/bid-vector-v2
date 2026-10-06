package bidvector.workflow.notification

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.AggregateId
import bidvector.workflow.event.AggregateVersion
import bidvector.workflow.event.ClaimedOutboxRow
import bidvector.workflow.event.ConsumerLeasePort
import bidvector.workflow.event.ConsumerTransactionPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.event.EventEnvelope
import bidvector.workflow.event.EventId
import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.event.InboxPort
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.LeaseGuard
import bidvector.workflow.event.NotificationEvidencePayload
import bidvector.workflow.event.NotificationRequestedPayload
import bidvector.workflow.event.OutboxConsumerKind
import bidvector.workflow.event.OutboxEntryId
import bidvector.workflow.event.OutboxPort
import bidvector.workflow.event.OutboxTransition
import bidvector.workflow.strategy.OperatorId
import java.time.Instant

internal val RELAY_OWNER = OperatorId("relay-operator")
internal val RELAY_CHANNEL = Channel.Telegram
internal val RELAY_ROUTE = RouteKey("relay-route")
internal val RELAY_TARGET = RelayTarget(RELAY_OWNER, RELAY_CHANNEL)
internal val RELAY_NOW: Instant = Instant.parse("2026-10-06T00:00:00Z")

/**
 * relay test 의 fake 들 — **mock framework 를 쓰지 않는다**(v2-지침서 §5: domain·application
 * test 는 값과 fake port 로). 이 fake 들은 호출 사실을 값으로 기록해 test 가 그 값을 단언한다.
 */
internal class FakeOutboxPort(
    pending: List<ClaimedOutboxRow<*>> = emptyList(),
    private val orphans: List<ClaimedOutboxRow<*>> = emptyList(),
) : OutboxPort {
    private val queue = pending.toMutableList()
    val delivered = mutableListOf<OutboxEntryId>()
    val failed = mutableListOf<OutboxEntryId>()
    val isolated = mutableListOf<OutboxEntryId>()
    val claimedKinds = mutableListOf<OutboxConsumerKind>()
    val claimedEntriesKinds = mutableListOf<OutboxConsumerKind>()

    override fun register(envelope: EventEnvelope<*>): OutboxEntryId = error("relay 는 outbox 에 등록하지 않는다")

    override fun claim(
        limit: Int,
        kind: OutboxConsumerKind,
    ): List<ClaimedOutboxRow<*>> {
        claimedKinds += kind
        val taken = queue.take(limit)
        queue.removeAll(taken)
        return taken
    }

    override fun claimedEntries(kind: OutboxConsumerKind): List<ClaimedOutboxRow<*>> {
        claimedEntriesKinds += kind
        return orphans
    }

    override fun markDelivered(transition: OutboxTransition.ToDelivered) {
        delivered += transition.entryId
    }

    override fun markFailed(transition: OutboxTransition.ToFailed) {
        failed += transition.entryId
    }

    override fun markIsolated(transition: OutboxTransition.ToIsolated) {
        isolated += transition.entryId
    }
}

internal class FakeInboxPort(
    processed: Set<String> = emptySet(),
) : InboxPort {
    private val keys = processed.toMutableSet()
    val recorded = mutableListOf<IdempotencyKey>()

    override fun hasProcessed(key: IdempotencyKey): Boolean = key.value in keys

    override fun markProcessed(key: IdempotencyKey) {
        keys += key.value
        recorded += key
    }
}

/** 경계를 **센다** — T1/T2 가 실제로 갈렸는지는 호출 횟수가 말한다. */
internal class CountingTransactions : ConsumerTransactionPort {
    var count = 0
        private set

    override fun <T> inTransaction(block: () -> T): T {
        count += 1
        return block()
    }
}

internal class GrantingLease : ConsumerLeasePort {
    val kinds = mutableListOf<OutboxConsumerKind>()

    override fun <T> withLease(
        kind: OutboxConsumerKind,
        body: (LeaseGuard) -> T,
    ): LeaseAttempt<T> {
        kinds += kind
        return LeaseAttempt.Held(body(LeaseGuard { true }))
    }
}

/** **본문을 부르지 않는다** — Busy 가 claim 0·격리 0 을 뜻한다는 것이 이 fake 의 계약이다. */
internal class BusyLease : ConsumerLeasePort {
    override fun <T> withLease(
        kind: OutboxConsumerKind,
        body: (LeaseGuard) -> T,
    ): LeaseAttempt<Nothing> = LeaseAttempt.Busy
}

/**
 * [heldFor] 번 묻는 동안만 쥐고 있다고 답하는 임대(R1-M-1) — 「본문 도중에 잃는다」를 fake 로
 * 표현한다. 실 DB 쪽 측정은 `RelayLeaseLossDatabaseTest` 가 임대 연결을 실제로 끊어서 한다.
 *
 * **호출 서수가 곧 지점이다**(D-6F10-31 ② 의 네 자리). 1 = 획득 직후 · 2 = 고아 격리 전 ·
 * 3 = claim 전 · 4 번째부터 = 행마다 발송 전. 그래서 `heldFor = 2` 는 「격리는 했고 claim 은
 * 안 했다」를 뜻한다. 지점을 더하거나 옮기면 이 대응이 깨지고 아래 test 들이 붉어진다 —
 * 그것이 의도다(순서를 조용히 바꾸지 못하게 한다).
 */
internal class LosingLease(
    private val heldFor: Int,
) : ConsumerLeasePort {
    /** 몇 번 물었나 — 「지점이 넷이다」를 test 가 **셈으로** 확인하는 자리다(cr R-13 ⓐ). */
    var asked = 0
        private set

    override fun <T> withLease(
        kind: OutboxConsumerKind,
        body: (LeaseGuard) -> T,
    ): LeaseAttempt<T> =
        LeaseAttempt.Held(
            body(
                LeaseGuard {
                    asked += 1
                    asked <= heldFor
                },
            ),
        )
}

internal class SingleRouteDirectory(
    private val enabled: Boolean = true,
    private val key: RouteKey? = RELAY_ROUTE,
) : RouteDirectory {
    override fun routesFor(owner: OperatorId): Map<Channel, ChannelRoute> =
        mapOf(RELAY_CHANNEL to ChannelRoute(enabled = enabled, key = key))
}

internal class EchoRenderer : ContentRenderer {
    override fun render(
        contentRef: ContentRef,
        channel: Channel,
    ): RenderedContent = RenderedContent(channel, contentRef.value)
}

/** 미리 정한 결과를 그대로 돌려주는 sender — 호출 요청을 기록한다. */
internal class ScriptedSender(
    private val result: DeliveryResult,
) : NotificationSender {
    val requests = mutableListOf<DeliveryRequest>()

    override fun send(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult {
        requests += request
        return result
    }
}

internal fun relayPolicy(mode: DeliveryMode): NotificationDeliveryPolicyData =
    NotificationDeliveryPolicyData(
        environmentModes = RuntimeEnvironment.entries.associateWith { mode },
        maskedSuffixLength = 4,
    )

internal fun notificationRow(
    entryId: String,
    idempotencyKey: String = "key-$entryId",
    noticeId: String = "N-$entryId",
): ClaimedOutboxRow<NotificationRequestedPayload> =
    outboxRow(
        entryId,
        idempotencyKey,
        NotificationRequestedPayload(
            noticeId = noticeId,
            bidNowReasons = emptyList(),
            ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "relay-test"),
            strategyRevision = StrategyRevision(1),
            evidence = NotificationEvidencePayload.NotPredicted(reason = "CircuitOpen"),
        ),
    )

/**
 * 종류는 `NotificationRequested` 인데 payload 가 그 타입이 아닌 행 — **어댑터가 만들 수 없는
 * 행**이다(codec 이 `payload_type` 과 payload 클래스를 함께 정하므로). 그래서 relay 의 미지
 * payload 처분은 port 경계에서만 잴 수 있고, kind 필터가 들어온 뒤로 그 분기는 심층 방어다.
 */
internal fun foreignPayloadRow(entryId: String): ClaimedOutboxRow<String> =
    outboxRow(entryId, "key-$entryId", "not-a-notification-payload")

private fun <P> outboxRow(
    entryId: String,
    idempotencyKey: String,
    payload: P,
): ClaimedOutboxRow<P> =
    ClaimedOutboxRow(
        entryId = OutboxEntryId(entryId),
        eventId = EventId("evt-$entryId"),
        aggregateId = AggregateId("agg-$entryId"),
        aggregateVersion = AggregateVersion(0),
        occurredAt = RELAY_NOW,
        correlationId = CorrelationId("corr-$entryId"),
        causationId = null,
        idempotencyKey = IdempotencyKey(idempotencyKey),
        actor = null,
        payload = payload,
    )
