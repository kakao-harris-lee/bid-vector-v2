package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.OutboxConsumerKind
import bidvector.workflow.event.OutboxEventSink
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.OperatorId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
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
        val claimed = boundary.inTransaction { outbox.claim(10, OutboxConsumerKind.StrategyUpdated) }

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
        val claimed = boundary.inTransaction { outbox.claim(10, OutboxConsumerKind.StrategyUpdated) }

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

        val claimed =
            boundary.inTransaction {
                JdbcOutboxPort(boundary).claim(10, OutboxConsumerKind.StrategyUpdated)
            }

        claimed.single().actor shouldBe null
    }

    /**
     * D-6F10-13 의 귀결 — `claim` 이 `payload_type` 으로 좁혀진 뒤로는 **어휘 밖 payload_type
     * 행이 어느 kind 로도 집히지 않는다.** 앞 판은 이 행이 claim 에서 복원 예외를 내는 것을
     * 쟀는데(좁히지 않은 claim 이 전부를 집었으므로), 지금은 질의가 그 행을 보지 않는다 —
     * 「모르면 건너뛴다」가 아니라 **소비 대상이 아니다**(어느 소비자의 종류도 아니므로).
     * 그 행은 `PENDING` 에 그대로 남는다(종단으로 태우지 않는다 — 되돌릴 간선이 없다).
     */
    @Test
    fun `어휘 밖 payload_type 행은 어느 kind 로도 claim 되지 않고 PENDING 에 남는다`() {
        insertPendingOutboxRow(
            dataSource(),
            entryId = "raw-bogus",
            idempotencyKey = "raw-key-2",
            payloadType = "BogusType",
        )
        val boundary = TransactionBoundary(dataSource())

        OutboxConsumerKind.entries.forEach { kind ->
            boundary.inTransaction { JdbcOutboxPort(boundary).claim(10, kind) }.shouldBeEmpty()
        }

        outboxStateOf(dataSource(), "raw-bogus") shouldBe "PENDING"
    }

    /**
     * 복원 fail-closed 는 그대로 선다 — `payload_type` 은 어휘 안인데 **본문이 그 형식이
     * 아닌** 행이 그 자리다(필드 수 고정 `check`). 어휘 밖 타입(위 test)과 다른 사실이다:
     * 저쪽은 집히지 않고, 이쪽은 집혀서 복원이 거부한다.
     */
    @Test
    fun `payload 본문이 형식을 어기면 claim 은 예외다 — 복원 시 fail-closed`() {
        insertPendingOutboxRow(
            dataSource(),
            entryId = "raw-malformed",
            idempotencyKey = "raw-key-3",
            payloadType = OutboxPayloadCodec.STRATEGY_UPDATED_TYPE,
            payload = "only-one-field",
        )
        val boundary = TransactionBoundary(dataSource())

        shouldThrow<IllegalStateException> {
            boundary.inTransaction { JdbcOutboxPort(boundary).claim(10, OutboxConsumerKind.StrategyUpdated) }
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
