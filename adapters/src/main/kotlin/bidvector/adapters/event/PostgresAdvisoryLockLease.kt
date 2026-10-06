package bidvector.adapters.event

import bidvector.workflow.event.ConsumerLeasePort
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.OutboxConsumerKind
import javax.sql.DataSource

/**
 * [ConsumerLeasePort]의 PostgreSQL 세션 advisory lock 구현(D-6F10-14, ADR 0005 D-10) —
 * **자물쇠를 지키는 것과 같은 범위에 건다.** 고아 판정의 범위는 DB(「이 DB 의 `CLAIMED` 행」)
 * 이므로 자물쇠도 DB 에 있어야 한다. 파일 잠금(`RunStateLock` 선례)은 프로세스·호스트 범위라
 * 다른 호스트의 두 소비자가 **둘 다 Held** 를 받아 서로의 in-flight 행을 격리한다.
 *
 * **`DataSource` 를 직접 받는다** — `ConnectionSource` 로는 안 된다: advisory lock 의 세션
 * 범위는 **연결이 살아 있는 동안**이고, `OwnTransactionConnectionSource` 는 호출마다 연결을
 * 닫고 `TransactionBoundary` 는 트랜잭션 경계 안에서만 연결을 준다. 이 클래스는 본문이 도는
 * 내내 전용 연결 하나를 쥔다 — 그 연결이 끊기면(프로세스 사망) 서버가 잠금을 **즉시**
 * 놓는다(D-10 ② 「TTL 이 아니다」).
 *
 * **알려진 제한 — TCP 반개방.** 프로세스가 죽어도 커널이 FIN 을 보내지 못한 경우(전원 차단·
 * 네트워크 분단) 서버는 연결이 끊긴 것을 TCP keepalive 만료까지 모른다. 그 구간에서는 다음
 * 소비자가 `Busy` 를 받아 **아무것도 하지 않는다** — 안전한 쪽으로 실패한다(놓침은 at-most-once
 * 가 이미 감수한다, `OPEN-NOTI-02`). 반대쪽(살아 있는 홀더의 잠금을 빼앗는 것)은 일어나지 않는다.
 *
 * `pg_try_advisory_lock`(블로킹하지 않는 쪽)을 쓴다 — 대기하면 일회 러너가 cron 주기를 넘겨
 * 겹친다. 못 쥐면 [LeaseAttempt.Busy]이고 본문은 **부르지 않는다**.
 */
class PostgresAdvisoryLockLease(
    private val dataSource: DataSource,
) : ConsumerLeasePort {
    override fun <T> withLease(
        kind: OutboxConsumerKind,
        body: () -> T,
    ): LeaseAttempt<T> =
        dataSource.connection.use { connection ->
            val key = lockKeyFor(kind)
            val acquired =
                connection.prepareStatement(TRY_ADVISORY_LOCK).use { statement ->
                    statement.setLong(1, key)
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "pg_try_advisory_lock 이 행을 돌려주지 않았다" }
                        rs.getBoolean(1)
                    }
                }
            if (!acquired) return@use LeaseAttempt.Busy
            try {
                LeaseAttempt.Held(body())
            } finally {
                // 연결을 닫기만 해도 세션 잠금은 풀린다 — 명시 해제는 「같은 연결을 재사용하는
                // pool 이 잠금을 들고 돌아가는」 경우를 막는다(HikariCP 는 연결을 pool 로
                // 되돌린다. `use` 의 close 는 반납이지 종료가 아니다).
                connection.prepareStatement(ADVISORY_UNLOCK).use { statement ->
                    statement.setLong(1, key)
                    statement.execute()
                }
            }
        }
}

/**
 * kind 하나가 곧 자물쇠 하나다(D-6F10-13 「lease 키도 kind 단위」) — 다른 종류의 소비자는
 * 서로를 막지 않는다.
 *
 * 키는 **열거**다. `name.hashCode()` 같은 도출을 쓰지 않는다: 해시는 JVM·문자열 변경에 따라
 * 조용히 달라져 「같은 kind 인데 다른 자물쇠」를 만들 수 있고, 그것이 바로 이 lease 가 막아야
 * 하는 상태다. 새 kind 가 생기면 소진 `when` 이 컴파일을 깨 값을 고르게 한다.
 */
private fun lockKeyFor(kind: OutboxConsumerKind): Long =
    when (kind) {
        OutboxConsumerKind.NotificationRequested -> NOTIFICATION_RELAY_LOCK_KEY
        OutboxConsumerKind.StrategyUpdated -> STRATEGY_EVENT_RELAY_LOCK_KEY
    }

private const val NOTIFICATION_RELAY_LOCK_KEY = 6_110_001L
private const val STRATEGY_EVENT_RELAY_LOCK_KEY = 6_110_002L

private const val TRY_ADVISORY_LOCK = "SELECT pg_try_advisory_lock(?)"
private const val ADVISORY_UNLOCK = "SELECT pg_advisory_unlock(?)"
