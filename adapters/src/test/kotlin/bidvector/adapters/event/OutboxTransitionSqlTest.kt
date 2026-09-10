package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 설계 검토 (3) 미달 위험 1 — `OutboxPort.markDelivered`/`markFailed`/`markIsolated`는 이
 * slice에서 production 호출부가 없고(`OPEN-4C2-MARK-UNEXERCISED`), `adapters` test도
 * [bidvector.workflow.event.OutboxTransition]의 하위 타입을 지을 수 없어(`internal
 * constructor`) 그 port 메서드를 부르는 test를 쓸 수 없다. 그래서 이 test는 production과
 * **같은 [EventSql] 상수**(사본 아님)를 직접 실행해 전이 UPDATE의 효과와 `WHERE state`
 * 거부(잘못된 이전 상태에서는 영향 행 0, 상태 불변)를 잰다.
 */
class OutboxTransitionSqlTest : PersistenceTestSupport() {
    private fun executeUpdate(
        sql: String,
        entryId: String,
    ): Int =
        dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, entryId)
                statement.executeUpdate()
            }
        }

    @Test
    fun `MARK_CLAIMED 는 Pending 행만 옮기고 영향 행 1이다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-claim-1", idempotencyKey = "t-claim-key-1")

        val affected = executeUpdate(EventSql.MARK_CLAIMED, "t-claim-1")

        affected shouldBe 1
        outboxStateOf(dataSource(), "t-claim-1") shouldBe "CLAIMED"
    }

    @Test
    fun `MARK_CLAIMED 는 이미 Claimed 인 행을 다시 옮기지 않는다 — WHERE state 거부`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-claim-2", idempotencyKey = "t-claim-key-2")
        executeUpdate(EventSql.MARK_CLAIMED, "t-claim-2")

        val affected = executeUpdate(EventSql.MARK_CLAIMED, "t-claim-2")

        affected shouldBe 0
        outboxStateOf(dataSource(), "t-claim-2") shouldBe "CLAIMED"
    }

    @Test
    fun `MARK_DELIVERED 는 Claimed 행만 옮기고 영향 행 1이다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-delivered-1", idempotencyKey = "t-delivered-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-delivered-1")

        val affected = executeUpdate(EventSql.MARK_DELIVERED, "t-delivered-1")

        affected shouldBe 1
        outboxStateOf(dataSource(), "t-delivered-1") shouldBe "DELIVERED"
    }

    @Test
    fun `MARK_DELIVERED 는 Pending 행을 옮기지 않는다 — 종단 전이표 밖 거부`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-delivered-2", idempotencyKey = "t-delivered-key-2")

        val affected = executeUpdate(EventSql.MARK_DELIVERED, "t-delivered-2")

        affected shouldBe 0
        outboxStateOf(dataSource(), "t-delivered-2") shouldBe "PENDING"
    }

    @Test
    fun `MARK_FAILED 는 Claimed 행만 옮기고, Delivered 로 옮긴 뒤에는 다시 옮기지 않는다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-failed-1", idempotencyKey = "t-failed-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-failed-1")
        executeUpdate(EventSql.MARK_DELIVERED, "t-failed-1")

        val affected = executeUpdate(EventSql.MARK_FAILED, "t-failed-1")

        affected shouldBe 0
        outboxStateOf(dataSource(), "t-failed-1") shouldBe "DELIVERED"
    }

    @Test
    fun `MARK_ISOLATED 는 Claimed 행만 옮기고 영향 행 1이다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-isolated-1", idempotencyKey = "t-isolated-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-isolated-1")

        val affected = executeUpdate(EventSql.MARK_ISOLATED, "t-isolated-1")

        affected shouldBe 1
        outboxStateOf(dataSource(), "t-isolated-1") shouldBe "ISOLATED"
    }

    @Test
    fun `Isolated 에서는 어느 전이도 다시 나가지 않는다 — 종단 재실행 없음`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-isolated-2", idempotencyKey = "t-isolated-key-2")
        executeUpdate(EventSql.MARK_CLAIMED, "t-isolated-2")
        executeUpdate(EventSql.MARK_ISOLATED, "t-isolated-2")

        executeUpdate(EventSql.MARK_DELIVERED, "t-isolated-2") shouldBe 0
        executeUpdate(EventSql.MARK_FAILED, "t-isolated-2") shouldBe 0
        outboxStateOf(dataSource(), "t-isolated-2") shouldBe "ISOLATED"
    }
}
