package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.time.Instant

/** 적재자가 마스킹을 피하려고 쓸 수 있었던 미래 시각 — 그 행은 `inserted_at < cutoff` 에 영원히 안 든다. */
private const val FAR_FUTURE = "2999-01-01T00:00:00Z"

/** 소유자가 묵은 행을 심을 때 쓰는 시각 — fixture 전체가 기대는 성질이다(경계 ±1초를 고르려면 필요하다). */
private val OWNER_SEEDED_AT: Instant = Instant.parse("2020-01-01T00:00:00Z")

/**
 * **vr R2-F2(b)** — 「적재 시각은 DB 시계가 소유한다」를 잰다.
 *
 * `inserted_at` 은 `DEFAULT now()` 일 뿐이었고, 그래서 앱 역할이 `inserted_at = '2999-01-01'` 로
 * INSERT 하면 그 행은 **마스킹 후보 조회에 영원히 들지 않는다**(표지를 미리 찍는 r1 F-4 와 같은 축이고,
 * 그쪽은 이미 구조로 닫혔다). 출하 수집 경로는 그 열을 **쓰지 않으므로**(`Sql.INSERT_RAW_OBSERVATION`
 * 의 열 목록 여섯에 없다 — 실측) DB 시계를 강제하는 데 드는 비용이 0 이다.
 *
 * 소유자에게는 강제하지 않는다: 그 자격은 이 slice 의 비방어 경계이고(`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`),
 * 묵은 행을 심는 fixture·복원 절차가 그 자유에 기댄다. 아래 마지막 test 가 그 경계를 음성 대조로 둔다.
 */
class InsertedAtOwnershipTest : PersistenceTestSupport() {
    @Test
    fun `앱 역할이 미래 적재 시각으로 넣은 행은 그 시각을 갖지 못한다 — DB 시계가 찍는다`() {
        appConnection().use { connection ->
            insertAs(connection, key = "ret-future", insertedAt = "'$FAR_FUTURE'") shouldBe 1

            insertedAtOf(connection, "ret-future").isBefore(Instant.parse("2100-01-01T00:00:00Z")) shouldBe true
        }
    }

    @Test
    fun `그 행은 기한이 지나면 마스킹 후보가 된다 — 미래 시각으로 영구히 빠지지 않는다`() {
        appConnection().use { connection ->
            insertAs(connection, key = "ret-future", insertedAt = "'$FAR_FUTURE'")

            // cutoff 를 **적재 직후보다 뒤**로 두면(= DB 시계가 찍은 값보다 미래) 후보에 든다.
            candidateCountAt(connection, cutoff = "now() + interval '1 second'") shouldBe 1
        }
    }

    /** 공허 대조 — 같은 질의가 **기한 안쪽** cutoff 에서는 0 을 낸다(술어가 실제로 시각을 본다). */
    @Test
    fun `공허 대조 — 기한 안쪽 cutoff 에서는 후보가 0 이다`() {
        appConnection().use { connection ->
            insertAs(connection, key = "ret-future", insertedAt = "'$FAR_FUTURE'")

            candidateCountAt(connection, cutoff = "now() - interval '1 day'") shouldBe 0
        }
    }

    /** 음성 대조 — **소유자**는 묵은 적재 시각을 그대로 심는다(fixture 가 기대는 성질). */
    @Test
    fun `소유자가 심은 묵은 적재 시각은 그대로 남는다 — 경계 fixture 가 선다`() {
        dataSource().connection.use { connection ->
            connection.autoCommit = false
            try {
                insertAs(connection, key = "ret-owner-seed", insertedAt = "'$OWNER_SEEDED_AT'") shouldBe 1

                insertedAtOf(connection, "ret-owner-seed") shouldBe OWNER_SEEDED_AT
            } finally {
                connection.rollback()
            }
        }
    }

    private fun insertAs(
        connection: Connection,
        key: String,
        insertedAt: String,
    ): Int =
        connection
            .prepareStatement(
                "INSERT INTO raw_observation (observation_key, source_endpoint, payload, payload_fields, " +
                    "observed_at, release_sha, inserted_at) " +
                    "VALUES (?, 'NOTICE_LIST', '{}', '{}'::jsonb, now(), 'ret-test', $insertedAt)",
            ).use { statement ->
                statement.setString(1, key)
                statement.executeUpdate()
            }

    private fun insertedAtOf(
        connection: Connection,
        key: String,
    ): Instant =
        connection
            .prepareStatement("SELECT inserted_at FROM raw_observation WHERE observation_key = ?")
            .use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "행이 없다: $key" }
                    rs.getTimestamp(1).toInstant()
                }
            }

    private fun candidateCountAt(
        connection: Connection,
        cutoff: String,
    ): Int =
        connection
            .createStatement()
            .use { statement ->
                val sql =
                    "SELECT count(*) FROM raw_observation " +
                        "WHERE inserted_at < $cutoff AND pii_masked_at IS NULL " +
                        "AND observation_key = 'ret-future'"
                statement.executeQuery(sql).use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
}
