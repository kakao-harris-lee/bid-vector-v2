package bidvector.adapters.event

import bidvector.adapters.persistence.ConnectionSource
import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.event.InboxPort

/**
 * [InboxPort] JDBC 구현(scope.md ⑤) — 같은 [IdempotencyKey] 재적재는 `inbox` PK 유일성이
 * 행 1개로 수렴시킨다(`INSERT ... ON CONFLICT DO NOTHING`). `hasProcessed`·`markProcessed`는
 * 별도 호출이라 그 사이 경합이 있을 수 있지만, 이중 기록 자체는 제약이 막는다(판정이 아니라
 * 제약이 센다 — 설계 검토 (1) 「구성으로 닫는 형태」).
 */
class JdbcInboxPort(
    private val connections: ConnectionSource,
) : InboxPort {
    override fun hasProcessed(key: IdempotencyKey): Boolean =
        connections.withConnection { connection ->
            connection.prepareStatement(EventSql.SELECT_INBOX).use { statement ->
                statement.setString(1, key.value)
                statement.executeQuery().use { it.next() }
            }
        }

    override fun markProcessed(key: IdempotencyKey) {
        connections.withConnection { connection ->
            connection.prepareStatement(EventSql.INSERT_INBOX).use { statement ->
                statement.setString(1, key.value)
                statement.executeUpdate()
            }
        }
    }
}
