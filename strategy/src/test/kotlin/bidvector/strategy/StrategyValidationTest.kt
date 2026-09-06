package bidvector.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val TEST_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-strategy-policy")

private fun policyOf(
    matchScoreRange: ScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
    probabilityScoreRange: ScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
    priorityScoreRange: ScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
    budgetBoundInclusivity: BudgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(matchScoreRange, probabilityScoreRange, priorityScoreRange, budgetBoundInclusivity),
        TEST_VERSION,
    )

private fun baseAmount(
    won: Long,
    vat: VatTreatment = VatTreatment.INCLUSIVE,
    provenance: Provenance = Provenance.OperatorDeclared,
): BaseAmount = BaseAmount(won, Currency.KRW, vat, provenance)

private val REV = StrategyRevision(1)

/**
 * M1/1E ⑤⑥⑦⑧ — 전략 값 validation. STR-03 acceptance 셋을 `reports/evidence/m1/1e/scope.md`
 * D-3 승인(decision 30) 문면 그대로 옮긴다.
 */
class StrategyValidationTest {
    @Test
    fun `STR-03 1 review 가 bidNow 보다 크면 거부된다`() {
        val draft = StrategyDraft(bidNowThreshold = BigDecimal("0.5"), reviewThreshold = BigDecimal("0.6"))

        val result = validate(draft, REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Invalid>()
        result.violations shouldBe listOf(StrategyViolation.ReviewAboveBidNow)
    }

    @Test
    fun `STR-03 1 review 와 bidNow 가 같으면 허용된다 — legacy-behavior 등호 통과`() {
        val draft = StrategyDraft(bidNowThreshold = BigDecimal("0.5"), reviewThreshold = BigDecimal("0.5"))

        val result = validate(draft, REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Valid>()
    }

    @Test
    fun `STR-03 2 min_budget 이 max_budget 보다 크면 거부된다`() {
        val draft = StrategyDraft(minBudget = baseAmount(2_000_000), maxBudget = baseAmount(1_000_000))

        val result = validate(draft, REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Invalid>()
        result.violations shouldBe listOf(StrategyViolation.MinBudgetAboveMaxBudget)
    }

    @Test
    fun `STR-03 3 임계치만 바꾸고 watch 필드가 비어 있으면 설정됐는가와 감시 범위를 좁히는가가 다른 값이다`() {
        val draft = StrategyDraft(bidNowThreshold = BigDecimal("0.7"))

        val result = validate(draft, REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Valid>()
        val strategy = result.strategy
        strategy.isConfigured() shouldBe true
        strategy.watchRules.isEmpty() shouldBe true
    }

    @Test
    fun `⑥ watch 필드도 임계치도 전혀 없는 draft 는 설정되지 않았다고 답한다`() {
        val result = validate(StrategyDraft(), REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Valid>()
        result.strategy.isConfigured() shouldBe false
        result.strategy.watchRules.isEmpty() shouldBe true
    }

    @Test
    fun `⑤ 점수가 범위를 벗어나면 ScoreOutOfRange 를 낸다`() {
        val narrowPolicy = policyOf(priorityScoreRange = ScoreRange(BigDecimal("0.2"), BigDecimal("0.8")))
        val draft = StrategyDraft(bidNowThreshold = BigDecimal("0.9"))

        val result = validate(draft, REV, narrowPolicy)

        result.shouldBeInstanceOf<StrategyValidation.Invalid>()
        result.violations shouldBe listOf(StrategyViolation.ScoreOutOfRange(ThresholdField.BidNowThreshold))
    }

    @Test
    fun `⑤ 위반을 전부 모아 낸다 — 첫 위반에서 멈추지 않는다`() {
        val draft =
            StrategyDraft(
                bidNowThreshold = BigDecimal("0.5"),
                reviewThreshold = BigDecimal("0.6"),
                minBudget = baseAmount(2_000_000),
                maxBudget = baseAmount(1_000_000),
                candidateLimit = 0,
            )

        val result = validate(draft, REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Invalid>()
        result.violations shouldBe
            listOf(
                StrategyViolation.MinBudgetAboveMaxBudget,
                StrategyViolation.ReviewAboveBidNow,
                StrategyViolation.CandidateLimitNotPositive,
            )
    }

    @Test
    fun `⑦ 후보 상한이 0 이하면 CandidateLimitNotPositive 다`() {
        val result = validate(StrategyDraft(candidateLimit = 0), REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Invalid>()
        result.violations shouldBe listOf(StrategyViolation.CandidateLimitNotPositive)
    }

    @Test
    fun `빈 문자열 키워드는 BlankTerm 위반이다`() {
        val result = validate(StrategyDraft(requiredKeywordTerms = listOf("유지보수", "  ")), REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Invalid>()
        result.violations shouldBe listOf(StrategyViolation.BlankTerm(WatchRuleId.RequiredKeyword))
    }

    @Test
    fun `D-6 예산 한계가 OperatorDeclared 아니면 BudgetLimitNotComparable 이다`() {
        val draft = StrategyDraft(minBudget = baseAmount(1_000_000, provenance = Provenance.Undeclared))

        val result = validate(draft, REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Invalid>()
        result.violations shouldBe listOf(StrategyViolation.BudgetLimitNotComparable(WatchRuleId.MinBudget))
    }

    @Test
    fun `D-6 예산 한계가 UNKNOWN 과세 처리면 BudgetLimitNotComparable 이다`() {
        val draft = StrategyDraft(maxBudget = baseAmount(1_000_000, vat = VatTreatment.UNKNOWN))

        val result = validate(draft, REV, policyOf())

        result.shouldBeInstanceOf<StrategyValidation.Invalid>()
        result.violations shouldBe listOf(StrategyViolation.BudgetLimitNotComparable(WatchRuleId.MaxBudget))
    }

    @Test
    fun `유효한 draft 는 policyVersion 을 그대로 싣는다`() {
        val policy = policyOf()
        val result = validate(StrategyDraft(focusCategories = listOf("service")), REV, policy)

        result.shouldBeInstanceOf<StrategyValidation.Valid>()
        result.policyVersion shouldBe policy.version
    }

    @Test
    fun `OperatorStrategy 는 validate 를 거쳐서만 생긴다 — 같은 모듈 안에서도 internal 생성자를 직접 쓸 수 있다`() {
        // 위협 모델 우회 (2) — internal 생성자는 모듈 밖 조립을 막지, 같은 모듈 test 의 직접
        // 호출까지 막지는 않는다(1B·1C 와 같은 한계, evidence 등재 대상).
        val watchRules = WatchRules.empty(BudgetBoundInclusivity.Inclusive)
        val strategy = OperatorStrategy(watchRules, ActionThresholds.empty(), null, REV)

        strategy.isConfigured() shouldBe false
    }

    @Test
    fun `Invalid violations 는 비어 있으면 구성 시점에 거부된다`() {
        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            StrategyValidation.Invalid(emptyList(), TEST_VERSION)
        }
    }
}
