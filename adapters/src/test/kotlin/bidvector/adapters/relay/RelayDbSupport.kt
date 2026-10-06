package bidvector.adapters.relay

import bidvector.adapters.e2e.EchoContentRenderer
import bidvector.adapters.e2e.RecordingNotificationSender
import bidvector.adapters.e2e.SingleRouteDirectory
import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.event.JdbcInboxPort
import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.event.OutboxPayloadCodec
import bidvector.adapters.event.PostgresAdvisoryLockLease
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.ConsumerTransactionPort
import bidvector.workflow.event.NotificationEvidencePayload
import bidvector.workflow.event.NotificationRequestedPayload
import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.DispatchNotification
import bidvector.workflow.notification.NOTIFICATION_DELIVERY_POLICY
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RelayOutboxNotifications
import bidvector.workflow.notification.RelayTarget
import bidvector.workflow.notification.RouteKey
import bidvector.workflow.notification.RuntimeEnvironment
import bidvector.workflow.strategy.OperatorId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.sql.DataSource

internal val RELAY_DB_NOW: Instant = Instant.parse("2026-10-06T00:00:00Z")
internal val RELAY_DB_OWNER = OperatorId("relay-db-operator")
internal val RELAY_DB_CHANNEL = Channel.Telegram
internal val RELAY_DB_ROUTE = RouteKey("relay-db-route")
internal const val RELAY_DB_LIMIT = 10

internal fun relayNotificationPolicy(): NotificationDeliveryPolicyData {
    val resolution = NOTIFICATION_DELIVERY_POLICY.resolve(LocalDate.ofInstant(RELAY_DB_NOW, ZoneOffset.UTC))
    check(resolution is Resolution.Resolved) { "NOTIFICATION_DELIVERY_POLICY 가 해소되지 않았다: $resolution" }
    return resolution.value
}

/**
 * 실 DB 위의 relay 조립 — [NotificationRelayRun] 과 **같은 모양**이지만 발송 축 셋을 fake 로
 * 바꿔치운다(실 채널이 없다, `OPEN-STR-12`). production 조립을 그대로 쓸 수 없는 이유가
 * 그것이고, 바꿔치우는 것은 그 셋뿐이다 — outbox·inbox·lease·경계·use case 는 production 이다.
 */
internal class RelayHarness(
    dataSource: DataSource,
    environment: RuntimeEnvironment = RuntimeEnvironment.Production,
    transactionsFor: (TransactionBoundary) -> ConsumerTransactionPort = { ConsumerTransactions(it) },
) {
    private val policy = relayNotificationPolicy()
    private val boundary = TransactionBoundary(dataSource)

    val sender = RecordingNotificationSender(RELAY_DB_NOW, policy)

    val relay =
        RelayOutboxNotifications(
            outbox = JdbcOutboxPort(boundary),
            inbox = JdbcInboxPort(boundary),
            dispatcher =
                DispatchNotification(
                    routes = SingleRouteDirectory(RELAY_DB_OWNER, RELAY_DB_CHANNEL, RELAY_DB_ROUTE),
                    renderer = EchoContentRenderer(),
                    sender = sender,
                    policyData = policy,
                    environment = environment,
                ),
            leases = PostgresAdvisoryLockLease(dataSource),
            transactions = transactionsFor(boundary),
            target = RelayTarget(RELAY_DB_OWNER, RELAY_DB_CHANNEL),
            environment = environment,
            policy = policy,
        )
}

/**
 * **T2 를 겨냥해 바꿔치우는 경계** — [atCall] 번째 호출 **직전에** [sideEffect] 를 돌린다.
 * 부작용은 경계 **밖**의 별도 커넥션으로 돌아야 한다([TransactionBoundary] 는 중첩을 거부한다).
 */
internal class InterferingTransactions(
    private val delegate: ConsumerTransactionPort,
    private val atCall: Int,
    private val sideEffect: () -> Unit,
) : ConsumerTransactionPort {
    private var calls = 0

    override fun <T> inTransaction(block: () -> T): T {
        calls += 1
        if (calls == atCall) sideEffect()
        return delegate.inTransaction(block)
    }
}

/** [atCall] 번째 경계 호출에서 던진다 — 「발송 뒤·종단 전 크래시」의 정직한 대역. */
internal class CrashingTransactions(
    private val delegate: ConsumerTransactionPort,
    private val atCall: Int,
) : ConsumerTransactionPort {
    private var calls = 0

    override fun <T> inTransaction(block: () -> T): T {
        calls += 1
        if (calls == atCall) throw RelayWorkerDied()
        return delegate.inTransaction(block)
    }
}

/** 크래시 주입의 sentinel — 의도한 예외만 삼키고 다른 예외는 올라가게 한다. */
internal class RelayWorkerDied : RuntimeException("relay 워커 사망 시뮬레이션")

internal fun insertPendingNotificationRow(
    dataSource: DataSource,
    entryId: String,
    idempotencyKey: String = "notification-$entryId",
    noticeId: String = "N-$entryId",
) = insertPendingRow(
    dataSource,
    entryId,
    idempotencyKey,
    OutboxPayloadCodec.NOTIFICATION_REQUESTED_TYPE,
    OutboxPayloadCodec.encode(
        NotificationRequestedPayload(
            noticeId = noticeId,
            bidNowReasons = emptyList(),
            ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "relay-db-test"),
            strategyRevision = StrategyRevision(1),
            evidence = NotificationEvidencePayload.NotPredicted(reason = "CircuitOpen"),
        ),
    ),
)

internal fun insertPendingStrategyRow(
    dataSource: DataSource,
    entryId: String,
) = insertPendingRow(
    dataSource,
    entryId,
    "strategy-$entryId",
    OutboxPayloadCodec.STRATEGY_UPDATED_TYPE,
    OutboxPayloadCodec.encode(
        StrategyEvent.StrategyUpdated(StrategyRevision(3), PolicyVersion(EffectiveFrom.Initial, "relay-db-test")),
    ),
)

/**
 * `adapters.event` 의 test 헬퍼와 **같은 일을 하는 사본이 아니다** — 그쪽은 `internal` 이지만
 * 같은 모듈이라 쓸 수 있고, 실제로 쓴다. 이 함수는 그 헬퍼로 가는 한 줄 위임이다.
 */
private fun insertPendingRow(
    dataSource: DataSource,
    entryId: String,
    idempotencyKey: String,
    payloadType: String,
    payload: String,
) = bidvector.adapters.event.insertPendingOutboxRow(dataSource, entryId, idempotencyKey, payloadType, payload)

internal fun outboxStateCounts(dataSource: DataSource): Map<String, Int> =
    dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT state, count(*) FROM outbox GROUP BY state").use { statement ->
            statement.executeQuery().use { rs ->
                buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) }
            }
        }
    }

internal fun inboxKeyCount(dataSource: DataSource): Int =
    dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT count(*) FROM inbox").use { statement ->
            statement.executeQuery().use { rs ->
                check(rs.next())
                rs.getInt(1)
            }
        }
    }

internal fun forceOutboxState(
    dataSource: DataSource,
    entryId: String,
    state: String,
) = dataSource.connection.use { connection ->
    connection.prepareStatement("UPDATE outbox SET state = ? WHERE entry_id = ?").use { statement ->
        statement.setString(1, state)
        statement.setString(2, entryId)
        check(statement.executeUpdate() == 1) { "상태 강제 변경이 행을 옮기지 못했다: $entryId" }
    }
}
