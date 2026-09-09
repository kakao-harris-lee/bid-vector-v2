package bidvector.workflow.event

import bidvector.strategy.StrategyEvent
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.EventSink

/** `StrategyUpdated`가 발행되는 aggregate — 단일 회사 전제(4A `OperatorId` 관례)의 단일 aggregate. */
private val STRATEGY_AGGREGATE_ID = AggregateId("operator-strategy")

/**
 * 4A [EventSink]의 실 구현(scope.md ⑦) — payload를 봉투에 실어 [outbox]에 등록한다. 4A
 * 알려진 제한의 앞쪽 절반(봉투·등록 경로)이 여기서 선다 — 원자성은 4C-2.
 *
 * `idempotencyKey`는 [bidvector.strategy.StrategyRevision]에서 유도한다 — revision은
 * `Confirmed` 전이마다 정확히 하나씩만 오르므로(1E 불변식) 같은 업무 사실의 재발행은 항상
 * 같은 키를 낸다(`OutboxEventSinkTest` 「같은 revision 재발행 → 같은 idempotencyKey」).
 * [EventIdFactory]가 내는 [EventId]는 그 축이 아니다 — 발행 시도마다 새로 난다.
 */
class OutboxEventSink(
    private val outbox: OutboxPort,
    private val ids: EventIdFactory,
    private val clock: Clock,
) : EventSink {
    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) {
        when (event) {
            is StrategyEvent.StrategyUpdated -> {
                // 이 이벤트는 command(사용자 확인)에서 바로 나므로 인과 사슬의 기원이다 —
                // correlationId는 이 이벤트 자신의 eventId와 같은 값으로 시작한다.
                val eventId = ids.newId()
                val envelope =
                    forStrategyUpdated(
                        eventId = eventId,
                        aggregateId = STRATEGY_AGGREGATE_ID,
                        aggregateVersion = AggregateVersion(event.revision.value.toLong()),
                        occurredAt = clock.now(),
                        correlationId = CorrelationId(eventId.value),
                        causationId = null,
                        idempotencyKey = IdempotencyKey("strategy-updated-${event.revision.value}"),
                        actor = actor,
                        payload = event,
                    )
                outbox.register(envelope)
            }
        }
    }
}
