package bidvector.adapters.access

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.setNullableTimestamp
import bidvector.workflow.access.AccountId
import bidvector.workflow.access.AccountSnapshot
import bidvector.workflow.access.AccountStorePort
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.PassphraseHash
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * 요청 경로의 계정 저장소 — **읽기와 실패 계수 갱신만 한다.** 계정을 만들거나 해시를 바꾸는
 * 메서드가 이 클래스에 없고([AccountStorePort] 의 서명에도 없다), 같은 경계를 V20 의 열 단위
 * GRANT 가 한 겹 더 진다.
 *
 * [ConnectionSource] 를 받는다 — 트랜잭션의 주인은 호출부다(`JdbcStrategyRepository` 관례).
 */
class JdbcAccountStore(
    private val connections: ConnectionSource,
) : AccountStorePort {
    override fun byLogin(login: LoginName): AccountSnapshot? =
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.SELECT_ACCOUNT_BY_LOGIN).use { statement ->
                statement.setString(1, login.value)
                statement.executeQuery().use { rs -> if (rs.next()) rs.toAccountSnapshot() else null }
            }
        }

    override fun recordFailure(
        account: AccountId,
        attempts: Int,
        lockedUntil: Instant?,
    ) = updateFailureState(account, attempts, lockedUntil)

    override fun clearFailures(account: AccountId) = updateFailureState(account, 0, null)

    private fun updateFailureState(
        account: AccountId,
        attempts: Int,
        lockedUntil: Instant?,
    ) {
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.UPDATE_ACCOUNT_FAILURE).use { statement ->
                var index = 1
                statement.setInt(index++, attempts)
                statement.setNullableTimestamp(index++, lockedUntil)
                statement.setLong(index, account.value)
                statement.executeUpdate()
            }
        }
    }
}

/**
 * 운영자 명령 전용 저장소 — **소유자 연결 위에서만 조립된다**(배선이 풀을 넘기지 않는다,
 * `JdbcRetentionStore` 선례). 요청 경로의 컨텍스트에는 이 타입의 빈이 없다.
 */
class JdbcAccountProvisioningStore(
    private val connections: ConnectionSource,
) : bidvector.workflow.access.AccountProvisioningPort {
    override fun anyExists(): Boolean =
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.SELECT_ACCOUNT_EXISTS).use { statement ->
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        }

    override fun insert(
        login: LoginName,
        hash: PassphraseHash,
        at: Instant,
    ): AccountId =
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.INSERT_ACCOUNT).use { statement ->
                var index = 1
                statement.setString(index++, login.value)
                statement.setString(index++, hash.encoded)
                statement.setTimestamp(index++, Timestamp.from(at))
                statement.setTimestamp(index, Timestamp.from(at))
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "계정 생성이 식별자를 내지 않았다" }
                    AccountId(rs.getLong(1))
                }
            }
        }

    override fun replaceHash(
        login: LoginName,
        hash: PassphraseHash,
        at: Instant,
    ): AccountId? =
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.UPDATE_ACCOUNT_HASH).use { statement ->
                var index = 1
                statement.setString(index++, hash.encoded)
                statement.setTimestamp(index++, Timestamp.from(at))
                statement.setString(index, login.value)
                statement.executeQuery().use { rs -> if (rs.next()) AccountId(rs.getLong(1)) else null }
            }
        }
}

private fun ResultSet.toAccountSnapshot(): AccountSnapshot =
    AccountSnapshot(
        id = AccountId(getLong("id")),
        hash = PassphraseHash(getString("passphrase_hash")),
        failedAttempts = getInt("failed_attempts"),
        lockedUntil = getTimestamp("locked_until")?.toInstant(),
    )
