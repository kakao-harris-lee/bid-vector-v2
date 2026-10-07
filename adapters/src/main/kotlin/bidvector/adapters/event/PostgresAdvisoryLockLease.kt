package bidvector.adapters.event

import bidvector.workflow.event.ConsumerLeasePort
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.LeaseGuard
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
 * 가 이미 감수한다, `OPEN-NOTI-02`).
 *
 * **알려진 제한 — 일하는 중에 잠금을 잃는다(R2-L-4 정정).** 「살아 있는 홀더의 잠금은 빼앗기지
 * 않는다」고 적었던 것은 **프로세스**와 **연결**을 섞은 것이었다. 서버가 잠금을 놓는 조건은
 * 연결 단절이고, 연결은 프로세스와 무관하게 끊긴다(`pg_terminate_backend`·pooler 회수·
 * 네트워크 재설정). 그러면 **본문이 아직 도는 중인** 홀더가 잠금을 잃고 다음 소비자가 `Held`
 * 를 받는다 — 그 교차가 [stillHolding] 을 둔 이유이고, 호출부는 그것을 [LeaseGuard] 로 네
 * 지점에서 되묻는다.
 *
 * `pg_try_advisory_lock`(블로킹하지 않는 쪽)을 쓴다 — 대기하면 일회 러너가 cron 주기를 넘겨
 * 겹친다. 못 쥐면 [LeaseAttempt.Busy]이고 본문은 **부르지 않는다**.
 */
class PostgresAdvisoryLockLease(
    private val dataSource: DataSource,
) : ConsumerLeasePort {
    override fun <T> withLease(
        kind: OutboxConsumerKind,
        body: (LeaseGuard) -> T,
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
            heldOrRelease(connection, key) { LeaseAttempt.Held(body(LeaseGuard { stillHolding(connection, key) })) }
        }

    /**
     * 본문 예외와 해제 예외가 **겹칠 때 본문 쪽을 남긴다**(cr L-6) — 해제가 던져 본문 예외를
     * 대체하면 러너의 정제된 원인 코드가 엉뚱해진다.
     *
     * 해제 실패를 **삼킨다**. `runCatching` 을 쓰지 않는 것은 그것이 무엇이든 삼키기
     * 때문이고(PR #62 review J), 좁은 `SQLException` 하나만 잡는다. cr L-6 은 본문 예외에
     * `addSuppressed` 로 붙이는 쪽을 제안했는데, 그러려면 본문 예외를 손에 쥐어야 하고 그것은
     * `Throwable` 을 잡는 것이다(detekt `TooGenericExceptionCaught`) — 그래서 **잃는 것을
     * 밝히고** 삼킨다: 잃는 것은 해제 실패의 **상세**이고, 잃지 않는 것은 본문의 결과·예외다.
     *
     * 삼켜도 되는 근거: `pg_advisory_unlock` 은 쥐지 않은 잠금에 예외가 아니라 `false` 를
     * 낸다. 그러므로 여기서 던지는 경우는 **연결이 이미 끊긴 것**뿐이고, 그때 잠금은 서버가
     * 이미 놓았으므로 보고할 사실이 없다 — 그리고 그 상황 자체는 relay 가 `LeaseLost` 로
     * 돌려준다(R1-M-1). 던지면 그 **결과를 예외가 덮는다**(test 로 실측).
     */
    private fun <T> heldOrRelease(
        connection: java.sql.Connection,
        key: Long,
        body: () -> T,
    ): T =
        try {
            body()
        } finally {
            releaseQuietly(connection, key)
        }
}

/**
 * 잠금을 놓는다 — 상세를 잃는 판단의 근거는 [PostgresAdvisoryLockLease] 의 `heldOrRelease` KDoc 이다.
 */
private fun releaseQuietly(
    connection: java.sql.Connection,
    key: Long,
) = try {
    // 연결을 닫기만 해도 세션 잠금은 풀린다 — 명시 해제는 「같은 연결을 재사용하는 pool 이
    // 잠금을 들고 돌아가는」 경우를 막는다(`use` 의 close 는 반납이지 종료가 아니다).
    connection.prepareStatement(ADVISORY_UNLOCK).use { statement ->
        statement.setLong(1, key)
        statement.execute()
    }
} catch (
    @Suppress("SwallowedException") release: java.sql.SQLException,
) {
    // 상세를 잃는다 — 그 판단의 근거는 `heldOrRelease` KDoc 이다.
}

/**
 * 임대를 **아직** 쥐고 있는가 — `pg_locks` 에서 **자기 backend 가 그 키의 advisory 잠금을
 * 들고 있는지** 직접 본다(PR #63 finding 3).
 *
 * 앞 판은 `SELECT 1` 로 **연결 생존**만 재고 「연결이 살아 있으면 잠금도 내 것」이라고 추론했다.
 * 그 추론이 깨지는 자리: **transaction/statement 모드 pooler**(pgbouncer 등) 뒤에서는 클라이언트
 * 연결이 그대로여도 질의마다 **다른 서버 backend** 에 붙는다. 그러면 ⓐ 잠금을 잡은 backend 가
 * 반납되어 서버가 잠금을 놓고 ⓑ 이쪽의 `SELECT 1` 은 다른 backend 에서 성공하므로 **둘이 동시에
 * `Held`** 를 받는다. 지금 질의는 그 상황에서 **거짓**을 낸다(내 backend 에 그 잠금이 없다).
 *
 * 그래도 pooler 를 **지원하지는 않는다**(알려진 제한 20) — 애초에 잠금을 잡은 질의와 이 질의가
 * 다른 backend 로 가면 `pg_try_advisory_lock` 자체가 의미를 잃는다. 이 probe 가 하는 일은 그
 * 어긋남을 **조용히 넘기지 않는** 것이고, 지금 배포는 직접 연결이다.
 *
 * bigint 키는 `classid`(상위 32비트)·`objid`(하위 32비트)로 나뉘고 세션 잠금은 `objsubid = 1`
 * 이다 — 키를 상수로 쪼개 박지 않고 인자에서 계산한다(키가 2^32 를 넘는 날에도 맞다).
 */
private fun stillHolding(
    connection: java.sql.Connection,
    key: Long,
): Boolean =
    try {
        connection.prepareStatement(LEASE_HELD_BY_SELF).use { statement ->
            statement.setLong(1, key ushr Int.SIZE_BITS)
            statement.setLong(2, key and LOW_32_BITS)
            statement.executeQuery().use { it.next() }
        }
    } catch (
        @Suppress("SwallowedException") lost: java.sql.SQLException,
    ) {
        // 연결이 끊겼다 = 임대를 잃었다. 예외를 올리지 않는다 — 호출부가 **값**으로 멈춘다.
        false
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

/**
 * `internal` 인 이유(cr R-13 ⓒ): `RelayLeaseLossDatabaseTest` 가 **이 키를 손으로 베껴**
 * `pg_locks` 에서 홀더 백엔드를 찾고 있었다. 어긋나면 그 test 가 아무 행도 못 찾아 기대가
 * RED 가 되므로 fail-closed 이긴 하지만, 값을 한 자리에만 두면 어긋날 자리가 없다.
 */
internal const val NOTIFICATION_RELAY_LOCK_KEY = 6_110_001L

private const val STRATEGY_EVENT_RELAY_LOCK_KEY = 6_110_002L

private const val TRY_ADVISORY_LOCK = "SELECT pg_try_advisory_lock(?)"
private const val ADVISORY_UNLOCK = "SELECT pg_advisory_unlock(?)"
private const val LOW_32_BITS = 0xFFFF_FFFFL

private const val LEASE_HELD_BY_SELF =
    "SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid() " +
        "AND classid = ? AND objid = ? AND objsubid = 1"
