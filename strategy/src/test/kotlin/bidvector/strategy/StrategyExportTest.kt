package bidvector.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private fun policy(): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        PolicyVersion(EffectiveFrom.Initial, "test-export"),
    )

private fun won(amount: Long): BaseAmount =
    BaseAmount(amount, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)

/**
 * 모든 필드가 기본값이 **아닌** 초안 — 왕복 등식의 입력이다. 목록·집합 축은 원소 하나로
 * 둔다(중복 제거·정렬이 끼어들면 왕복이 아니라 그 규칙을 재게 된다).
 */
private fun fullyPopulatedDraft(): StrategyDraft =
    StrategyDraft(
        focusCategories = listOf("1234"),
        focusRegionTerms = listOf("서울"),
        excludeRegionTerms = listOf("제주"),
        requiredKeywordTerms = listOf("정보화"),
        excludeKeywordTerms = listOf("유지보수"),
        minBudget = won(1_000_000),
        maxBudget = won(9_000_000),
        minimumMatchScore = BigDecimal("0.10"),
        minimumProbabilityScore = BigDecimal("0.20"),
        bidNowThreshold = BigDecimal("0.90"),
        reviewThreshold = BigDecimal("0.70"),
        candidateLimit = 7,
        maxActiveBids = 3,
    )

/**
 * M6/6A-2b D-6A2b-22(code-review r1 MEDIUM-4) — `toDraft()` 의 **전수성**을 게이트에 건다.
 *
 * 이 함수는 편집의 안전장치다: 필드 하나를 바꾸는 요청이 **나머지 필드를 현재 값 그대로**
 * 싣게 한다. 한 필드를 빠뜨리면 그 필드는 편집할 때마다 조용히 기본값(대개 `null`)으로
 * 되돌아간다 — 컴파일러는 그 누락을 보지 못한다(`StrategyDraft` 생성자의 매개변수가 전부
 * 기본값을 갖는다).
 *
 * 그래서 두 축을 함께 잰다. ① 왕복 등식: 모든 필드를 채운 초안 → `validate` → `toDraft` 가
 * 처음 초안과 **같다**. ② 정의역: 그 입력이 `StrategyDraft` 의 **모든** 필드를 실제로
 * 기본값에서 벗어나게 했는지 리플렉션으로 확인한다 — 새 필드가 생기면 ②가 먼저 붉어지고,
 * `toDraft` 가 그 필드를 빠뜨리면 ①이 붉어진다.
 */
class StrategyExportTest {
    @Test
    fun `모든 필드를 채운 전략의 왕복이 같다 — validate 를 지나 toDraft 로 돌아온다`() {
        val draft = fullyPopulatedDraft()

        val strategy = (validate(draft, StrategyRevision(4), policy()) as StrategyValidation.Valid).strategy

        strategy.toDraft() shouldBe draft
    }

    /**
     * 왕복 입력이 정의역 전부를 덮는지 — **기본값과 다른 값**이 들어갔는지로 잰다.
     * 새 필드를 `StrategyDraft` 에 더하면 그 필드가 기본값 그대로 남아 이 단언이 붉어진다.
     */
    @Test
    fun `왕복 입력이 StrategyDraft 의 모든 필드를 덮는다 — 정의역 등식`() {
        val full = fullyPopulatedDraft()
        val empty = StrategyDraft()

        val untouched =
            StrategyDraft::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .filterNot {
                    java.lang.reflect.Modifier
                        .isStatic(it.modifiers)
                }.onEach { it.isAccessible = true }
                .filter { it.get(full) == it.get(empty) }
                .map { it.name }

        untouched.shouldBeEmpty()
    }
}
