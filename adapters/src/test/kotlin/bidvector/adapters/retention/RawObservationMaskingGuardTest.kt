package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.time.Instant

/** V3·V18 이 `RAISE EXCEPTION … USING ERRCODE` 로 쓰는 코드 — 권한 거부(42501)와 구별한다. */
private const val APPEND_ONLY_SQLSTATE = "P0001"
private const val INSUFFICIENT_PRIVILEGE_SQLSTATE = "42501"

private val SEED_AT = Instant.parse("2026-01-01T00:00:00Z")
private const val SEED_KEY = "ret-guard"

/**
 * P-1 — V18 이 append-only 계약에 낸 **단 하나의 구멍**이 그 모양대로만 열려 있는지 잰다.
 *
 * 각 test 는 설계 검토 (2) 의 우회 하나에 대응한다 — 우회 1(앱 역할) · 2(두 번째 마스킹·
 * 되돌림) · 3(다른 열 끼워 넣기) · DELETE 거부 유지. 「열렸다」 쪽 test 가 하나뿐인 것이
 * 요지다: 지나는 길이 하나이고 나머지는 전부 막힌다.
 */
class RawObservationMaskingGuardTest : PersistenceTestSupport() {
    @BeforeEach
    fun seed() {
        RetentionFixture(dataSource()).seedAll("guard", SEED_AT)
    }

    @Test
    fun `소유자의 마스킹 UPDATE 는 지난다 — 표지가 NULL 에서 값으로 한 번 간다`() {
        val updated = maskOnce(payload = """{"bidNtceNo":"RET-guard"}""")

        updated shouldBe 1
    }

    @Test
    fun `두 번째 마스킹은 거부한다 — 값에서 값으로 가는 UPDATE 가 원문을 다시 쓰는 길이다`() {
        maskOnce(payload = """{"bidNtceNo":"RET-guard"}""") shouldBe 1

        val failure =
            shouldThrow<PSQLException> {
                owner { connection -> applyMasking(connection, """{"bidNtceNo":"OVERWRITTEN"}""") }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    @Test
    fun `마스킹 표지 되돌림은 거부한다 — 값에서 NULL 로 가면 다시 마스킹 대상이 된다`() {
        maskOnce(payload = """{"bidNtceNo":"RET-guard"}""") shouldBe 1

        val failure =
            shouldThrow<PSQLException> {
                owner { connection ->
                    connection
                        .prepareStatement(
                            "UPDATE raw_observation SET pii_masked_at = NULL WHERE observation_key = ?",
                        ).use { statement ->
                            statement.setString(1, SEED_KEY)
                            statement.executeUpdate()
                        }
                }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    @Test
    fun `마스킹 UPDATE 에 다른 열 변경을 끼워 넣으면 거부한다 — 행 전체를 비교한다`() {
        val failure =
            shouldThrow<PSQLException> {
                owner { connection ->
                    connection
                        .prepareStatement(
                            "UPDATE raw_observation SET payload = '{}', pii_masked_at = now(), " +
                                "observed_at = now() WHERE observation_key = ?",
                        ).use { statement ->
                            statement.setString(1, SEED_KEY)
                            statement.executeUpdate()
                        }
                }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    @Test
    fun `표지를 찍지 않는 원문 UPDATE 는 여전히 거부한다 — V3 의 성질이 남아 있다`() {
        val failure =
            shouldThrow<PSQLException> {
                owner { connection ->
                    connection
                        .prepareStatement("UPDATE raw_observation SET payload = 'TAMPERED' WHERE observation_key = ?")
                        .use { statement ->
                            statement.setString(1, SEED_KEY)
                            statement.executeUpdate()
                        }
                }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    @Test
    fun `소유자의 DELETE 는 여전히 거부한다 — 운영자 결정 A-3 「파기하지 않는다」`() {
        val failure =
            shouldThrow<PSQLException> {
                owner { connection ->
                    connection
                        .prepareStatement("DELETE FROM raw_observation WHERE observation_key = ?")
                        .use { statement ->
                            statement.setString(1, SEED_KEY)
                            statement.executeUpdate()
                        }
                }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    /**
     * 우회 1 의 **1차 방어** — GRANT 가 없으니 트리거에 닿기 전에 권한이 막는다.
     * SQLSTATE 로 「무엇이 막았는가」를 구별한다(아래 test 와 쌍이다).
     */
    @Test
    fun `앱 역할의 마스킹 UPDATE 는 권한이 먼저 막는다`() {
        val failure =
            shouldThrow<PSQLException> {
                appConnection().use { connection -> applyMasking(connection, "{}") }
            }

        failure.sqlState shouldBe INSUFFICIENT_PRIVILEGE_SQLSTATE
    }

    /**
     * 우회 1 의 **2차 방어** — 뒤 마이그레이션이 실수로 UPDATE 권한을 주더라도 트리거가 막는다.
     * 그 상황을 트랜잭션 안에서 **실제로 만들어** 재고(GRANT 는 롤백으로 사라진다) 거부 코드가
     * 권한이 아니라 트리거(P0001)임을 확인한다 — 이 test 가 없으면 「2차 방어가 있다」는 주장은
     * GRANT 가 없다는 사실에 가려 영원히 실측되지 않는다.
     */
    @Test
    fun `권한을 줘도 앱 역할의 마스킹 UPDATE 는 트리거가 막는다`() {
        val failure =
            shouldThrow<PSQLException> {
                owner { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("GRANT UPDATE ON raw_observation TO bidvector_app")
                        statement.execute("SET ROLE bidvector_app")
                    }
                    applyMasking(connection, "{}")
                }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    /**
     * `notice_audit` 에는 구멍이 없다(운영자 결정 A-3 의 좁은 적용 — 그 표의 `previous_row` 는
     * `to_jsonb(OLD)` 라 `notice` 의 열 집합뿐이고 담당자 열이 거기 없다). 「구멍이 하나뿐」을
     * 이 표에서 재는 자리다.
     */
    @Test
    fun `notice_audit 은 마스킹 UPDATE 도 거부한다 — V18 이 낸 구멍은 raw_observation 하나다`() {
        val failure =
            shouldThrow<PSQLException> {
                owner { connection ->
                    connection
                        .createStatement()
                        .use { it.execute("UPDATE notice_audit SET previous_row = '{}'::jsonb") }
                }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    private fun maskOnce(payload: String): Int =
        dataSource().connection.use { connection ->
            connection.autoCommit = true
            applyMasking(connection, payload)
        }

    private fun applyMasking(
        connection: Connection,
        payload: String,
    ): Int =
        connection
            .prepareStatement(
                "UPDATE raw_observation SET payload = ?, pii_masked_at = now() WHERE observation_key = ?",
            ).use { statement ->
                statement.setString(1, payload)
                statement.setString(2, SEED_KEY)
                statement.executeUpdate()
            }

    /** 거부를 재는 자리는 **롤백하는 트랜잭션** 안이다 — GRANT 변이도 그 롤백이 되돌린다. */
    private fun <T> owner(body: (Connection) -> T): T =
        dataSource().connection.use { connection ->
            connection.autoCommit = false
            try {
                body(connection)
            } finally {
                connection.rollback()
            }
        }
}
