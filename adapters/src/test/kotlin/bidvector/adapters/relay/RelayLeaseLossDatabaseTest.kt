package bidvector.adapters.relay

import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.ConsumerTransactionPort
import bidvector.workflow.notification.RelayReport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import javax.sql.DataSource

/** `NotificationRequested` 종류의 임대 키(`PostgresAdvisoryLockLease` 의 열거값과 같은 값). */
private const val NOTIFICATION_LEASE_KEY = 6_110_001L

/**
 * **R1-M-1 의 답** — 임대 **연결만** 끊겼을 때(프로세스는 살아 있다) relay 가 그것을 알고 멈추는지
 * 실 DB 로 잰다. verifier probe V6b 를 상설 test 로 올린 것이다.
 *
 * 왜 이것이 문제였나: advisory lock 은 홀더의 연결이 끊기면 서버가 **즉시** 놓는다. 그 해제는
 * 프로세스 사망뿐 아니라 `pg_terminate_backend`·네트워크 단절로도 일어난다. 앞 판의 relay 는
 * 본문 중 임대를 다시 묻지 않아, 그 사이 다음 relay 가 임대를 쥐고 첫째의 in-flight `CLAIMED` 를
 * 고아로 읽어 격리했다 — 중복 발송은 0 이지만 **발송된 행이 `ISOLATED` 로 표기**되고 첫째
 * 배치의 미발송 행은 놓치며, 첫째의 실패 원인이 해제 시점 예외로 가려졌다. 위협 모델 ⑦ 과
 * 알려진 제한 11 의 문면이 그래서 어긋나 있었다.
 *
 * 지금 재는 것: 임대가 끊기면 relay 가 **남은 행을 건드리지 않고** `LeaseLost` 를 낸다. 이미
 * 발송한 행은 종단으로 가고, 남은 행은 `CLAIMED` 에 남아 다음 run 의 고아 격리가 받는다(놓침은
 * at-most-once 가 감수하는 것이다).
 */
class RelayLeaseLossDatabaseTest : PersistenceTestSupport() {
    @Test
    fun `임대 연결이 끊기면 남은 행을 건드리지 않고 LeaseLost 로 멈춘다`() {
        insertPendingNotificationRow(dataSource(), "lease-a")
        insertPendingNotificationRow(dataSource(), "lease-b")

        lateinit var harness: RelayHarness
        harness =
            RelayHarness(dataSource()) { boundary ->
                // 첫 행의 발송이 끝난 **직후**(그 행의 T2 경계에서) 임대 연결을 서버 쪽에서
                // 끊는다 — 둘째 행의 임대 재확인이 거짓을 받게 되는 유일한 창이다.
                TerminateLeaseAfterFirstSend(ConsumerTransactions(boundary), dataSource()) {
                    harness.sender.sentKeys().size
                }
            }

        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.claimed shouldBe 2
        report.partial.delivered shouldBe 1
        harness.sender.sentKeys().size shouldBe 1
        // 발송한 행은 종단으로, 남은 행은 CLAIMED 로.
        outboxStateCounts(dataSource()) shouldBe mapOf("DELIVERED" to 1, "CLAIMED" to 1)
    }

    /**
     * 임대를 **잃지 않은** run 의 대조 — 같은 harness 로 두 행을 끝까지 돈다. 이 대조가 없으면
     * 위 test 가 「두 행을 못 돌리는 구현」에서도 초록이다.
     */
    @Test
    fun `임대가 유지되면 두 행을 끝까지 돈다 — 음성 대조`() {
        insertPendingNotificationRow(dataSource(), "intact-a")
        insertPendingNotificationRow(dataSource(), "intact-b")

        val harness = RelayHarness(dataSource())
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.claimed shouldBe 2
        report.delivered shouldBe 2
        outboxStateCounts(dataSource()) shouldBe mapOf("DELIVERED" to 2)
    }
}

/**
 * [sentCount] 가 1 이 되는 **첫 경계 호출**에서 임대 백엔드를 끊는다 — 그 다음 행의 임대
 * 재확인이 거짓을 받는다. 순번이 아니라 **사건**에 거는 것은 `CrashAfterDispatch` 와 같은 이유다.
 */
private class TerminateLeaseAfterFirstSend(
    private val delegate: ConsumerTransactionPort,
    private val dataSource: DataSource,
    private val sentCount: () -> Int,
) : ConsumerTransactionPort {
    private var terminated = false

    override fun <T> inTransaction(block: () -> T): T {
        if (!terminated && sentCount() == 1) {
            terminated = terminateLeaseBackend(dataSource)
        }
        return delegate.inTransaction(block)
    }
}

/**
 * 알림 종류의 advisory lock 을 쥔 백엔드를 끊는다. `pg_locks` 에서 그 잠금을 찾는다 — bigint
 * 키는 `classid`(상위 32비트)·`objid`(하위 32비트)로 나뉘고 이 키는 2^32 미만이라 `classid` 가
 * 0 이다.
 */
private fun terminateLeaseBackend(dataSource: DataSource): Boolean =
    dataSource.connection.use { connection ->
        connection
            .prepareStatement(
                "SELECT pg_terminate_backend(pid) FROM pg_locks " +
                    "WHERE locktype = 'advisory' AND classid = 0 AND objid = ? AND objsubid = 1",
            ).use { statement ->
                statement.setLong(1, NOTIFICATION_LEASE_KEY)
                statement.executeQuery().use { it.next() }
            }
    }
