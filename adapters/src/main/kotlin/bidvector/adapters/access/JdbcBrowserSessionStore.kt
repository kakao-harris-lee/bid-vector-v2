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
 * **여는 문장이 여기 없다**(verifier r1 F1 뒤) — 세션 등록은 자격 조건·기존 세션 폐기와 한
 * 트랜잭션이어야 해서 [JdbcSignInCompletion] 이 그 셋을 진다. 이 클래스가 지는 것은 조회와
 * 살아 있는 세션의 갱신·폐기뿐이다.
 */
class JdbcBrowserSessionStore(
    private val connections: ConnectionSource,
) : BrowserSessionStorePort {
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
