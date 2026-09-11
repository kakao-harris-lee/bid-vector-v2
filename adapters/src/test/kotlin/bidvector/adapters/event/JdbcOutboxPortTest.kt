package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.OutboxEventSink
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.OperatorId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-09-10T00:00:00Z")
private val ACTOR = Actor.Operator(OperatorId("op-4c2"))

private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

private fun strategyUpdated(revision: Int) =
    StrategyEvent.StrategyUpdated(StrategyRevision(revision), PolicyVersion(EffectiveFrom.Initial, "test"))

/**
 * scope.md ①③④ — 통합 test. [OutboxEventSink](이미 존재하는 4A `EventSink` 구현)를 통해
 * 봉투를 만든다 — `adapters`는 [bidvector.workflow.event.EventEnvelope]를 직접 지을 수
 * 없다(4C-1 `internal constructor`, 이 slice의 통제 대상). `register`가 production 경로이자
 * 유일한 획득 경로다.
 */
class JdbcOutboxPortTest : PersistenceTestSupport() {
    @Test
    fun `register 로 등록한 항목을 claim 이 봉투 필드 전부 그대로 되살린다`() {
        val boundary = TransactionBoundary(dataSource())
        val outbox = JdbcOutboxPort(boundary)
        val sink = OutboxEventSink(outbox, JdbcEventIdFactory(), FixedClock(NOW))
        val event = strategyUpdated(11)

        boundary.inTransaction { sink.publish(event, ACTOR) }
        val claimed = boundary.inTransaction { outbox.claim(10) }

        claimed shouldHaveSize 1
        val row = claimed.single()
        row.payload shouldBe event
        row.actor shouldBe ACTOR
        row.occurredAt shouldBe NOW
        row.idempotencyKey.value shouldBe "strategy-updated-11"
        row.entryId.value.isBlank() shouldBe false
    }

    @Test
    fun `claim 은 등록 순서(inserted_at)대로 반환한다`() {
        val boundary = TransactionBoundary(dataSource())
        val outbox = JdbcOutboxPort(boundary)
        val sink = OutboxEventSink(outbox, JdbcEventIdFactory(), FixedClock(NOW))
        val first = strategyUpdated(1)
        val second = strategyUpdated(2)

        boundary.inTransaction { sink.publish(first, ACTOR) }
        boundary.inTransaction { sink.publish(second, ACTOR) }
        val claimed = boundary.inTransaction { outbox.claim(10) }

        claimed.map { it.payload } shouldBe listOf(first, second)
    }

    @Test
    fun `actor 가 없는 행은 claim 에서 actor null 로 되살아난다`() {
        // OutboxEventSink.forStrategyUpdated 는 actor 를 필수로 요구하므로(D-M4-4 (a)) 이
        // 경로로는 actor=null 을 만들 수 없다 — register 자체(코덱)가 null actor 를 다루는
        // 것은 raw SQL 로 채운 행으로 직접 확인한다(코덱이 짓지 않은 값의 복원).
        insertPendingOutboxRow(
            dataSource(),
            entryId = "raw-null-actor",
            idempotencyKey = "raw-key-1",
            payloadType = OutboxPayloadCodec.STRATEGY_UPDATED_TYPE,
            payload = OutboxPayloadCodec.encode(strategyUpdated(1)),
        )
        val boundary = TransactionBoundary(dataSource())

        val claimed = boundary.inTransaction { JdbcOutboxPort(boundary).claim(10) }

        claimed.single().actor shouldBe null
    }

    @Test
    fun `알 수 없는 payload_type 이 DB 에 있으면 claim 은 예외다 — 복원 시 fail-closed`() {
        insertPendingOutboxRow(
            dataSource(),
            entryId = "raw-bogus",
            idempotencyKey = "raw-key-2",
            payloadType = "BogusType",
        )
        val boundary = TransactionBoundary(dataSource())

        shouldThrow<IllegalStateException> {
            boundary.inTransaction { JdbcOutboxPort(boundary).claim(10) }
        }
    }

    @Test
    fun `트랜잭션 경계 밖에서 register 를 호출하면 예외다 — 우회 3 양성 대조`() {
        val boundary = TransactionBoundary(dataSource())
        val outbox = JdbcOutboxPort(boundary)
        val sink = OutboxEventSink(outbox, JdbcEventIdFactory(), FixedClock(NOW))

        shouldThrow<IllegalStateException> { sink.publish(strategyUpdated(99), ACTOR) }
    }
}
