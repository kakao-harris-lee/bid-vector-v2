package bidvector.adapters.evaluation

import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.event.CorrelationId
import java.util.UUID

/**
 * [CorrelationIdFactory] 첫 production 구현(M6/6F-2, D-6F2-8) — `UUID.randomUUID()`
 * (UUIDv4)로 발급한다(`JdbcEventIdFactory`와 같은 관례 — 이 축도 시간 정렬 UUID가 필요할
 * 근거가 없다). `bidvector.adapters.evaluation`에 둔다 — port가 `workflow.evaluation`에
 * 선언돼 있고 이 패키지가 그 루트를 이미 허용한다.
 */
class UuidCorrelationIdFactory : CorrelationIdFactory {
    override fun newId(): CorrelationId = CorrelationId(UUID.randomUUID().toString())
}
