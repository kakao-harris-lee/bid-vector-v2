package bidvector.adapters.event

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import java.sql.Connection
import javax.sql.DataSource

/** 기본값으로 심을 payload — 실제 값은 중요하지 않은 test(claim 경합 등)가 쓴다. */
private val PLACEHOLDER_EVENT =
    StrategyEvent.StrategyUpdated(StrategyRevision(0), PolicyVersion(EffectiveFrom.Initial, "placeholder"))

/**
 * `outbox` 행을 손으로 하나 심는 공용 test 헬퍼(중복 금지, 3D `PersistenceTestSupport` 관례)
 * — [JdbcOutboxPortTest]·[OutboxClaimConcurrencyTest]·[OutboxTransitionSqlTest]가 공유한다.
 * production 경로(`JdbcOutboxPort.register`)를 지나지 않고 임의 상태·payload_type 을 직접
 * 심어야 하는 test(claim 경합·전이 SQL·fail-closed 복원)만 이 헬퍼를 쓴다 — 정상 등록
 * 경로를 재는 test는 `JdbcOutboxPort.register`(또는 `OutboxEventSink`)를 그대로 쓴다.
 *
 * 기본 payload_type/payload는 **유효한** `StrategyUpdated` 인코딩이다 — `claim`이 항상
 * payload를 디코드하므로(fail-closed), payload 자체가 test의 관심사가 아닌 경우(claim
 * 경합·전이 SQL)에도 디코드가 실패하지 않아야 한다. payload_type만 의도적으로 깨는
 * test(fail-closed 복원 실측)는 `payloadType`을 명시적으로 넘긴다.
 */
internal fun insertPendingOutboxRow(
    dataSource: DataSource,
    entryId: String,
    idempotencyKey: String,
    payloadType: String = OutboxPayloadCodec.STRATEGY_UPDATED_TYPE,
    payload: String = OutboxPayloadCodec.encode(PLACEHOLDER_EVENT),
) {
    dataSource.connection.use { connection ->
        insertPendingOutboxRow(connection, entryId, idempotencyKey, payloadType, payload)
    }
}

internal fun insertPendingOutboxRow(
    connection: Connection,
    entryId: String,
    idempotencyKey: String,
    payloadType: String,
    payload: String,
) {
    connection
        .prepareStatement(
            "INSERT INTO outbox (entry_id, event_id, aggregate_id, aggregate_version, occurred_at, " +
                "correlation_id, causation_id, idempotency_key, actor_kind, actor_detail, " +
                "payload_type, payload, state) " +
                "VALUES (?, ?, 'operator-strategy', 1, now(), ?, NULL, ?, NULL, NULL, ?, ?, 'PENDING')",
        ).use { statement ->
            statement.setString(1, entryId)
            statement.setString(2, "evt-$entryId")
            statement.setString(3, "corr-$entryId")
            statement.setString(4, idempotencyKey)
            statement.setString(5, payloadType)
            statement.setString(6, payload)
            statement.executeUpdate()
        }
}

internal fun outboxStateOf(
    dataSource: DataSource,
    entryId: String,
): String? =
    dataSource.connection.use { connection ->
        connection.prepareStatement(EventSql.SELECT_OUTBOX_STATE).use { statement ->
            statement.setString(1, entryId)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }
