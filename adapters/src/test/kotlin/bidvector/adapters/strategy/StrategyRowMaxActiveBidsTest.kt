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
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

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
}
