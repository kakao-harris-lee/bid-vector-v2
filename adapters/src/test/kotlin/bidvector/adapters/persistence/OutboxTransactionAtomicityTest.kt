package bidvector.adapters.persistence

import bidvector.adapters.event.JdbcEventIdFactory
import bidvector.adapters.event.JdbcOutboxPort
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.OutboxEventSink
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.OperatorId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val ACTOR = Actor.Operator(OperatorId("op-atomic"))
private val FIXED_NOW: Instant = Instant.parse("2026-09-10T00:00:00Z")

private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

private class ForcedFailure : RuntimeException("강제 실패 — 원자성 실측(scope.md ②)")

/**
 * scope.md ②③ — 이 slice의 존재 이유. D-4C2-1 갈래 (b): 실 도메인 write는
 * `raw_observation` append 하나뿐이다(전략 영속은 후속 slice, 3D의 다른 네 repository는
 * 이 개조에 참여하지 않는다 — 알려진 제한). [TransactionBoundary]가 `raw` append와 outbox
 * 등록을 같은 트랜잭션으로 묶는다는 것을 실패 주입·정상 커밋 양쪽으로 증명한다.
 */
class OutboxTransactionAtomicityTest : PersistenceTestSupport() {
    private fun rawObservation(second: Int) =
        RawNoticeObservation.of(
            emptyMap(),
            SourceEndpoint.NOTICE_LIST,
            Instant.parse("2026-09-10T00:00:0${second}Z"),
        )

    private fun strategyUpdated(revision: Int) =
        StrategyEvent.StrategyUpdated(StrategyRevision(revision), PolicyVersion(EffectiveFrom.Initial, "test"))

    private fun countRawObservation(): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM raw_observation").use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    private fun countOutboxByIdempotencyKey(key: String): Long =
        dataSource().connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM outbox WHERE idempotency_key = ?").use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    @Test
    fun `raw append 와 outbox 등록이 같은 트랜잭션에서 실패하면 둘 다 0행이다`() {
        val boundary = TransactionBoundary(dataSource())
        val rawStore = JdbcRawObservationStore(boundary, testFieldContracts(), TEST_RELEASE_SHA)
        val outbox = JdbcOutboxPort(boundary)
        val sink = OutboxEventSink(outbox, JdbcEventIdFactory(), FixedClock(FIXED_NOW))
        val event = strategyUpdated(21)

        shouldThrow<ForcedFailure> {
            boundary.inTransaction {
                rawStore.append(rawObservation(1))
                sink.publish(event, ACTOR)
                throw ForcedFailure()
            }
        }

        countRawObservation() shouldBe 0L
        countOutboxByIdempotencyKey("strategy-updated-21") shouldBe 0L
    }

    @Test
    fun `raw append 와 outbox 등록이 정상 커밋되면 둘 다 1행이다`() {
        val boundary = TransactionBoundary(dataSource())
        val rawStore = JdbcRawObservationStore(boundary, testFieldContracts(), TEST_RELEASE_SHA)
        val outbox = JdbcOutboxPort(boundary)
        val sink = OutboxEventSink(outbox, JdbcEventIdFactory(), FixedClock(FIXED_NOW))
        val event = strategyUpdated(22)

        boundary.inTransaction {
            rawStore.append(rawObservation(2))
            sink.publish(event, ACTOR)
        }

        countRawObservation() shouldBe 1L
        countOutboxByIdempotencyKey("strategy-updated-22") shouldBe 1L
    }

    @Test
    fun `경계 밖에서 raw append 를 호출해도 예외다 — ConnectionSource 참여자 공통 규율`() {
        val boundary = TransactionBoundary(dataSource())
        val rawStore = JdbcRawObservationStore(boundary, testFieldContracts(), TEST_RELEASE_SHA)

        shouldThrow<IllegalStateException> { rawStore.append(rawObservation(3)) }
    }

    @Test
    fun `crash-after-commit — 커밋된 행은 새 트랜잭션 경계에서도 남아 있고 claim 이 집는다`() {
        val boundary = TransactionBoundary(dataSource())
        val rawStore = JdbcRawObservationStore(boundary, testFieldContracts(), TEST_RELEASE_SHA)
        val outbox = JdbcOutboxPort(boundary)
        val sink = OutboxEventSink(outbox, JdbcEventIdFactory(), FixedClock(FIXED_NOW))
        val event = strategyUpdated(23)

        boundary.inTransaction {
            rawStore.append(rawObservation(4))
            sink.publish(event, ACTOR)
        }

        // 「재기동」의 정직한 대역(설계 검토 (3) 미달 위험 2 — 프로세스 재기동은 test가
        // 할 수 없다) — 새 커넥션(새 TransactionBoundary 인스턴스, 같은 dataSource)으로
        // 확인한다.
        countRawObservation() shouldBe 1L
        countOutboxByIdempotencyKey("strategy-updated-23") shouldBe 1L
        val restarted = TransactionBoundary(dataSource())
        val claimed = restarted.inTransaction { JdbcOutboxPort(restarted).claim(10) }
        claimed.map { it.payload } shouldBe listOf(event)
    }
}
