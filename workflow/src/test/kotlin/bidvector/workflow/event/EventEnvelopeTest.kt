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

private val NOW: Instant = Instant.parse("2026-09-09T00:00:00Z")
private val ACTOR = Actor.Operator(OperatorId("op-1"))
private val EVENT = StrategyEvent.StrategyUpdated(StrategyRevision(2), PolicyVersion(EffectiveFrom.Initial, "test"))

/**
 * scope.md ①, D-M4-4 (a) — 봉투 아홉 필드와 `StrategyUpdated` 전용 생성 경로(actor 필수,
 * 설계 검토 (2) #1·#3). `newEnvelope`/`forStrategyUpdated`가 `internal`이라 봉투를 직접
 * 조립하는 위조는 이 test 가 아니라 임시 clone 컴파일 거부 실측(evidence commands.md)이
 * 증명한다 — 이 test 는 정상 생성 경로의 형태만 진다.
 */
class EventEnvelopeTest {
    @Test
    fun `forStrategyUpdated 는 아홉 필드를 채우고 causationId 는 null 이 허용된다`() {
        val envelope =
            forStrategyUpdated(
                eventId = EventId("evt-1"),
                aggregateId = AggregateId("operator-strategy"),
                aggregateVersion = AggregateVersion(2),
                occurredAt = NOW,
                correlationId = CorrelationId("evt-1"),
                causationId = null,
                idempotencyKey = IdempotencyKey("strategy-updated-2"),
                actor = ACTOR,
                payload = EVENT,
            )

        envelope.eventId shouldBe EventId("evt-1")
        envelope.aggregateId shouldBe AggregateId("operator-strategy")
        envelope.aggregateVersion shouldBe AggregateVersion(2)
        envelope.occurredAt shouldBe NOW
        envelope.correlationId shouldBe CorrelationId("evt-1")
        envelope.causationId shouldBe null
        envelope.idempotencyKey shouldBe IdempotencyKey("strategy-updated-2")
        envelope.actor shouldBe ACTOR
        envelope.payload shouldBe EVENT
    }

    @Test
    fun `newEnvelope 는 actor 없이도(nullable) 만들 수 있다 — 제네릭 봉투는 actor 를 강제하지 않는다`() {
        val envelope =
            newEnvelope(
                eventId = EventId("evt-2"),
                aggregateId = AggregateId("operator-strategy"),
                aggregateVersion = AggregateVersion(1),
                occurredAt = NOW,
                correlationId = CorrelationId("evt-2"),
                causationId = null,
                idempotencyKey = IdempotencyKey("k-2"),
                actor = null,
                payload = EVENT,
            )

        envelope.actor shouldBe null
    }

    @Test
    fun `restore 는 저장소 복원 경로로 같은 값을 그대로 되살린다`() {
        val restored =
            EventEnvelope.restore(
                eventId = EventId("evt-3"),
                aggregateId = AggregateId("operator-strategy"),
                aggregateVersion = AggregateVersion(3),
                occurredAt = NOW,
                correlationId = CorrelationId("evt-3"),
                causationId = CausationId("evt-2"),
                idempotencyKey = IdempotencyKey("k-3"),
                actor = ACTOR,
                payload = EVENT,
            )

        restored.eventId shouldBe EventId("evt-3")
        restored.causationId shouldBe CausationId("evt-2")
    }
}
