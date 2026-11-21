package bidvector.adapters.access

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test

/**
 * **계정 표의 열 단위 권한 등식**(M15/15B) — `BidCaseColumnPrivilegeTest` 와 같은 분담이다.
 *
 * 왜 따로 재는가: `CleanMigrationPrivilegeTest` 축9 의 행렬은 `has_table_privilege` 라 **열 단위
 * 부여를 세지 않는다**. V29 가 `GRANT INSERT (login_name, passphrase_hash)` 를 주어도 그 행렬의
 * `account.insert` 는 여전히 `false` 이고, 그래서 그 자리만으로는 **가입이 무엇을 열었는지**를 볼 수
 * 없다. 이 class 가 그 자리다.
 *
 * 술어는 `has_column_privilege` 다 — 직접 부여·PUBLIC 부여·역할 상속을 전부 해소한다. 그래서 누군가
 * 표 단위 INSERT 를 주면 **모든 열이 참**이 되어 등식이 깨진다(음성 대조가 술어에 붙어 있다).
 *
 * 열은 DB 에서 **발견**한다 — 열이 늘면 모집단이 함께 늘고, 그 새 열이 열려 있으면 여기서 붉는다.
 */
class AccountColumnPrivilegeTest : PersistenceTestSupport() {
    /**
     * V29 가 연 것 — **가입이 쓰는 두 열뿐**이다. 시각 세 열은 DEFAULT 가 정하므로 요청 경로가 값을
     * 고를 수 없고(`created_at` 을 먼 미래로 지정해 보존 cutoff 를 비껴가는 길이 없다), `id` 는
     * identity 라 애초에 적을 수 없다.
     */
    @Test
    fun `앱 역할이 계정 표에 넣을 수 있는 열은 아이디와 해시 둘뿐이다`() {
        grantedColumns("account", "INSERT") shouldContainExactlyInAnyOrder listOf("login_name", "passphrase_hash")
    }

    /** V20 의 경계가 **V29 뒤에도 그대로**인가 — 가입이 열렸다고 해서 해시 교체가 열리지 않는다. */
    @Test
    fun `앱 역할이 계정 표에서 갱신할 수 있는 열은 실패 계수와 잠금 둘뿐이다`() {
        grantedColumns("account", "UPDATE") shouldContainExactlyInAnyOrder listOf("failed_attempts", "locked_until")
    }

    /**
     * 술어의 **양성 대조** — 표 단위 SELECT 가 있는 표에서는 모든 열이 참으로 나온다. 이 단언이
     * 없으면 위 둘이 「술어가 늘 거짓」이라서 통과하는 경우와 구별되지 않는다.
     */
    @Test
    fun `술어 대조 — 표 단위 SELECT 는 모든 열에서 참이다`() {
        val columns = accountColumns()
        columns.shouldNotBeEmpty()

        grantedColumns("account", "SELECT") shouldContainExactlyInAnyOrder columns
    }

    private fun accountColumns(): List<String> =
        queryStrings(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = 'account'",
        )

    private fun grantedColumns(
        table: String,
        privilege: String,
    ): List<String> {
        val columns = accountColumns()
        check(columns.isNotEmpty()) { "열을 발견하지 못하면 아래 등식이 공허해진다: $table" }
        return columns.filter { column -> hasColumnPrivilege(table, column, privilege) }
    }

    private fun hasColumnPrivilege(
        table: String,
        column: String,
        privilege: String,
    ): Boolean =
        dataSource().connection.use { connection ->
            connection.prepareStatement("SELECT has_column_privilege('bidvector_app', ?, ?, ?)").use { statement ->
                statement.setString(1, table)
                statement.setString(2, column)
                statement.setString(3, privilege)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        }

    private fun queryStrings(sql: String): List<String> {
        val actual = mutableListOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    while (rs.next()) actual += rs.getString(1)
                }
            }
        }
        return actual
    }
}
