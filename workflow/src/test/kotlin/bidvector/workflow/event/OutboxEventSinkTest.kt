package bidvector.workflow.event

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.OperatorId
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-09-09T00:00:00Z")
private val ACTOR = Actor.Operator(OperatorId("op-1"))

private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

private class SequentialEventIdFactory : EventIdFactory {
    private var counter = 0

    override fun newId(): EventId {
        counter += 1
        return EventId("evt-$counter")
    }
}

/**
 * `OutboxEventSinkTest`는 [register]만 잰다 — `claim`·`mark*`는 배달 오케스트레이션(4C-2)
 * 소관이라 이 fake 는 호출 사실만 기록한다(detekt `EmptyFunctionBlock` 회피 겸 미사용
 * 표면의 최소 증거).
 */
private class InMemoryOutboxPort : OutboxPort {
    val registered = mutableListOf<EventEnvelope<*>>()
    val delivered = mutableListOf<OutboxTransition.ToDelivered>()
    val failed = mutableListOf<OutboxTransition.ToFailed>()
    val isolated = mutableListOf<OutboxTransition.ToIsolated>()
    private var nextEntryId = 0

    override fun register(envelope: EventEnvelope<*>): OutboxEntryId {
        registered += envelope
        nextEntryId += 1
        return OutboxEntryId("outbox-$nextEntryId")
    }

    override fun claim(limit: Int): List<OutboxEntry> = emptyList()

    override fun markDelivered(transition: OutboxTransition.ToDelivered) {
        delivered += transition
    }

    override fun markFailed(transition: OutboxTransition.ToFailed) {
        failed += transition
    }

    override fun markIsolated(transition: OutboxTransition.ToIsolated) {
        isolated += transition
    }
}

/**
 * scope.md ⑦ — 4A `EventSink` 구현. payload 를 봉투에 싣고 `OutboxPort.register` 로
 * 넘긴다. actor 는 `StrategyUpdated` 전용 생성 경로(`forStrategyUpdated`)가 필수로 요구한다
 * (D-M4-4 (a)).
 */
class OutboxEventSinkTest {
    @Test
    fun `StrategyUpdated 를 발행하면 actor 를 실은 봉투 하나가 outbox 에 등록된다`() {
        val outbox = InMemoryOutboxPort()
        val sink = OutboxEventSink(outbox, SequentialEventIdFactory(), FixedClock(NOW))
        val event = StrategyEvent.StrategyUpdated(StrategyRevision(3), PolicyVersion(EffectiveFrom.Initial, "test"))

        sink.publish(event, ACTOR)

        outbox.registered shouldHaveSize 1
        val envelope = outbox.registered.single()
        envelope.payload shouldBe event
        envelope.actor shouldBe ACTOR
        envelope.aggregateVersion shouldBe AggregateVersion(3)
        envelope.occurredAt shouldBe NOW
    }

    @Test
    fun `같은 revision 을 두 번 발행해도 idempotencyKey 는 같다 — 재전달 dedup 의 전제`() {
        val outbox = InMemoryOutboxPort()
        val sink = OutboxEventSink(outbox, SequentialEventIdFactory(), FixedClock(NOW))
        val event = StrategyEvent.StrategyUpdated(StrategyRevision(5), PolicyVersion(EffectiveFrom.Initial, "test"))

        sink.publish(event, ACTOR)
        sink.publish(event, ACTOR)

        val keys = outbox.registered.map { it.idempotencyKey }.toSet()
        keys shouldHaveSize 1
    }

    @Test
    fun `발행마다 새 eventId 를 받는다 — eventId 는 재전달 dedup 축이 아니다`() {
        val outbox = InMemoryOutboxPort()
        val sink = OutboxEventSink(outbox, SequentialEventIdFactory(), FixedClock(NOW))
        val event = StrategyEvent.StrategyUpdated(StrategyRevision(5), PolicyVersion(EffectiveFrom.Initial, "test"))

        sink.publish(event, ACTOR)
        sink.publish(event, ACTOR)

        val eventIds = outbox.registered.map { it.eventId }.toSet()
        eventIds shouldHaveSize 2
    }
}
