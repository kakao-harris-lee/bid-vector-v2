package bidvector.adapters.strategy

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.Sql
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.MaxActiveBids
import bidvector.strategy.ScoreRange
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.SQLException

private fun testPolicy(): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        PolicyVersion(EffectiveFrom.Initial, "test-6a3f3-strategy-policy"),
    )

private fun emptyRow(
    maxActiveBids: Int?,
    candidateLimit: Int? = null,
    revision: Int = 1,
): StrategyRow =
    StrategyRow(
        focusCategories = emptyList(),
        focusRegionTerms = emptyList(),
        excludeRegionTerms = emptyList(),
        requiredKeywordTerms = emptyList(),
        excludeKeywordTerms = emptyList(),
        minBudgetWon = null,
        minBudgetCurrency = null,
        minBudgetVat = null,
        minBudgetProvenance = null,
        minBudgetProvenanceDetail = null,
        maxBudgetWon = null,
        maxBudgetCurrency = null,
        maxBudgetVat = null,
        maxBudgetProvenance = null,
        maxBudgetProvenanceDetail = null,
        minimumMatchScore = null,
        minimumProbabilityScore = null,
        bidNowThreshold = null,
        reviewThreshold = null,
        candidateLimit = candidateLimit,
        maxActiveBids = maxActiveBids,
        revision = revision,
    )

/**
 * D-6A3-4 — `StrategyRow`·`Sql`(SELECT·UPSERT 둘)·`JdbcStrategyRepository`의 `maxActiveBids`
 * 왕복을 잠근다. `AppliedStrategy`는 `workflow` 밖에서 만들 수 없어(D-6F1-2와 같은 폐쇄)
 * `JdbcStrategyRepository.save()`(내부에서 `AppliedStrategy`를 요구)를 거치지 않는다 —
 * `bindStrategyRow`+`Sql.UPSERT_STRATEGY`(production 코드, 이 모듈 `internal`)로 직접 쓰고
 * `load()`(production `SELECT_STRATEGY`+`toStrategyRow`+`toDraft`+`validate` 경로)로 읽어
 * SELECT·INSERT 양쪽 실 SQL을 다 잰다 — `JdbcStrategyRepositoryTest`(adapters.persistence,
 * 이 slice out_of_scope)가 쓰는 `EditStrategyWorkflow` 구동 방식과는 다른 경로다.
 */
class StrategyRowMaxActiveBidsTest : PersistenceTestSupport() {
    private fun insertStrategyRow(row: StrategyRow) {
        dataSource().connection.use { connection ->
            connection.prepareStatement(Sql.UPSERT_STRATEGY).use { statement ->
                statement.bindStrategyRow(1, row)
                statement.executeUpdate()
            }
        }
    }

    private fun insertRevisionRow(row: StrategyRow) {
        dataSource().connection.use { connection ->
            connection.prepareStatement(Sql.INSERT_STRATEGY_REVISION).use { statement ->
                statement.bindStrategyRow(1, row)
                statement.executeUpdate()
            }
        }
    }

    private fun maxActiveBidsInRevision(revision: Int): Int? =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT max_active_bids FROM operator_strategy_revision WHERE revision = ?",
                ).use { statement ->
                    statement.setInt(1, revision)
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "revision=$revision 행이 없다" }
                        val value = rs.getInt(1)
                        if (rs.wasNull()) null else value
                    }
                }
        }

    @Test
    fun `maxActiveBids 있는 행이 UPSERT·SELECT 실 SQL 왕복에서 보존된다`() {
        insertStrategyRow(emptyRow(maxActiveBids = 7))

        val strategy = JdbcStrategyRepository(dataSource(), testPolicy()).load()

        strategy.maxActiveBids shouldBe MaxActiveBids(7)
    }

    @Test
    fun `maxActiveBids 없는 행은 null 로 복원된다 — 지어내지 않는다`() {
        insertStrategyRow(emptyRow(maxActiveBids = null))

        val strategy = JdbcStrategyRepository(dataSource(), testPolicy()).load()

        strategy.maxActiveBids shouldBe null
    }

    @Test
    fun `StrategyRow toDraft 는 maxActiveBids 를 그대로 옮긴다`() {
        val row = emptyRow(maxActiveBids = 3)

        row.toDraft().maxActiveBids shouldBe 3
    }

    @Test
    fun `OperatorStrategy toRow 는 maxActiveBids 값을 그대로 옮긴다`() {
        val result = validate(StrategyDraft(maxActiveBids = 9), StrategyRevision(1), testPolicy())
        val strategy = (result as StrategyValidation.Valid).strategy

        strategy.toRow().maxActiveBids shouldBe 9
    }

    /**
     * D-6A3-21(migration-reviewer 위반 1) — `Sql.UPSERT_STRATEGY`의 `ON CONFLICT ... DO
     * UPDATE SET max_active_bids = EXCLUDED.max_active_bids` 줄을 지우는 변이를 잡는다.
     * 두 번째 저장(7 → 3)이 **덮어쓰지 않으면** `load()`가 여전히 7을 낸다 — 그 줄이 있어야
     * RED 없이 3을 낸다.
     */
    @Test
    fun `두 번 저장하면 나중 값이 이긴다 — UPSERT 가 max_active_bids 를 실제로 덮어쓴다`() {
        insertStrategyRow(emptyRow(maxActiveBids = 7, revision = 1))
        insertStrategyRow(emptyRow(maxActiveBids = 3, revision = 2))

        val strategy = JdbcStrategyRepository(dataSource(), testPolicy()).load()

        strategy.maxActiveBids shouldBe MaxActiveBids(3)
    }

    /**
     * D-6A3-21 — 개정 이력 표(`operator_strategy_revision`, append-only)에서도 왕복이
     * 보존된다. 이 표는 UPDATE 가 아니라 INSERT 뿐이라 「덮어쓰기 누락」 변이 표적은 아니지만,
     * `bindStrategyRow`의 컬럼 순서가 `Sql.INSERT_STRATEGY_REVISION`과 어긋나면(예: 다른
     * 열과 자리가 바뀌면) 이 test가 잡는다 — 두 개정을 따로 다시 읽어 각각의 값이 섞이지
     * 않았음을 확인한다.
     */
    @Test
    fun `개정 이력 표는 서로 다른 개정의 maxActiveBids 를 각각 보존한다`() {
        insertRevisionRow(emptyRow(maxActiveBids = 7, revision = 1))
        insertRevisionRow(emptyRow(maxActiveBids = 3, revision = 2))

        maxActiveBidsInRevision(1) shouldBe 7
        maxActiveBidsInRevision(2) shouldBe 3
    }

    /**
     * D-6A3-21(migration-reviewer 권고) — V16 의 `CHECK (max_active_bids IS NULL OR
     * max_active_bids > 0)`을 `>= 0` 으로 느슨하게 하는 변이를 잡는다. `Sql`·`StrategyRow`를
     * 거치지 않고 **직접** `0`을 INSERT 해 DB 자신이 거부하는지를 잰다(Kotlin 도메인 값
     * `MaxActiveBids`의 `require(value > 0)`은 이미 이 조건을 강제하지만, 그건 애플리케이션
     * 층이다 — 이 test는 그 값을 우회해 SQL로 직접 넣어도 DB가 스스로 막는지를 잰다).
     */
    @Test
    fun `max_active_bids 0 은 CHECK 로 거부된다 — DB 직접 INSERT`() {
        val exception =
            shouldThrow<SQLException> {
                dataSource().connection.use { connection ->
                    connection
                        .prepareStatement(
                            "INSERT INTO operator_strategy (id, revision, focus_categories, " +
                                "focus_region_terms, exclude_region_terms, required_keyword_terms, " +
                                "exclude_keyword_terms, max_active_bids) " +
                                "VALUES (1, 1, ?, ?, ?, ?, ?, 0)",
                        ).use { statement ->
                            val empty = connection.createArrayOf("text", emptyArray<String>())
                            statement.setArray(1, empty)
                            statement.setArray(2, empty)
                            statement.setArray(3, empty)
                            statement.setArray(4, empty)
                            statement.setArray(5, empty)
                            statement.executeUpdate()
                        }
                }
            }

        exception.sqlState shouldBe "23514" // PostgreSQL check_violation
    }
}
