package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * scope.md ④, 설계 검토 (4)-④ — claim 경합은 **래치로 순서를 고정한다**(sleep 금지, 설계
 * 검토 (4)-⑥). 워커 A가 claim 후 커밋 전 대기, 워커 B가 그 사이 claim → A의 행을
 * 건너뛴다(`FOR UPDATE SKIP LOCKED`). 둘째 test는 워커가 claim 트랜잭션 안에서 죽는
 * 경우(커밋 전 예외 → 롤백) 잠금이 즉시 풀려 행이 `Pending`으로 남는다는 것(claim
 * 트랜잭션이 `Claimed`를 커밋하고 끝난다는 문면과 반대편 증거).
 */
class OutboxClaimConcurrencyTest : PersistenceTestSupport() {
    @Test
    fun `두 워커가 동시에 claim 하면 같은 행이 두 번 배달되지 않는다 — SKIP LOCKED`() {
        insertPendingOutboxRow(dataSource(), entryId = "concurrent-1", idempotencyKey = "concurrent-key-1")
        val boundary = TransactionBoundary(dataSource())
        val aClaimedLatch = CountDownLatch(1)
        val bDoneLatch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        val workerA =
            Callable {
                boundary.inTransaction {
                    val claimed = JdbcOutboxPort(boundary).claim(1).map { it.entryId.value }
                    aClaimedLatch.countDown()
                    bDoneLatch.await(5, TimeUnit.SECONDS)
                    claimed
                }
            }
        val aFuture = executor.submit(workerA)
        aClaimedLatch.await(5, TimeUnit.SECONDS)

        val bClaimed = boundary.inTransaction { JdbcOutboxPort(boundary).claim(1) }
        bDoneLatch.countDown()
        val aClaimed = aFuture.get(5, TimeUnit.SECONDS)
        executor.shutdown()

        aClaimed shouldBe listOf("concurrent-1")
        bClaimed.shouldBeEmpty()
        outboxStateOf(dataSource(), "concurrent-1") shouldBe "CLAIMED"
    }

    @Test
    fun `claim 트랜잭션 안에서 워커가 죽으면(롤백) 행은 Pending 으로 남아 재수령 가능하다`() {
        insertPendingOutboxRow(dataSource(), entryId = "dies-1", idempotencyKey = "dies-key-1")
        val boundary = TransactionBoundary(dataSource())

        var threw = false
        try {
            boundary.inTransaction {
                JdbcOutboxPort(boundary).claim(1)
                error("워커 사망 시뮬레이션 — 커밋 전 예외")
            }
        } catch (expected: IllegalStateException) {
            threw = true
        }

        threw shouldBe true
        outboxStateOf(dataSource(), "dies-1") shouldBe "PENDING"
        val reclaimed = boundary.inTransaction { JdbcOutboxPort(boundary).claim(1) }
        reclaimed.map { it.entryId.value } shouldBe listOf("dies-1")
    }
}
