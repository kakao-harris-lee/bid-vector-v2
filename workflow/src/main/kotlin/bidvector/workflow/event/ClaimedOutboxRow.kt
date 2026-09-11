package bidvector.workflow.event

import bidvector.workflow.strategy.Actor
import java.time.Instant

/**
 * outbox 저장소가 [OutboxPort.claim]으로 돌려주는 **원시 행**(scope.md ③, verifier H-1
 * 시정) — 완성된 [EventEnvelope]가 아니다. 어댑터(persistence, 4C-2)는 DB 컬럼을 그대로
 * 이 값에 옮겨 담기만 한다 — [EventEnvelope]를 다루지 않는다(그 생성자는 `workflow` 안에서만
 * 유효하다, `EventEnvelope.restore`도 이 시정으로 `internal`이다).
 *
 * **이 타입의 생성자는 공개다** — 아무 모듈이나 이 값을 지어도 얻는 것이 없다.
 * `OutboxPort.register`는 [EventEnvelope]를 받고, 이 행에서 [EventEnvelope]로 가는
 * 유일한 경로는 `workflow` 안의 `internal` 매핑([OutboxEntry.restore])뿐이다 — 그 함수를
 * `workflow` 밖에서는 부를 수 없으므로, 이 행을 아무리 위조해도 outbox 에 주입할 방법이
 * 없다. `InboxPort`와 같은 처분(설계 검토 (2) — 위조해도 자기 소비만 손해)이 아니라
 * **위조해도 소비할 방법 자체가 없다**는, 더 강한 형태의 무해함이다.
 */
data class ClaimedOutboxRow<out P>(
    val entryId: OutboxEntryId,
    val eventId: EventId,
    val aggregateId: AggregateId,
    val aggregateVersion: AggregateVersion,
    val occurredAt: Instant,
    val correlationId: CorrelationId,
    val causationId: CausationId?,
    val idempotencyKey: IdempotencyKey,
    val actor: Actor?,
    val payload: P,
)
