package bidvector.adapters.access

import bidvector.adapters.persistence.ConnectionSource
import bidvector.workflow.access.AccountId
import bidvector.workflow.access.NewBrowserSession
import bidvector.workflow.access.PassphraseHash
import bidvector.workflow.access.SignInCompletionPort
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/**
 * 로그인 확정의 **한 트랜잭션**(verifier r1 F1 · code-review r2 M-1).
 *
 * 문장 셋이 한 [ConnectionSource.withConnection] 블록 안에 있다 — 로그인 경로의 공급원은
 * 「호출 하나 = 트랜잭션 하나」라 이 블록이 곧 한 커밋이다.
 *
 *  0. [AccessSql.SET_LOCAL_LOCK_WAIT] — 이 트랜잭션의 **잠금 대기 상한**(code-review r3 L-2).
 *     넘으면 PostgreSQL 이 끊고 그 로그인은 거부다 — 커넥션을 쥔 채 무한정 기다리지 않는다.
 *  1. [AccessSql.CLAIM_ACCOUNT_FOR_SIGN_IN] — 계정 행을 **잠그며** 실패 계수·잠금을 비운다.
 *     자격이 검증 때와 다르거나 지금 잠겨 있으면 0 행이고, 그때는 **아무것도 쓰지 않고** 끝난다.
 *  2. [AccessSql.REVOKE_ACCOUNT_SESSIONS] — 그 계정의 살아 있는 세션 전부를 폐기한다.
 *     1 의 잠금 아래이므로, 앞선 로그인이 방금 연 세션도 이 문장의 스냅숏에 들어온다.
 *  3. [AccessSql.INSERT_BROWSER_SESSION] — 새 세션을 등록한다.
 *
 * **왜 1 이 먼저인가.** 2·3 만으로는 교체와 겹칠 때 「옛 자격으로 연 세션이 교체의 폐기를
 * 비껴가 유일하게 살아남는」 상태가 된다. 1 을 먼저 두면 그 경우가 조건 불성립으로 떨어지고,
 * 겹치지 않는 경우에는 잠금이 두 쪽을 줄 세운다 — 한 기제가 두 결함을 함께 닫는다.
 */
class JdbcSignInCompletion(
    private val connections: ConnectionSource,
    private val lockWait: Duration,
) : SignInCompletionPort {
    override fun complete(
        session: NewBrowserSession,
        verified: PassphraseHash,
        at: Instant,
    ): Boolean =
        try {
            connections.withConnection { connection ->
                applyLockWait(connection)
                if (!claimAccount(connection, session.account, verified, at)) {
                    false
                } else {
                    revokeLiveSessions(connection, session.account, at)
                    insertSession(connection, session)
                    true
                }
            }
        } catch (failure: SQLException) {
            // 대기 상한을 넘긴 것만 **거부**로 접는다 — 그 밖의 DB 오류는 그대로 올린다
            // (삼키면 「로그인이 안 된다」가 원인 없이 반복된다). 판정은 SQLState 로 한다.
            if (failure.sqlState != LOCK_NOT_AVAILABLE) throw failure
            false
        }

    /**
     * 이 트랜잭션 안에서만 사는 대기 상한. 잠금을 기다리는 쪽이 **커넥션을 쥔 채** 무한정 서
     * 있지 않게 한다(code-review r3 L-2) — 상한을 넘으면 PostgreSQL 이 끊고 위의 `catch` 가
     * 그것을 거부로 읽는다.
     */
    private fun applyLockWait(connection: Connection) {
        connection.prepareStatement(AccessSql.SET_LOCAL_LOCK_WAIT).use { statement ->
            statement.setString(1, "${lockWait.toMillis()}ms")
            statement.executeQuery().use { rs -> rs.next() }
        }
    }

    private fun claimAccount(
        connection: Connection,
        account: AccountId,
        verified: PassphraseHash,
        at: Instant,
    ): Boolean =
        connection.prepareStatement(AccessSql.CLAIM_ACCOUNT_FOR_SIGN_IN).use { statement ->
            var index = 1
            statement.setLong(index++, account.value)
            statement.setString(index++, verified.encoded)
            statement.setTimestamp(index, Timestamp.from(at))
            statement.executeQuery().use { rs -> rs.next() }
        }

    private fun revokeLiveSessions(
        connection: Connection,
        account: AccountId,
        at: Instant,
    ) {
        connection.prepareStatement(AccessSql.REVOKE_ACCOUNT_SESSIONS).use { statement ->
            statement.setTimestamp(1, Timestamp.from(at))
            statement.setLong(2, account.value)
            statement.executeUpdate()
        }
    }

    private fun insertSession(
        connection: Connection,
        session: NewBrowserSession,
    ) {
        connection.prepareStatement(AccessSql.INSERT_BROWSER_SESSION).use { statement ->
            var index = 1
            statement.setString(index++, session.digest.hex)
            statement.setLong(index++, session.account.value)
            statement.setString(index++, session.csrfDigest.hex)
            statement.setTimestamp(index++, Timestamp.from(session.startedAt))
            statement.setTimestamp(index++, Timestamp.from(session.idleExpiresAt))
            statement.setTimestamp(index, Timestamp.from(session.absoluteExpiresAt))
            statement.executeUpdate()
        }
    }

    private companion object {
        /** PostgreSQL 이 `lock_timeout` 초과에 내는 SQLState — 「지금은 못 잡는다」의 표준 신호다. */
        const val LOCK_NOT_AVAILABLE = "55P03"
    }
}
