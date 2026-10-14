package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val TIMESTAMPTZ = "timestamp with time zone"

/**
 * P-2 — 분류 레지스트리는 **스키마 정본과 집합 등식**이다. 손 목록이면 V19 가 표를 더할 때
 * 조용히 빠지고, 빠진 표는 「파기 대상이 아니다」와 구별되지 않는다.
 *
 * 등식의 상대는 **DB 카탈로그**다(`CleanMigrationTest` 의 `private val expectedTables` 가
 * 아니다 — 그 값은 클래스 밖에서 볼 수 없고, 애초에 그 test 가 「카탈로그 == expectedTables」를
 * 이미 잠근다. 카탈로그를 상대로 재면 두 등식이 겹쳐 레지스트리가 스키마 스냅숏 래칫에
 * 전이적으로 묶인다).
 */
class RetentionRegistrySchemaEqualityTest : PersistenceTestSupport() {
    @Test
    fun `분류 레지스트리의 표 집합은 스키마의 표 집합과 같다`() {
        RETENTION_TABLE_RULES.map { it.table } shouldContainExactlyInAnyOrder storedTables()
    }

    @Test
    fun `분류에 중복 표가 없다`() {
        RETENTION_TABLE_RULES.map { it.table }.distinct().size shouldBe RETENTION_TABLE_RULES.size
    }

    @Test
    fun `fixture 는 분류된 표를 전수 심는다 — 새 표가 분류만 되고 재지 않는 일이 없다`() {
        RETENTION_SEEDS.map { it.table } shouldContainExactlyInAnyOrder RETENTION_TABLE_RULES.map { it.table }
    }

    /**
     * 기준 시각 열이 **NOT NULL 이어야 한다** — nullable 이면 `< cutoff` 술어가 NULL 행을
     * 조용히 건너뛰어 그 행은 영원히 남는다(「90일이 지나면 사라진다」가 거짓이 된다).
     */
    @Test
    fun `파기·마스킹 대상의 기준 시각 열은 실존하고 NOT NULL 인 TIMESTAMPTZ 다`() {
        val columns = timestampColumns()

        val violations =
            RETENTION_TABLE_RULES.mapNotNull { rule ->
                val column =
                    when (rule) {
                        is RetentionRule.Purge -> rule.insertedAtColumn
                        is RetentionRule.MaskPii -> rule.insertedAtColumn
                        // CASCADE 자식도 기한 가드가 이 열을 읽는다(D-6B3-3 ②) — nullable 이면
                        // 그 자식은 가드에 걸리지 않고 부모와 함께 사라진다.
                        is RetentionRule.CascadeWithParent -> rule.insertedAtColumn
                        is RetentionRule.Exempt -> null
                    } ?: return@mapNotNull null
                val observed = columns[rule.table to column]
                if (observed == TIMESTAMPTZ to false) null else "${rule.table}.$column=$observed"
            }

        violations shouldBe emptyList()
    }

    @Test
    fun `마스킹 표지 열은 실존하고 nullable 이다 — NULL 이 「아직 마스킹 안 됨」을 뜻한다`() {
        val columns = timestampColumns()

        val violations =
            RETENTION_TABLE_RULES
                .filterIsInstance<RetentionRule.MaskPii>()
                .mapNotNull { rule ->
                    val observed = columns[rule.table to rule.maskedAtColumn]
                    if (observed == TIMESTAMPTZ to true) null else "${rule.table}.${rule.maskedAtColumn}=$observed"
                }

        violations shouldBe emptyList()
    }

    @Test
    fun `마스킹 규칙의 키·마스킹·투영 열이 모두 실존한다`() {
        val present = allColumns()

        val violations =
            RETENTION_TABLE_RULES
                .filterIsInstance<RetentionRule.MaskPii>()
                .flatMap { rule ->
                    (listOf(rule.keyColumn) + rule.maskedColumns)
                        .filterNot { (rule.table to it) in present }
                        .map { "${rule.table}.$it" }
                }

        violations shouldBe emptyList()
    }

    /**
     * **마스킹 개념이 계약에 닿는가**(D-6B3-3 ①) — 개념에 계약이 없으면 지울 키 집합이 조용히
     * 비고, 「업체명을 지운다」가 아무 일도 하지 않으면서 초록이다. 이 test 는 그 공허를 잰다.
     */
    @Test
    fun `마스킹 개념은 모두 계약 rawName 으로 풀린다 — 빈 집합이 아니다`() {
        val rules = RETENTION_TABLE_RULES.filterIsInstance<RetentionRule.MaskPii>()
        rules.shouldNotBeEmpty()

        rules.forEach { rule ->
            // 개념마다 계약이 없으면 `maskedRawNamesOf` 가 던진다 — 그 호출이 곧 단언이다.
            val names = maskedRawNamesOf(rule, testFieldContracts())
            (names.size >= rule.maskedConcepts.size) shouldBe true
        }
    }

    /**
     * cascade 분류는 **카탈로그가 뒷받침해야 한다** — 「부모와 함께 사라진다」가 FK 의 성질이
     * 아니면 그 표는 아무도 지우지 않는 채로 분류만 받는다.
     */
    @Test
    fun `cascade 분류의 표는 선언한 부모를 ON DELETE CASCADE FK 로 참조한다`() {
        val edges = dataSource().connection.use(::readForeignKeyEdges)

        val violations =
            RETENTION_TABLE_RULES
                .filterIsInstance<RetentionRule.CascadeWithParent>()
                .filterNot { rule ->
                    edges.any { it.child == rule.table && it.parent == rule.parentTable && it.cascade }
                }.map { "${it.table} -> ${it.parentTable}" }

        violations shouldBe emptyList()
    }

    /**
     * 복합 FK 의 열 쌍이 **짝으로** 복원되는지 — `information_schema` 경유 조회가 cross product
     * 를 주던 자리다(`CleanMigrationTest` 축6 KDoc). 짝이 틀리면 자식 가드 술어가 엉뚱한 열을
     * 비교하고, 그 술어는 **늘 참**이 되어 부모를 지우지 못하거나 **늘 거짓**이 되어 FK 위반을 낸다.
     */
    @Test
    fun `복합 FK 의 열 쌍은 순서대로 복원된다`() {
        val edges = dataSource().connection.use(::readForeignKeyEdges)

        val reservePrice =
            edges.single { it.child == "opening_reserve_price" && it.parent == "opening_result" }

        reservePrice.columnPairs shouldBe
            listOf("notice_number" to "notice_number", "notice_round" to "notice_round")
    }

    /**
     * `outbox` 상태 어휘가 늘면 **분류가 그것을 모르는 채로** 남는다 — 새 상태가 종단인지
     * 아닌지는 결정이 필요한 사안이고(A-4), 이 등식이 그 결정을 요구한다.
     */
    @Test
    fun `outbox 상태 어휘는 CHECK 가 정한 다섯과 같다`() {
        checkVocabularyOf("outbox", "state") shouldContainExactlyInAnyOrder OUTBOX_STATE_VOCABULARY
    }

    @Test
    fun `파기하는 outbox 상태는 그 어휘의 부분집합이다`() {
        val condition =
            RETENTION_TABLE_RULES
                .filterIsInstance<RetentionRule.Purge>()
                .single { it.table == "outbox" }
                .condition as PurgeCondition.TerminalStates

        (condition.values - checkVocabularyOf("outbox", condition.column).toSet()) shouldBe emptySet()
    }

    private fun storedTables(): List<String> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(PUBLIC_TABLES_SQL).use { rs ->
                    val tables = mutableListOf<String>()
                    while (rs.next()) tables += rs.getString(1)
                    tables
                }
            }
        }

    /** (표, 열) → (자료형, nullable) — 시각 열만 추린다. */
    private fun timestampColumns(): Map<Pair<String, String>, Pair<String, Boolean>> {
        val observed = mutableMapOf<Pair<String, String>, Pair<String, Boolean>>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT table_name, column_name, data_type, is_nullable " +
                            "FROM information_schema.columns WHERE table_schema = 'public' " +
                            "AND data_type LIKE 'timestamp%'",
                    ).use { rs ->
                        while (rs.next()) {
                            observed[rs.getString("table_name") to rs.getString("column_name")] =
                                rs.getString("data_type") to (rs.getString("is_nullable") == "YES")
                        }
                    }
            }
        }
        return observed
    }

    private fun allColumns(): Set<Pair<String, String>> {
        val present = mutableSetOf<Pair<String, String>>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT table_name, column_name FROM information_schema.columns " +
                            "WHERE table_schema = 'public'",
                    ).use { rs ->
                        while (rs.next()) present += rs.getString(1) to rs.getString(2)
                    }
            }
        }
        return present
    }

    /** 열 하나를 가두는 `CHECK (… IN (…))` 의 어휘 — 상수 목록을 제약 정의에서 읽는다. */
    private fun checkVocabularyOf(
        table: String,
        column: String,
    ): List<String> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT pg_get_constraintdef(oid) AS def FROM pg_constraint " +
                        "WHERE contype = 'c' AND conrelid = ?::regclass",
                ).use { statement ->
                    statement.setString(1, table)
                    statement.executeQuery().use { rs ->
                        val definitions = mutableListOf<String>()
                        while (rs.next()) definitions += rs.getString("def")
                        val vocabulary =
                            definitions.single { it.contains("$column = ANY") || it.contains("($column)::text = ANY") }
                        Regex("'([^']*)'").findAll(vocabulary).map { it.groupValues[1] }.toList()
                    }
                }
        }
}
