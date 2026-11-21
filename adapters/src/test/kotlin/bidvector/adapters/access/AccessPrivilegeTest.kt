package bidvector.adapters.access

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

/** PostgreSQL 권한 거부 — 이 코드로 떨어지는 것이 「권한이 없다」의 실측이다. */
private const val INSUFFICIENT_PRIVILEGE = "42501"

/**
 * M7/7D — V20 의 **열 단위 GRANT** 가 실제로 무엇을 막는지 앱 역할로 직접 쏘아 잰다.
 *
 * 왜 권한 행렬(`CleanMigrationPrivilegeTest` 축9)만으로 모자라나: `has_table_privilege(…,
 * 'UPDATE')` 는 **한 열이라도** UPDATE 권한이 있으면 참이다. 즉 그 행렬은 「해시 열을 덮어쓸 수
 * 있는가」를 보지 못한다 — 이 slice 가 세운 경계가 바로 그 열 단위다(계정 생성·해시 교체는
 * 운영자 명령의 소유자 연결만 할 수 있다).
 *
 * `appConnection()` 은 `SET ROLE bidvector_app` 한 커넥션이다(하네스 제공) — 런타임 풀이 하는
 * 것과 같은 전환이다.
 */
class AccessPrivilegeTest : PersistenceTestSupport() {
    @BeforeEach
    fun seedRows() {
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
                        "VALUES ('privilege-probe', 'not-a-real-hash-value', now(), now(), 0)",
                )
                statement.execute(
                    "INSERT INTO browser_session " +
                        "(session_digest, account_id, csrf_digest, last_used_at, idle_expires_at, " +
                        " absolute_expires_at) " +
                        "SELECT 'probe-digest', id, 'probe-csrf-digest', now(), now() + interval '30 minutes', " +
                        "       now() + interval '12 hours' FROM account WHERE login_name = 'privilege-probe'",
                )
            }
        }
    }

    /**
     * **M15/15B 가 이 단언을 뒤집었다.** V20 은 계정 INSERT 를 0 으로 뒀고(「앱이 뚫려도 계정을 만들
     * 수 없다」), V29 가 그 결정의 DB 권한 절반을 되돌린다 — 열린 가입(운영자 지시 2026-11-21).
     * 열린 것은 **열 둘**뿐이고 그 사실이 이 두 문장이다: 가입이 쓰는 꼴은 서고, 열을 하나라도 더
     * 적으면 거부다(음성 대조).
     *
     * `id` 를 적지 않는 것이 전제다 — identity 열의 시퀀스는 열에 소유되어 있어 `serial` 과 달리
     * 별도 USAGE GRANT 를 요구하지 않는다. 그 사실을 문장이 아니라 **이 성공**이 든다.
     */
    @Test
    fun `앱 역할은 가입이 쓰는 두 열로만 계정을 만들 수 있다 — 열을 더 적으면 거부`() {
        allowed("INSERT INTO account (login_name, passphrase_hash) VALUES ('opened', 'opened-hash')")

        refused("INSERT INTO account (login_name, passphrase_hash, created_at) VALUES ('f1', 'h', now())")
        refused("INSERT INTO account (login_name, passphrase_hash, updated_at) VALUES ('f2', 'h', now())")
        refused("INSERT INTO account (login_name, passphrase_hash, failed_attempts) VALUES ('f3', 'h', 0)")
        refused("INSERT INTO account (login_name, passphrase_hash, locked_until) VALUES ('f4', 'h', now())")
    }

    /**
     * 가입이 쓰는 **실제 문장**(production 상수)이 앱 역할로 돈다 — 위 test 는 손으로 적은 꼴이고,
     * 이쪽은 `ON CONFLICT … RETURNING` 까지 그대로다(그 구문은 UNIQUE 색인 읽기와 `id` 의 SELECT
     * 권한을 함께 요구한다).
     */
    @Test
    fun `가입의 production 문장이 앱 역할로 돈다 — ON CONFLICT 와 RETURNING 포함`() {
        allowedPrepared(AccessSql.INSERT_ACCOUNT_IF_ABSENT) { statement ->
            statement.setString(1, "opened-via-production-sql")
            statement.setString(2, "opened-hash")
        }
    }

    /**
     * **V29 가 열지 않은 것** — 계정 생성이 열렸다고 해서 해시 교체나 파기가 열리지 않는다. 이 셋이
     * 그 사실의 음성 대조이고, 위 「두 열만」과 짝이다(아래 열 단위 UPDATE test 와 다른 축: 저쪽은
     * V20 의 기존 경계, 이쪽은 **V29 뒤에도 그대로인가**를 묻는다).
     */
    @Test
    fun `가입이 열려도 해시 교체와 파기는 그대로 막혀 있다 — V29 가 열지 않은 것`() {
        refused("UPDATE account SET passphrase_hash = 'overwritten'")
        refused("DELETE FROM account")
        refused("TRUNCATE TABLE account")
    }

    @Test
    fun `앱 역할은 비밀 문구 해시와 로그인 식별자를 덮어쓸 수 없다 — 열 단위 UPDATE`() {
        refused("UPDATE account SET passphrase_hash = 'overwritten'")
        refused("UPDATE account SET login_name = 'renamed'")
        refused("UPDATE account SET updated_at = now()")
    }

    /**
     * verifier r1 F6 — **적재 시각 열**. 열 GRANT 를 `created_at` 쪽으로 넓혀도 이 class 가
     * 초록이었다(그 열을 쏘는 문장이 없었다). `browser_session.created_at` 은 보존·파기(6B-3)의
     * **cutoff 축**이라, 앱 결함이 그 값을 다시 쓰면 파기 대상이 영원히 미래로 밀린다.
     * `account.created_at` 은 계정이 언제 생겼는지의 유일한 기록이다.
     */
    @Test
    fun `앱 역할은 두 표의 적재 시각을 바꿀 수 없다 — 보존 cutoff 와 생성 기록`() {
        refused("UPDATE account SET created_at = now()")
        refused("UPDATE browser_session SET created_at = now()")
    }

    @Test
    fun `앱 역할은 실패 계수와 잠금만 갱신할 수 있다 — 요청 경로가 필요한 두 열`() {
        allowed("UPDATE account SET failed_attempts = 3, locked_until = now() + interval '15 minutes'")
    }

    /**
     * H-1·F1 뒤의 두 문장은 모양이 다르다 — 갱신식이 **같은 행의 열을 읽고**(`failed_attempts + 1`),
     * 조건이 **자격 해시 열을 읽으며**(F1), 하나는 `RETURNING` 으로 열을 되돌린다. 셋 다 UPDATE
     * 권한 말고 **SELECT 권한**을 함께 요구하므로, 앞 test 의 절대값 UPDATE 가 통과한다고 해서
     * 이 둘이 통과한다는 보장이 없다. 그래서 production 문장을 그대로 쏜다(상수를 가져와 쓴다 —
     * 사본을 두지 않는다). 잠금 수단을 `SELECT … FOR UPDATE` 로 바꾸면 이 자리가 먼저 붉어진다
     * (그 구문은 표 단위 UPDATE 권한을 요구하고 앱 역할에는 열 단위뿐이다).
     */
    @Test
    fun `앱 역할은 계수 증가·확정 문장을 그대로 돌릴 수 있다 — 갱신식·조건의 열 읽기 포함`() {
        allowedPrepared(AccessSql.REGISTER_ACCOUNT_FAILURE) { statement ->
            statement.setInt(1, 5)
            statement.setInt(2, 5)
            statement.setTimestamp(3, Timestamp.from(Instant.parse("2026-10-16T00:15:00Z")))
            statement.setLong(4, probeAccountId())
        }
        allowedPrepared(AccessSql.CLAIM_ACCOUNT_FOR_SIGN_IN) { statement ->
            statement.setLong(1, probeAccountId())
            statement.setString(2, "not-a-real-hash-value")
            statement.setTimestamp(3, Timestamp.from(Instant.parse("2026-10-16T00:00:00Z")))
        }
    }

    @Test
    fun `앱 역할은 세션의 소유 계정·CSRF 해시·절대 시한을 바꿀 수 없다`() {
        refused("UPDATE browser_session SET account_id = 999999")
        refused("UPDATE browser_session SET csrf_digest = 'swapped'")
        refused("UPDATE browser_session SET absolute_expires_at = now() + interval '100 days'")
        refused("UPDATE browser_session SET session_digest = 'swapped'")
    }

    @Test
    fun `앱 역할은 세션의 사용 시각·유휴 시한·폐기 시각만 갱신할 수 있다`() {
        allowed("UPDATE browser_session SET last_used_at = now(), idle_expires_at = now(), revoked_at = now()")
    }

    @Test
    fun `앱 역할은 두 표에서 행을 지울 수 없다 — 파기는 소유자 연결의 일이다`() {
        refused("DELETE FROM browser_session")
        refused("DELETE FROM account")
        refused("TRUNCATE TABLE browser_session")
    }

    @Test
    fun `앱 역할은 두 표를 읽을 수 있다 — 요청 경로의 판정 입력`() {
        allowed("SELECT id, passphrase_hash, failed_attempts, locked_until FROM account")
        allowed("SELECT session_digest, account_id, csrf_digest, revoked_at FROM browser_session")
    }

    @Test
    fun `앱 역할은 세션을 등록할 수 있다 — 로그인이 그것을 한다`() {
        allowed(
            "INSERT INTO browser_session " +
                "(session_digest, account_id, csrf_digest, last_used_at, idle_expires_at, absolute_expires_at) " +
                "SELECT 'second-digest', id, 'second-csrf', now(), now() + interval '30 minutes', " +
                "       now() + interval '12 hours' FROM account WHERE login_name = 'privilege-probe'",
        )
    }

    private fun refused(sql: String) {
        val thrown =
            runCatching {
                appConnection().use { connection ->
                    connection.createStatement().use { it.execute(sql) }
                }
            }.exceptionOrNull()

        (thrown as? SQLException)?.sqlState shouldBe INSUFFICIENT_PRIVILEGE
    }

    private fun allowed(sql: String) {
        appConnection().use { connection ->
            connection.createStatement().use { it.execute(sql) }
            connection.rollback()
        }
    }

    private fun allowedPrepared(
        sql: String,
        bind: (PreparedStatement) -> Unit,
    ) {
        appConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement)
                statement.execute()
            }
            connection.rollback()
        }
    }

    private fun probeAccountId(): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM account WHERE login_name = 'privilege-probe'").use { rs ->
                    check(rs.next()) { "탐침 계정 행이 없다" }
                    rs.getLong(1)
                }
            }
        }
}
