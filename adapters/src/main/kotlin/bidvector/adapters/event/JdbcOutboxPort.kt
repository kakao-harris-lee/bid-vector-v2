package bidvector.adapters.event

import bidvector.adapters.persistence.ConnectionSource
import bidvector.workflow.event.AggregateId
import bidvector.workflow.event.AggregateVersion
import bidvector.workflow.event.CausationId
import bidvector.workflow.event.ClaimedOutboxRow
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.event.EventEnvelope
import bidvector.workflow.event.EventId
import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.event.OutboxEntryId
import bidvector.workflow.event.OutboxPort
import bidvector.workflow.event.OutboxTransition
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * [OutboxPort] JDBC 구현(scope.md ③, D-4C2-1 갈래 b) — **`DataSource`를 갖지 않는다**. 이
 * 클래스는 [ConnectionSource]만 받는다 — [ConnectionSource]가 `sealed`라(verifier r1 H-2
 * 시정) 다른 모듈은 그 어떤 구현도 새로 만들 수 없고, 이 모듈 안의 두 구현 중
 * [bidvector.adapters.persistence.TransactionBoundary] 밖에서 부르면
 * [ConnectionSource.withConnection]이 실제로 던진다(교차 모듈 구조 우회는 컴파일 층에서,
 * 같은 모듈 안 오호출은 실행 층에서 — 설계 검토 (2) 우회 3). `register`가 도메인 write와
 * 같은 트랜잭션에서 커밋되는지는 그 write를 함께 감싸는 `TransactionBoundary.inTransaction
 * { ... }` 호출부가 정한다 — 이 클래스 자신은 트랜잭션을 열지 않는다.
 *
 * `claim`은 [EventEnvelope]를 다루지 않는다(4C-1 verifier H-1 시정) — DB 행을 [ClaimedOutboxRow]
 * (원시 필드)로만 되살린다. 봉투 복원은 `workflow` 안의 `internal` 매핑(`OutboxEntry.restore`)
 * 몫이다.
 */
class JdbcOutboxPort(
    private val connections: ConnectionSource,
) : OutboxPort {
    override fun register(envelope: EventEnvelope<*>): OutboxEntryId {
        val entryId = OutboxEntryId(UUID.randomUUID().toString())
        connections.withConnection { connection ->
            connection.prepareStatement(EventSql.INSERT_OUTBOX).use { statement ->
                bindEnvelope(statement, entryId, envelope)
                statement.executeUpdate()
            }
        }
        return entryId
    }

    /**
     * `Pending`을 `Claimed`로 옮기며 최대 [limit]개를 반환한다(scope.md ④, 설계 검토 (4)-④)
     * — `FOR UPDATE SKIP LOCKED`로 후보를 잠그고 같은 커넥션에서 `Claimed`로 옮긴다.
     *
     * **verifier r1 L-6 시정** — 커밋 시점을 정하는 것은 이 메서드가 아니라 **호출부**다.
     * 이 port는 트랜잭션을 열지도 커밋하지도 않는다(`TransactionBoundary.inTransaction`이
     * 진다) — 호출부가 `claim`만 감싸 바로 커밋하면 배달까지 트랜잭션을 열어 두지 않는
     * at-most-once(D-M4-5)가 되고, 워커가 그 커밋 전에 죽으면(호출부 트랜잭션이 롤백)
     * `Pending`으로 남아 다음 `claim`이 다시 집는다. 이 port 자신은 그 경계 선택을 강제하지
     * 않는다 — 설계 검토 (4)-④가 요구하는 「claim 트랜잭션을 배달까지 열어 두지 않는다」는
     * 호출부의 규율이다.
     */
    override fun claim(limit: Int): List<ClaimedOutboxRow<*>> =
        connections.withConnection { connection ->
            val candidates = selectPendingForUpdate(connection, limit)
            markClaimed(connection, candidates)
            candidates
        }

    override fun markDelivered(transition: OutboxTransition.ToDelivered) =
        transitionState(transition.entryId, EventSql.MARK_DELIVERED)

    override fun markFailed(transition: OutboxTransition.ToFailed) =
        transitionState(transition.entryId, EventSql.MARK_FAILED)

    override fun markIsolated(transition: OutboxTransition.ToIsolated) =
        transitionState(transition.entryId, EventSql.MARK_ISOLATED)

    private fun transitionState(
        entryId: OutboxEntryId,
        sql: String,
    ) {
        connections.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, entryId.value)
                statement.executeUpdate()
            }
        }
    }

    private fun selectPendingForUpdate(
        connection: java.sql.Connection,
        limit: Int,
    ): List<ClaimedOutboxRow<*>> {
        val candidates = mutableListOf<ClaimedOutboxRow<*>>()
        connection.prepareStatement(EventSql.SELECT_PENDING_FOR_UPDATE_SKIP_LOCKED).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rs ->
                while (rs.next()) candidates += rs.toClaimedOutboxRow()
            }
        }
        return candidates
    }

    private fun markClaimed(
        connection: java.sql.Connection,
        candidates: List<ClaimedOutboxRow<*>>,
    ) {
        if (candidates.isEmpty()) return
        connection.prepareStatement(EventSql.MARK_CLAIMED).use { statement ->
            for (row in candidates) {
                statement.setString(1, row.entryId.value)
                statement.executeUpdate()
            }
        }
    }

    private fun bindEnvelope(
        statement: java.sql.PreparedStatement,
        entryId: OutboxEntryId,
        envelope: EventEnvelope<*>,
    ) {
        var index = 1
        statement.setString(index++, entryId.value)
        statement.setString(index++, envelope.eventId.value)
        statement.setString(index++, envelope.aggregateId.value)
        statement.setLong(index++, envelope.aggregateVersion.value)
        statement.setTimestamp(index++, Timestamp.from(envelope.occurredAt))
        statement.setString(index++, envelope.correlationId.value)
        statement.setString(index++, envelope.causationId?.value)
        statement.setString(index++, envelope.idempotencyKey.value)
        statement.setString(index++, ActorCodec.kindOf(envelope.actor))
        statement.setString(index++, ActorCodec.detailOf(envelope.actor))
        statement.setString(index++, OutboxPayloadCodec.payloadTypeOf(envelope.payload))
        statement.setString(index, OutboxPayloadCodec.encode(envelope.payload))
    }
}

private fun ResultSet.toClaimedOutboxRow(): ClaimedOutboxRow<*> {
    val payloadType = getString("payload_type")
    val payload = getString("payload")
    return ClaimedOutboxRow(
        entryId = OutboxEntryId(getString("entry_id")),
        eventId = EventId(getString("event_id")),
        aggregateId = AggregateId(getString("aggregate_id")),
        aggregateVersion = AggregateVersion(getLong("aggregate_version")),
        occurredAt = getTimestamp("occurred_at").toInstant(),
        correlationId = CorrelationId(getString("correlation_id")),
        causationId = getString("causation_id")?.let(::CausationId),
        idempotencyKey = IdempotencyKey(getString("idempotency_key")),
        actor = ActorCodec.decode(getString("actor_kind"), getString("actor_detail")),
        payload = OutboxPayloadCodec.decode(payloadType, payload),
    )
}
