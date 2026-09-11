package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.event.decideInbox
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * scope.md ⑤⑥ — consumer inbox의 영속과 dedup을 실 DB 위에서 잰다. 4C-1이 `decideInbox`
 * (순수 판정)로 이미 증명한 것을 이 slice는 저장 층에서 잰다 — `inbox` PK 유일성이
 * `INSERT ... ON CONFLICT DO NOTHING`으로 같은 키의 이중 적재를 행 1개로 수렴시킨다.
 */
class JdbcInboxPortTest : PersistenceTestSupport() {
    private fun inboxPort(boundary: TransactionBoundary) = JdbcInboxPort(boundary)

    private fun countInboxRows(key: IdempotencyKey): Long =
        dataSource().connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM inbox WHERE idempotency_key = ?").use { statement ->
                statement.setString(1, key.value)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    @Test
    fun `markProcessed 전에는 hasProcessed 가 false 다`() {
        val boundary = TransactionBoundary(dataSource())
        val key = IdempotencyKey("inbox-key-1")

        val processed = boundary.inTransaction { inboxPort(boundary).hasProcessed(key) }

        processed shouldBe false
    }

    @Test
    fun `markProcessed 뒤에는 hasProcessed 가 true 이고 행이 1개다`() {
        val boundary = TransactionBoundary(dataSource())
        val key = IdempotencyKey("inbox-key-2")

        boundary.inTransaction { inboxPort(boundary).markProcessed(key) }

        boundary.inTransaction { inboxPort(boundary).hasProcessed(key) } shouldBe true
        countInboxRows(key) shouldBe 1L
    }

    @Test
    fun `같은 키를 두 번 markProcessed 해도 행은 1개로 수렴한다 — dedup`() {
        val boundary = TransactionBoundary(dataSource())
        val key = IdempotencyKey("inbox-key-3")

        boundary.inTransaction { inboxPort(boundary).markProcessed(key) }
        boundary.inTransaction { inboxPort(boundary).markProcessed(key) }

        countInboxRows(key) shouldBe 1L
    }

    @Test
    fun `duplicate·out-of-order 수신은 decideInbox 판정과 함께 저장 층에서도 수렴한다`() {
        val boundary = TransactionBoundary(dataSource())
        val key = IdempotencyKey("inbox-key-4")
        val port = inboxPort(boundary)

        // 1차 수신 — 처리 전이라 Process.
        val firstDecision = boundary.inTransaction { decideInbox(port.hasProcessed(key)) }
        boundary.inTransaction { port.markProcessed(key) }

        // 2차 수신(재전달·순서 뒤바뀜 모두 이 축에서는 같은 키로 도착) — SkipDuplicate.
        val secondDecision = boundary.inTransaction { decideInbox(port.hasProcessed(key)) }

        firstDecision shouldBe bidvector.workflow.event.InboxDecision.Process
        secondDecision shouldBe bidvector.workflow.event.InboxDecision.SkipDuplicate
        countInboxRows(key) shouldBe 1L
    }
}
