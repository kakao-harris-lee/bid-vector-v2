package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.time.Instant

private val SEED_AT = Instant.parse("2026-01-01T00:00:00Z")
private const val SEED_KEY = "ret-enum"
private const val APPEND_ONLY_SQLSTATE = "P0001"

/**
 * V18 관문 ⑤ — 「마스킹 열 밖은 불변」을 **열을 손으로 고르지 않고** 잰다.
 *
 * 기존 자리(`RawObservationMaskingGuardTest`)는 열 하나(`observed_at`)를 골라 거부를 보였다.
 * 그것은 「그 열이 막힌다」만 잰다: V19 가 열을 더하면 그 새 열은 아무도 재지 않는다(트리거의
 * 술어는 행 전체 비교라 자동으로 포함되지만, **포함된다는 사실**이 실측되지 않는다 — 술어를
 * 열 이름 열거로 바꾸는 변경이 조용히 초록이 된다).
 *
 * 그래서 열 집합을 **카탈로그에서 전수 열거**하고, 마스킹 열(레지스트리가 말한다)과 표지 열을
 * 뺀 모든 열에 대해 「마스킹 UPDATE 에 그 열 변경을 끼워 넣으면 거부된다」를 각각 잰다. 값은
 * 자료형에서 짓고, **모르는 자료형이 나오면 실패한다** — 새 자료형의 열이 들어오면 이 test 가
 * 「어떻게 변이할지 모른다」로 멈춘다(조용히 건너뛰지 않는다).
 */
class MaskingColumnEnumerationTest : PersistenceTestSupport() {
    private val rule = RETENTION_TABLE_RULES.filterIsInstance<RetentionRule.MaskPii>().single()

    @BeforeEach
    fun seed() {
        RetentionFixture(dataSource()).seedAll("enum", SEED_AT)
    }

    @Test
    fun `공허 대조 — 마스킹 열 밖의 열이 전수 수집된다`() {
        immutableColumns().shouldNotBeEmpty()
    }

    @Test
    fun `마스킹 열과 표지 열 밖의 모든 열은 마스킹 UPDATE 에서 불변이다`() {
        val passed =
            immutableColumns().filterNot { (column, type) ->
                rejectsMutationOf(column, mutatedValueOf(column, type))
            }

        passed shouldBe emptyList()
    }

    /** 마스킹 UPDATE 와 함께 [column] 을 바꾸면 거부되는가 — 롤백하는 트랜잭션 안에서 잰다. */
    private fun rejectsMutationOf(
        column: String,
        value: String,
    ): Boolean =
        dataSource().connection.use { connection ->
            connection.autoCommit = false
            try {
                connection
                    .prepareStatement(
                        "UPDATE raw_observation SET ${quoteIdent(rule.rawColumn)} = '{}', " +
                            "${quoteIdent(rule.maskedAtColumn)} = now(), ${quoteIdent(column)} = $value " +
                            "WHERE ${quoteIdent(rule.keyColumn)} = ?",
                    ).use { statement ->
                        statement.setString(1, SEED_KEY)
                        statement.executeUpdate()
                    }
                false
            } catch (rejected: PSQLException) {
                rejected.sqlState == APPEND_ONLY_SQLSTATE
            } finally {
                connection.rollback()
            }
        }

    /**
     * **다른 값**을 짓는다(`mutation-must-replace` — 같은 값을 쓰면 트리거의 행 비교가 통과하고
     * 이 test 가 「막혔다」를 보지 못한다). 키 열은 PK 라 다른 값이 곧 다른 행을 가리킨다.
     */
    private fun mutatedValueOf(
        column: String,
        type: String,
    ): String =
        when (type) {
            "text" -> "'RET-ENUM-MUTATED'"
            "timestamp with time zone" -> "now() + interval '1 second'"
            "jsonb" -> """'{"mutated":1}'::jsonb"""
            else -> error("변이 값을 모르는 자료형이다: $column $type")
        }

    /** 마스킹 열·표지 열을 뺀 열 전수(이름, 자료형) — 집합의 출처는 카탈로그다. */
    private fun immutableColumns(): List<Pair<String, String>> {
        val excluded = rule.maskedColumns + rule.maskedAtColumn
        val columns = mutableListOf<Pair<String, String>>()
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT column_name, data_type FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position",
                ).use { statement ->
                    statement.setString(1, rule.table)
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("column_name")
                            if (name !in excluded) columns += name to rs.getString("data_type")
                        }
                    }
                }
        }
        return columns
    }
}
