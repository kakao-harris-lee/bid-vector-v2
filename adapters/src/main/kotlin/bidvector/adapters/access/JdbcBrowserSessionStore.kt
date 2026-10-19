package bidvector.adapters.access

import bidvector.adapters.persistence.ConnectionSource
import bidvector.workflow.access.AccountId
import bidvector.workflow.access.BrowserSessionDigest
import bidvector.workflow.access.BrowserSessionSnapshot
import bidvector.workflow.access.BrowserSessionStorePort
import bidvector.workflow.access.CsrfDigest
import bidvector.workflow.access.NewBrowserSession
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * 세션 저장소 — **쿠키 원문을 받는 서명이 없다.** 포트가 [BrowserSessionDigest] 만 받으므로
 * 이 클래스에 원문이 닿을 길이 없고, 따라서 「해시로만 저장」이 코드 규율이 아니라 타입의
 * 성질이다.
 *
 * 세션을 **만드는** 입력([NewBrowserSession])은 `workflow` 의 `internal` 생성자라 이 어댑터가
 * 스스로 지을 수 없다 — 받은 것을 그대로 행으로 옮긴다.
 */
class JdbcBrowserSessionStore(
    private val connections: ConnectionSource,
) : BrowserSessionStorePort {
    /**
     * 폐기와 등록이 **한 커넥션 블록**이다 — `ConnectionSource` 의 어느 구현 아래에서도 그
     * 블록 하나가 트랜잭션 하나다(경계가 주인이면 ambient 트랜잭션, 호출이 주인이면 그 호출의
     * 트랜잭션). 두 문장을 포트의 두 호출로 두면 로그인 경계 아래에서 따로 커밋될 수 있다.
     */
    override fun openReplacing(
        session: NewBrowserSession,
        revokedAt: Instant,
    ) {
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.REVOKE_ACCOUNT_SESSIONS).use { statement ->
                statement.setTimestamp(1, Timestamp.from(revokedAt))
                statement.setLong(2, session.account.value)
                statement.executeUpdate()
            }
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
    }

    override fun byDigest(digest: BrowserSessionDigest): BrowserSessionSnapshot? =
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.SELECT_BROWSER_SESSION).use { statement ->
                statement.setString(1, digest.hex)
                statement.executeQuery().use { rs -> if (rs.next()) rs.toBrowserSessionSnapshot() else null }
            }
        }

    override fun touch(
        digest: BrowserSessionDigest,
        usedAt: Instant,
        idleExpiresAt: Instant,
    ) {
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.TOUCH_BROWSER_SESSION).use { statement ->
                var index = 1
                statement.setTimestamp(index++, Timestamp.from(usedAt))
                statement.setTimestamp(index++, Timestamp.from(idleExpiresAt))
                statement.setString(index, digest.hex)
                statement.executeUpdate()
            }
        }
    }

    override fun revoke(
        digest: BrowserSessionDigest,
        at: Instant,
    ) {
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.REVOKE_BROWSER_SESSION).use { statement ->
                statement.setTimestamp(1, Timestamp.from(at))
                statement.setString(2, digest.hex)
                statement.executeUpdate()
            }
        }
    }

    override fun revokeAllOf(
        account: AccountId,
        at: Instant,
    ) {
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.REVOKE_ACCOUNT_SESSIONS).use { statement ->
                statement.setTimestamp(1, Timestamp.from(at))
                statement.setLong(2, account.value)
                statement.executeUpdate()
            }
        }
    }
}

private fun ResultSet.toBrowserSessionSnapshot(): BrowserSessionSnapshot =
    BrowserSessionSnapshot(
        digest = BrowserSessionDigest(getString("session_digest")),
        account = AccountId(getLong("account_id")),
        csrfDigest = CsrfDigest(getString("csrf_digest")),
        lastUsedAt = getTimestamp("last_used_at").toInstant(),
        idleExpiresAt = getTimestamp("idle_expires_at").toInstant(),
        absoluteExpiresAt = getTimestamp("absolute_expires_at").toInstant(),
        revokedAt = getTimestamp("revoked_at")?.toInstant(),
    )
