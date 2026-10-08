package bidvector.adapters.relay

import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.event.NOTIFICATION_RELAY_LOCK_KEY
import bidvector.adapters.event.PostgresAdvisoryLockLease
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.ConsumerLeasePort
import bidvector.workflow.event.ConsumerTransactionPort
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.LeaseGuard
import bidvector.workflow.event.OutboxConsumerKind
import bidvector.workflow.notification.RelayReport
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import javax.sql.DataSource

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
        insertPendingNotificationRow(pooledDataSource(), "lease-a")
        insertPendingNotificationRow(pooledDataSource(), "lease-b")

        lateinit var harness: RelayHarness
        harness =
            RelayHarness(
                pooledDataSource(),
                // 첫 행의 발송이 끝난 **직후**(그 행의 T2 경계에서) 임대 연결을 서버 쪽에서
                // 끊는다 — 둘째 행의 임대 재확인이 거짓을 받게 되는 유일한 창이다.
                transactionsFor = { boundary ->
                    TerminateLeaseAfterFirstSend(ConsumerTransactions(boundary), pooledDataSource()) {
                        harness.sender.sentKeys().size
                    }
                },
            )

        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.claimed shouldBe 2
        report.partial.delivered shouldBe 1
        harness.sender.sentKeys().size shouldBe 1
        // 발송한 행은 종단으로, 남은 행은 CLAIMED 로.
        outboxStateCounts(pooledDataSource()) shouldBe mapOf("DELIVERED" to 1, "CLAIMED" to 1)
    }

    /**
     * **probe V6c 를 상설로**(D-6F10-31 ②) — 임대를 **획득 직후**에 잃으면 고아 격리도 claim 도
     * 하지 않는다.
     *
     * 앞 판이 여기서 무엇을 했나(verifier r2 실측): 재확인이 행 루프 안에만 있어, 이미 임대를
     * 잃은 relay 가 `CLAIMED` 행을 고아로 읽어 **영구 격리**했고, 집을 행이 0 이면 루프가 돌지
     * 않아 guard 가 한 번도 불리지 않고 `Completed` + 종료 코드 **0** 이 났다. 그 행이 새 홀더의
     * in-flight 였다면 발송된 행이 `ISOLATED` 로 표기된다 — 되돌릴 간선이 없는 종단이다.
     *
     * 입력에 고아(`CLAIMED`)와 집을 행(`PENDING`) 을 **둘 다** 둔다. 둘 중 하나라도 움직이면
     * 그것이 곧 결함이므로, 상태 분포가 **전후로 같다**가 이 test 의 단언이다.
     *
     * 종료 코드 1 은 여기서 재지 않는다 — `adapters` 는 `app` 에 의존하지 않는다(의존 방향).
     * `LeaseLost` → 1 의 사상은 `RelayExitCodeTest` 가 잠근다.
     */
    @Test
    fun `임대를 획득 직후에 잃으면 격리도 claim 도 하지 않는다`() {
        insertPendingNotificationRow(pooledDataSource(), "v6c-orphan")
        forceOutboxState(pooledDataSource(), "v6c-orphan", "CLAIMED")
        insertPendingNotificationRow(pooledDataSource(), "v6c-pending")
        val before = outboxStateCounts(pooledDataSource())

        val harness =
            RelayHarness(
                pooledDataSource(),
                leasesFor = { source -> TerminateLeaseOnAcquire(PostgresAdvisoryLockLease(source), source) },
            )

        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.orphansIsolated shouldBe 0
        report.partial.claimed shouldBe 0
        report.partial.delivered shouldBe 0
        harness.sender.sentKeys().shouldBeEmpty()
        outboxStateCounts(pooledDataSource()) shouldBe before
    }

    /**
     * **cr T-1 의 회귀 test** — 고아를 태우는 **도중**에 임대 연결이 끊기면 격리가 멈추고 남은
     * `CLAIMED` 가 보존된다.
     *
     * 왜 실 DB 가 필요한가: fake 쪽은 「guard 가 거짓을 답하면 멈춘다」를 재고, 여기서는 **서버가
     * 실제로 잠금을 놓은 뒤** 그 다음 질의가 어떻게 되는지를 잰다. 끊기면 `stillHolding` 의
     * `SELECT 1` 이 던지고 `PostgresAdvisoryLockLease` 가 그것을 `false` 로 값화한다 — 그 사슬이
     * 끊어지면(예: 예외를 올리면) 이 test 는 `LeaseLost` 대신 예외를 본다.
     *
     * 끊는 시점은 **순번이 아니라 사건**이다(`CrashAfterDispatch` 와 같은 이유) — 「`ISOLATED`
     * 행이 하나 생겼다」가 조건이라, 경계 호출 수가 바뀌어도 이 test 는 같은 자리를 잡는다.
     */
    @Test
    fun `고아를 태우는 도중 임대가 끊기면 격리가 멈추고 남은 CLAIMED 가 보존된다`() {
        insertPendingNotificationRow(pooledDataSource(), "burn-1")
        forceOutboxState(pooledDataSource(), "burn-1", "CLAIMED")
        insertPendingNotificationRow(pooledDataSource(), "burn-2")
        forceOutboxState(pooledDataSource(), "burn-2", "CLAIMED")

        val harness =
            RelayHarness(
                pooledDataSource(),
                transactionsFor = { boundary ->
                    TerminateLeaseAfterFirstIsolation(ConsumerTransactions(boundary), pooledDataSource())
                },
            )

        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.LeaseLost>()

        report.partial.orphansIsolated shouldBe 1
        report.partial.claimed shouldBe 0
        harness.sender.sentKeys().shouldBeEmpty()
        // 하나만 태워지고 하나는 `CLAIMED` 에 남았다 — 다음 run 의 고아 격리가 받는다.
        outboxStateCounts(pooledDataSource()) shouldBe mapOf("ISOLATED" to 1, "CLAIMED" to 1)
    }

    /**
     * 임대를 **잃지 않은** run 의 대조 — 같은 harness 로 두 행을 끝까지 돈다. 이 대조가 없으면
     * 위 test 가 「두 행을 못 돌리는 구현」에서도 초록이다.
     */
    @Test
    fun `임대가 유지되면 두 행을 끝까지 돈다 — 음성 대조`() {
        insertPendingNotificationRow(pooledDataSource(), "intact-a")
        insertPendingNotificationRow(pooledDataSource(), "intact-b")

        val harness = RelayHarness(pooledDataSource())
        val report = harness.relay.relay(RELAY_DB_LIMIT).shouldBeInstanceOf<RelayReport.Completed>()

        report.claimed shouldBe 2
        report.delivered shouldBe 2
        outboxStateCounts(pooledDataSource()) shouldBe mapOf("DELIVERED" to 2)
    }
}

/**
 * `ISOLATED` 행이 **하나 생긴** 뒤 첫 경계 호출에서 임대 백엔드를 끊는다 — 그 다음 고아의
 * 격리 전 임대 확인이 거짓을 받는다(cr T-1).
 *
 * 경계 **뒤**에 끊는다(`delegate` 를 먼저 부른다) — 앞에서 끊으면 그 격리 자체가 끊긴 연결로
 * 들어간다. 조건이 순번이 아니라 상태이므로 경계 호출 수가 바뀌어도 자리가 밀리지 않는다.
 */
private class TerminateLeaseAfterFirstIsolation(
    private val delegate: ConsumerTransactionPort,
    private val dataSource: DataSource,
) : ConsumerTransactionPort {
    private var terminated = false

    override fun <T> inTransaction(block: () -> T): T {
        val result = delegate.inTransaction(block)
        if (!terminated && outboxStateCounts(dataSource)["ISOLATED"] == 1) {
            terminated = terminateLeaseBackend(dataSource)
        }
        return result
    }
}

/**
 * 임대를 **쥔 직후, 본문을 부르기 전에** 끊는다 — relay 의 첫 `stillHeld()` 가 거짓을 받는
 * 유일한 창이다. `ConsumerTransactionPort` 로는 이 지점에 걸 수 없다(첫 guard 확인이 어떤
 * 트랜잭션보다 앞이다) — 그것이 이 데코레이터가 필요한 이유다.
 *
 * 임대 자체는 **production 구현**이다. 바꿔치우는 것은 「언제 끊기는가」뿐이고, 끊긴 뒤의
 * 판정(`SELECT 1` 이 던지고 `stillHolding` 이 false 를 낸다)은 production 경로를 그대로 탄다.
 */
private class TerminateLeaseOnAcquire(
    private val delegate: ConsumerLeasePort,
    private val dataSource: DataSource,
) : ConsumerLeasePort {
    override fun <T> withLease(
        kind: OutboxConsumerKind,
        body: (LeaseGuard) -> T,
    ): LeaseAttempt<T> =
        delegate.withLease(kind) { guard ->
            check(terminateLeaseBackend(dataSource)) { "임대 백엔드를 `pg_locks` 에서 찾지 못했다" }
            body(guard)
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
                statement.setLong(1, NOTIFICATION_RELAY_LOCK_KEY)
                statement.executeQuery().use { it.next() }
            }
    }
