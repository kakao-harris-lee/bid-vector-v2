package bidvector.adapters.event

import bidvector.workflow.event.EventId
import bidvector.workflow.event.EventIdFactory
import java.util.UUID

/**
 * [EventIdFactory] JDBC 어댑터(scope.md ⑥) — `UUID.randomUUID()`(UUIDv4)로 발급한다. claim
 * 순서는 `outbox.inserted_at`이 지므로(V6, `outbox_pending_order_idx`) 시간 정렬 UUID(v7
 * 등)가 필요하지 않다 — 새 라이브러리를 끌어오지 않는다(측정된 필요 없음).
 */
class JdbcEventIdFactory : EventIdFactory {
    override fun newId(): EventId = EventId(UUID.randomUUID().toString())
}
