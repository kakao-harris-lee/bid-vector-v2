package bidvector.workflow.event

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.OperatorId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * verifier H-1 시정 — `OutboxPort.claim`이 완성된 [EventEnvelope]가 아니라 원시 행
 * ([ClaimedOutboxRow])을 돌려주고, `workflow` 안(internal)의 [OutboxEntry.restore]만
 * 그 행을 [OutboxEntry]로 되살린다. 어댑터(4C-2)는 [EventEnvelope]를 다루지 않는다 —
 * `EventEnvelope.restore`가 `internal`로 내려가면서 그 밖에서 임의 필드로 봉투를 지어
 * `register`에 넣는 경로가 구조적으로 사라진다.
 */
class OutboxEntryTest {
    @Test
    fun `OutboxEntry restore 는 원시 행에서 Claimed 상태의 항목을 되살린다`() {
        val row =
            ClaimedOutboxRow(
                entryId = OutboxEntryId("entry-1"),
                eventId = EventId("evt-1"),
                aggregateId = AggregateId("operator-strategy"),
                aggregateVersion = AggregateVersion(3),
                occurredAt = Instant.parse("2026-09-09T00:00:00Z"),
                correlationId = CorrelationId("evt-1"),
                causationId = null,
                idempotencyKey = IdempotencyKey("strategy-updated-3"),
                actor = Actor.Operator(OperatorId("op-1")),
                payload =
                    StrategyEvent.StrategyUpdated(
                        StrategyRevision(3),
                        PolicyVersion(EffectiveFrom.Initial, "test"),
                    ),
            )

        val entry = OutboxEntry.restore(row)

        entry.id shouldBe OutboxEntryId("entry-1")
        entry.state shouldBe OutboxEntryState.Claimed
        entry.envelope.eventId shouldBe EventId("evt-1")
        entry.envelope.actor shouldBe Actor.Operator(OperatorId("op-1"))
        entry.envelope.payload shouldBe
            StrategyEvent.StrategyUpdated(StrategyRevision(3), PolicyVersion(EffectiveFrom.Initial, "test"))
    }
}
