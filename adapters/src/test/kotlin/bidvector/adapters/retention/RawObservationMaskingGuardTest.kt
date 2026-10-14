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

/** CHECK 제약 위반 — 트리거 거부(P0001)와 구별한다(V19 의 투영 열 형태 제약). */
private const val CHECK_VIOLATION_SQLSTATE = "23514"

private val SEED_AT = Instant.parse("2026-01-01T00:00:00Z")
private const val SEED_KEY = "ret-guard"

/** 관문 ②가 **이름이 아니라 소유자**로 가르는지 재기 위해 트랜잭션 안에서 만드는 역할. */
private const val THIRD_ROLE = "ret_guard_third_role"

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
     * vr F-5 · mig L-1 — 관문 ②는 역할 **이름**이 아니라 **표 소유자**로 가른다. 이름 하나를
     * 거부하는 deny-list 였다면 뒤의 마이그레이션이 `bidvector_app` 이 아닌 새 역할에 UPDATE 를
     * 주는 순간 열렸다. allow-list(실행 역할이 표 소유자의 멤버인가)는 그 역할을 열거 없이 막는다.
     */
    @Test
    fun `제3 역할에 권한을 줘도 트리거가 막는다 — 소유자 allow-list 다`() {
        val failure =
            shouldThrow<PSQLException> {
                owner { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("CREATE ROLE $THIRD_ROLE")
                        statement.execute("GRANT SELECT, UPDATE ON raw_observation TO $THIRD_ROLE")
                        statement.execute("SET LOCAL ROLE $THIRD_ROLE")
                    }
                    applyMasking(connection, "{}")
                }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    /**
     * vr F-4 — 표지를 **미리 찍은 INSERT**. 앱 역할의 INSERT GRANT 는 표 단위라 새 열도 포함하고,
     * 마스킹 조회는 `pii_masked_at IS NULL` 만 보므로 그런 행은 영원히 마스킹되지 않고 계수에도
     * 없다. 표지를 쓰는 주체를 마스킹 주체로 닫는다 — INSERT 에서 표지는 NULL 이어야 한다.
     */
    @Test
    fun `표지를 찍은 INSERT 는 거부한다 — 앱 역할이 마스킹을 자칭할 수 없다`() {
        val failure =
            shouldThrow<PSQLException> {
                appConnection().use { connection -> insertRawObservation(connection, maskedAt = "now()") }
            }

        failure.sqlState shouldBe APPEND_ONLY_SQLSTATE
    }

    /** 음성 대조 — 표지를 찍지 않는 보통의 적재는 INSERT 분기를 지난다(수집이 막히지 않는다). */
    @Test
    fun `표지가 NULL 인 INSERT 는 지난다 — 수집 경로는 그대로다`() {
        appConnection().use { connection ->
            insertRawObservation(connection, maskedAt = "NULL") shouldBe 1
        }
    }

    /**
     * vr F-9 — 객체가 아닌 투영 열 한 행이 마스킹 조회 전체(`jsonb_object_keys`)를 매 run
     * 실패시켰다. 그 행이 들어올 수 없게 **구조로** 닫는다(V19 CHECK) — 조회 쪽에서 피하면 그
     * 행의 남김 집합이 조용히 비어 원문이 `{}` 가 된다.
     */
    @Test
    fun `객체가 아닌 투영 열은 INSERT 가 거부한다 — 마스킹 조회를 매 run 실패시켰다`() {
        val failure =
            shouldThrow<PSQLException> {
                appConnection().use { connection ->
                    insertRawObservation(connection, maskedAt = "NULL", payloadFields = "'[1]'::jsonb")
                }
            }

        failure.sqlState shouldBe CHECK_VIOLATION_SQLSTATE
    }

    /**
     * D-6B3-3 ① — 마스킹 열은 **둘**이다(`payload`·`payload_fields`). 업체명이 투영 열에도
     * 실리므로 그 열이 불변이면 그 값은 구조적으로 지울 수 없다(privacy F-1).
     */
    @Test
    fun `마스킹 UPDATE 는 투영 열을 함께 바꿀 수 있다 — 마스킹 열이 둘이다`() {
        val updated =
            dataSource().connection.use { connection ->
                connection.autoCommit = true
                connection
                    .prepareStatement(
                        "UPDATE raw_observation SET payload = '{}', payload_fields = '{}'::jsonb, " +
                            "pii_masked_at = now() WHERE observation_key = ?",
                    ).use { statement ->
                        statement.setString(1, SEED_KEY)
                        statement.executeUpdate()
                    }
            }

        updated shouldBe 1
    }

    /**
     * cr L-2 — **표지만 찍는 UPDATE 는 지난다**(관문이 「payload 가 바뀌었는가」를 보지 않는다).
     * 그 행은 원문을 그대로 가진 채 「마스킹 완료」로 영구히 잠긴다(둘째 마스킹은 관문 ④가 거부).
     *
     * 닫지 않는 이유: 관문에 「표지와 마스킹 열이 **함께** 바뀌어야 한다」를 넣으면, 치환값이
     * 원문과 우연히 같은 행(이미 남김 집합만 남은 행)의 마스킹 UPDATE 가 거부되어 그 run 이
     * 실패한다 — 되돌릴 수 없는 경로에서 fail-closed 가 아니라 **fail-stuck** 이다. 출하 경로는
     * 늘 치환값을 함께 쓰고, 소유자 임의 SQL 은 비방어 축이다(`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`).
     * 그래서 **막지 않고 성질을 실측**해 둔다(runbook 알려진 제한 9).
     */
    @Test
    fun `표지만 찍는 UPDATE 는 지난다 — 비방어 성질을 실측해 둔다`() {
        val updated =
            dataSource().connection.use { connection ->
                connection.autoCommit = false
                try {
                    connection
                        .prepareStatement(
                            "UPDATE raw_observation SET pii_masked_at = now() WHERE observation_key = ?",
                        ).use { statement ->
                            statement.setString(1, SEED_KEY)
                            statement.executeUpdate()
                        }
                } finally {
                    connection.rollback()
                }
            }

        updated shouldBe 1
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

    /**
     * 적재 한 행 — INSERT 분기를 재는 자리가 쓴다. 표지와 투영 열의 **문면을 인자로** 받는다
     * (`now()`·`NULL`·비객체 jsonb 는 값이 아니라 식이라 바인딩으로 표현되지 않는다).
     */
    private fun insertRawObservation(
        connection: java.sql.Connection,
        maskedAt: String,
        payloadFields: String = "'{}'::jsonb",
    ): Int =
        connection
            .prepareStatement(
                "INSERT INTO raw_observation (observation_key, source_endpoint, payload, payload_fields, " +
                    "observed_at, release_sha, inserted_at, pii_masked_at) " +
                    "VALUES (?, 'NOTICE_LIST', '{}', $payloadFields, now(), 'ret-test', now(), $maskedAt)",
            ).use { statement ->
                statement.setString(1, "ret-guard-insert")
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
