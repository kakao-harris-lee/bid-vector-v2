package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.event.OutboxConsumerKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 집음 대기 — 다른 워커가 claim 을 끝내는 것을 기다리는 시한. */
private const val CLAIM_TIMEOUT_SECONDS = 5L

/** 쥠 대기 — 쥔 워커가 풀리기를 기다리는 시한(claim 보다 넉넉해야 교차 대기가 시한에 걸리지 않는다). */
private const val HOLD_TIMEOUT_SECONDS = 30L

/** 합류 대기 — 워커 future 를 거두는 시한. */
private const val JOIN_TIMEOUT_SECONDS = 60L

private val KIND = OutboxConsumerKind.StrategyUpdated

/**
 * claim 경합은 **래치로 순서를 고정한다**(sleep 금지). 워커 A 가 claim 후 커밋 전 대기,
 * 워커 B 가 그 사이 claim → A 의 행을 건너뛴다(`FOR UPDATE SKIP LOCKED`). 둘째 test 는
 * 워커가 claim 트랜잭션 안에서 죽는 경우(커밋 전 예외 → 롤백) 잠금이 즉시 풀려 행이
 * `Pending` 으로 남는다는 것(claim 트랜잭션이 `Claimed` 를 커밋하고 끝난다는 문면의 반대편
 * 증거).
 *
 * **D-6F10-6 — 래치 반환값을 단언한다.** 앞 판은 두 `await(5s)` 의 반환값을 버렸다(6D-1
 * verifier 실측): production 에서 `SKIP LOCKED` 를 빼면 B 가 A 의 행 잠금에 막혀 시한까지
 * 기다렸다가 **빈손으로** 돌아오는데, `bClaimed.shouldBeEmpty()` 는 그 빈손도 통과시킨다 —
 * 「건너뛰었다」와 「막혀서 못 집었다」가 같은 값이 된다. `await` 가 `false` 를 돌려주면
 * 그것은 **시한 초과**이고, 그 사실을 단언하지 않으면 test 가 공허하게 초록이다. 그래서 래치
 * 대기의 반환값을 전부 `shouldBe true` 로 잠근다 — production `SKIP LOCKED` 제거와 claim
 * 순차화 둘 다 RED 가 된다.
 *
 * **D-6F10-13 — [KIND] 를 명시한다.** 헬퍼가 심는 행의 `payload_type` 은 `StrategyUpdated`
 * 이므로 그 종류로 집는다. 앞 두 test 가 재는 것은 종류가 아니라 **같은 종류 안의 경합**이고,
 * 마지막 test 가 종류 사이의 격리를 잰다.
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
                    val claimed = JdbcOutboxPort(boundary).claim(1, KIND).map { it.entryId.value }
                    aClaimedLatch.countDown()
                    // 반환값을 올려 단언한다 — 여기서 시한이 넘으면 A 는 B 가 끝나기 전에
                    // 커밋해 버리고, 그러면 B 의 빈손이 SKIP LOCKED 의 증거가 아니게 된다.
                    ClaimUnderHold(claimed, bDoneLatch.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                }
            }
        val aFuture = executor.submit(workerA)
        val aClaimedInTime = aClaimedLatch.await(CLAIM_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val bClaimed = boundary.inTransaction { JdbcOutboxPort(boundary).claim(1, KIND) }
        bDoneLatch.countDown()
        val aResult = aFuture.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        executor.shutdown()

        // 래치 둘의 반환값 — 「기다림이 시한으로 끝났다」를 초록으로 흡수하지 않는다.
        aClaimedInTime shouldBe true
        aResult.heldUntilBDone shouldBe true
        aResult.entryIds shouldBe listOf("concurrent-1")
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
                JdbcOutboxPort(boundary).claim(1, KIND)
                error("워커 사망 시뮬레이션 — 커밋 전 예외")
            }
        } catch (expected: IllegalStateException) {
            threw = true
        }

        threw shouldBe true
        outboxStateOf(dataSource(), "dies-1") shouldBe "PENDING"
        val reclaimed = boundary.inTransaction { JdbcOutboxPort(boundary).claim(1, KIND) }
        reclaimed.map { it.entryId.value } shouldBe listOf("dies-1")
    }

    /**
     * D-6F10-13 — 종류가 다른 행은 **서로를 집지 않는다.** 한 종류의 소비자가 다른 종류의
     * 행을 집으면 그 행은 전이표상 격리밖에 갈 곳이 없다(소비자 없는 종류를 종단으로 태운다).
     */
    @Test
    fun `claim 은 자기 종류의 행만 집고 다른 종류는 PENDING 에 남긴다`() {
        insertPendingOutboxRow(dataSource(), entryId = "kind-strategy", idempotencyKey = "kind-key-1")
        insertPendingOutboxRow(
            dataSource(),
            entryId = "kind-notification",
            idempotencyKey = "kind-key-2",
            payloadType = OutboxPayloadCodec.NOTIFICATION_REQUESTED_TYPE,
            payload = OutboxPayloadCodec.encode(notificationRequestedFixture()),
        )
        val boundary = TransactionBoundary(dataSource())

        val strategyClaimed =
            boundary.inTransaction {
                JdbcOutboxPort(boundary).claim(10, OutboxConsumerKind.StrategyUpdated)
            }

        strategyClaimed.map { it.entryId.value } shouldBe listOf("kind-strategy")
        outboxStateOf(dataSource(), "kind-notification") shouldBe "PENDING"
    }
}

/** 워커 A 가 돌려주는 두 사실 — 집은 것과 **쥐고 기다리는 데 성공했는지**. */
private data class ClaimUnderHold(
    val entryIds: List<String>,
    val heldUntilBDone: Boolean,
)
