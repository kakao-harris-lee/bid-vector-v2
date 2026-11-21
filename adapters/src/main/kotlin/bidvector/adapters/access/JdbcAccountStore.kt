package bidvector.adapters.access

import bidvector.adapters.persistence.ConnectionSource
import bidvector.workflow.access.AccountId
import bidvector.workflow.access.AccountSignUpPort
import bidvector.workflow.access.AccountSnapshot
import bidvector.workflow.access.AccountStorePort
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.PassphraseHash
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * 요청 경로의 계정 저장소 — **읽기와 실패 계수 올리기만 한다.** 성공의 초기화는 여기 없다
 * (verifier r1 F1 뒤 [JdbcSignInCompletion] 의 한 트랜잭션으로 옮겼다 — 자격 조건·행 잠금과
 * 떨어질 수 없는 문장이다). 계정을 만들거나 해시를 바꾸는
 * 메서드가 이 클래스에 없고([AccountStorePort] 의 서명에도 없다), 같은 경계를 V20 의 열 단위
 * GRANT 가 한 겹 더 진다.
 *
 * [SignInConnections] 를 받는다 — 트랜잭션의 주인은 호출부이고(`JdbcStrategyRepository` 관례),
 * 그 공급원이 **빌린 커넥션마다 대기 상한을 씌운다**(code-review r4 M-1). 실패 계수 갱신도 계정
 * 행을 잠그는 문장이라 그 상한이 필요하다 — 그쪽은 **자격 없이 칠 수 있는 경로**다.
 */
internal class JdbcAccountStore(
    private val connections: SignInConnections,
) : AccountStorePort {
    override fun byLogin(login: LoginName): AccountSnapshot? =
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.SELECT_ACCOUNT_BY_LOGIN).use { statement ->
                statement.setString(1, login.value)
                statement.executeQuery().use { rs -> if (rs.next()) rs.toAccountSnapshot() else null }
            }
        }

    override fun registerFailure(
        account: AccountId,
        attemptLimit: Int,
        lockUntil: Instant,
    ) {
        // **경합이면 계수를 올리지 못한 채 끝난다**(code-review r4 M-1). 이 문장도 계정 행을
        // 잠그므로 대기 상한 아래 선다 — 그 상한을 넘기면 `null` 이고, 여기서는 그것을 그대로
        // 흘린다. 안전한 방향이다: 계수를 못 올리면 잠금이 **늦어질 뿐** 권한이 새지 않는다
        // (반대로 여기서 예외를 올리면 틀린 비밀 문구 하나가 500 이 된다).
        connections.withBoundedConnection { connection ->
            connection.prepareStatement(AccessSql.REGISTER_ACCOUNT_FAILURE).use { statement ->
                var index = 1
                // 상한이 두 번 바인딩되는 것은 두 열이 **같은 조건**으로 갈리기 때문이다 —
                // 한 문장 안에서 계수와 잠금이 따로 판정되면 그 둘이 어긋날 수 있다.
                statement.setInt(index++, attemptLimit)
                statement.setInt(index++, attemptLimit)
                statement.setTimestamp(index++, Timestamp.from(lockUntil))
                statement.setLong(index, account.value)
                statement.executeUpdate()
            }
        }
    }
}

/**
 * 열린 가입의 저장소(M15/15B) — **메서드가 하나뿐**이고 그 하나가 「없으면 만든다」다.
 *
 * 요청 경로의 조립이 받는 쓰기가 이것뿐이라, 인증 결함 하나가 해시 교체나 계정 삭제로 번지는 길이
 * 타입에 없다(같은 경계를 V29 의 열 단위 GRANT 가 한 겹 더 진다).
 *
 * [SignInConnections] 를 받는 이유는 [JdbcAccountStore] 와 같다 — 이 문장도 **자격 없이 칠 수 있는
 * 경로**이고, 같은 이름으로 동시에 들어오면 UNIQUE 색인에서 잠깐 기다린다. 그 대기가 상한 아래 선다.
 */
internal class JdbcAccountSignUpStore(
    private val connections: SignInConnections,
) : AccountSignUpPort {
    override fun insertIfAbsent(
        login: LoginName,
        hash: PassphraseHash,
    ): AccountId? =
        // 대기 상한을 넘기면 `null` 이고 여기서 **중복과 같은 값으로 접는다** — 아래 KDoc 의 사유.
        connections.withBoundedConnection { connection -> inserted(connection, login, hash) }?.firstOrNull()

    /**
     * 그 문장이 **돌려준 행**(0 또는 1). 목록으로 받는 이유는 경계의 서명이다 —
     * `withBoundedConnection` 의 결과 `null` 은 「경합으로 못 했다」이고 블록의 빈 목록은 「이미
     * 있다」라, 두 사실이 **블록 안에서는** 갈려 있다.
     *
     * **호출부가 그 둘을 일부러 409 로 접는다**(code-review r1 LOW 정정). 앞 판의 이 주석은 접지
     * 않는 것처럼 읽혔지만 바로 다음 줄의 `?.firstOrNull()` 이 접는다. 접는 이유는 **응답이
     * 같아야 하기 때문**이다: 잠금 대기를 넘긴 쪽은 같은 이름으로 동시에 들어온 상대가 있다는
     * 뜻이고, 그 상대가 커밋하면 실제로 중복이다. 상대가 롤백하는 좁은 창에서만 「비어 있는
     * 이름인데 이미 쓰인다고 듣는다」가 되고, 그때 재시도가 성공한다 — 안전한 방향이다.
     */
    private fun inserted(
        connection: Connection,
        login: LoginName,
        hash: PassphraseHash,
    ): List<AccountId> =
        connection.prepareStatement(AccessSql.INSERT_ACCOUNT_IF_ABSENT).use { statement ->
            statement.setString(1, login.value)
            statement.setString(2, hash.encoded)
            statement.executeQuery().use { rs ->
                if (rs.next()) listOf(AccountId(rs.getLong(1))) else emptyList()
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
    override fun existsWithLogin(login: LoginName): Boolean =
        connections.withConnection { connection ->
            connection.prepareStatement(AccessSql.SELECT_ACCOUNT_EXISTS_BY_LOGIN).use { statement ->
                statement.setString(1, login.value)
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
        lockedUntil = getTimestamp("locked_until")?.toInstant(),
    )
