package bidvector.adapters.access

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.SQLException

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

    @Test
    fun `앱 역할은 계정을 만들 수 없다 — INSERT 권한이 없다`() {
        refused(
            "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
                "VALUES ('forged', 'forged-hash', now(), now(), 0)",
        )
    }

    @Test
    fun `앱 역할은 비밀 문구 해시와 로그인 식별자를 덮어쓸 수 없다 — 열 단위 UPDATE`() {
        refused("UPDATE account SET passphrase_hash = 'overwritten'")
        refused("UPDATE account SET login_name = 'renamed'")
        refused("UPDATE account SET updated_at = now()")
    }

    @Test
    fun `앱 역할은 실패 계수와 잠금만 갱신할 수 있다 — 요청 경로가 필요한 두 열`() {
        allowed("UPDATE account SET failed_attempts = 3, locked_until = now() + interval '15 minutes'")
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
}
