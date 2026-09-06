package bidvector.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.compareKnownVat
import java.math.BigDecimal

/** [validate]가 검사하는 임계치 필드 식별자([StrategyViolation.ScoreOutOfRange]). */
sealed interface ThresholdField {
    data object MinimumMatchScore : ThresholdField

    data object MinimumProbabilityScore : ThresholdField

    data object BidNowThreshold : ThresholdField

    data object ReviewThreshold : ThresholdField
}

/**
 * 전략 편집 원시 입력(D-10) — 전부 optional. 어댑터(M3)가 채운다. `revision`은 여기 없다 —
 * 시스템이 매기는 시퀀스 값이지 운영자 입력이 아니므로 [validate]의 별도 인자로 받는다
 * (판단이 갈린 지점 — D-10 문면은 "전부 optional"이라 하지만 그 대상은 watch·임계·상한 등
 * 운영자가 실제로 편집하는 필드다).
 */
data class StrategyDraft(
    val focusCategories: List<String> = emptyList(),
    val focusRegionTerms: List<String> = emptyList(),
    val excludeRegionTerms: List<String> = emptyList(),
    val requiredKeywordTerms: List<String> = emptyList(),
    val excludeKeywordTerms: List<String> = emptyList(),
    val minBudget: BaseAmount? = null,
    val maxBudget: BaseAmount? = null,
    val minimumMatchScore: BigDecimal? = null,
    val minimumProbabilityScore: BigDecimal? = null,
    val bidNowThreshold: BigDecimal? = null,
    val reviewThreshold: BigDecimal? = null,
    val candidateLimit: Int? = null,
)

/** 전략 값 불변식 위반(⑤) — 구조화 코드, 문장이 아니다(`v2-지침서.md` §4.2). */
sealed interface StrategyViolation {
    data object ReviewAboveBidNow : StrategyViolation

    data object MinBudgetAboveMaxBudget : StrategyViolation

    data class ScoreOutOfRange(
        val field: ThresholdField,
    ) : StrategyViolation

    data object CandidateLimitNotPositive : StrategyViolation

    data class BlankTerm(
        val field: WatchRuleId,
    ) : StrategyViolation

    /** D-6 — 한계 값이 `OperatorDeclared`·`INCLUSIVE`로 구성되지 않아 비교가 성립하지 않는다. */
    data class BudgetLimitNotComparable(
        val field: WatchRuleId,
    ) : StrategyViolation
}

/**
 * validate()의 결과(⑤, `data-dictionary.md` §2.2.6) — `Valid`만 [OperatorStrategy]를
 * 낸다(D-10, 「편집 경로마다 재구현 폐기」).
 */
sealed interface StrategyValidation {
    data class Valid(
        val strategy: OperatorStrategy,
        val policyVersion: PolicyVersion,
    ) : StrategyValidation

    data class Invalid(
        val violations: List<StrategyViolation>,
        val policyVersion: PolicyVersion,
    ) : StrategyValidation {
        init {
            require(violations.isNotEmpty()) { "Invalid.violations는 비어 있을 수 없다" }
        }
    }
}

private fun collectTerms(
    raw: List<String>,
    field: WatchRuleId,
    violations: MutableList<StrategyViolation>,
): List<String> {
    if (raw.any { it.isBlank() }) violations += StrategyViolation.BlankTerm(field)
    return raw.filterNot { it.isBlank() }
}

private fun checkBudgetLimitDeclaration(
    amount: BaseAmount?,
    field: WatchRuleId,
    violations: MutableList<StrategyViolation>,
) {
    if (amount == null) return
    val declaredInclusive =
        amount.provenance == Provenance.OperatorDeclared && amount.vatTreatment == VatTreatment.INCLUSIVE
    if (!declaredInclusive) violations += StrategyViolation.BudgetLimitNotComparable(field)
}

private fun checkBudgetOrder(
    min: BaseAmount?,
    max: BaseAmount?,
    violations: MutableList<StrategyViolation>,
) {
    if (min == null || max == null) return
    val cmp = compareKnownVat(min, max)
    if (cmp is Fact.Known && cmp.value > 0) violations += StrategyViolation.MinBudgetAboveMaxBudget
}

private fun scoreOf(
    raw: BigDecimal?,
    range: ScoreRange,
    field: ThresholdField,
    violations: MutableList<StrategyViolation>,
): Score? {
    if (raw == null) return null
    return when (val fact = Score.of(raw, range)) {
        is Fact.Known -> {
            fact.value
        }

        is Fact.Absent -> {
            violations += StrategyViolation.ScoreOutOfRange(field)
            null
        }
    }
}

private fun buildWatchRules(
    draft: StrategyDraft,
    policy: StrategyPolicyData,
    violations: MutableList<StrategyViolation>,
): WatchRules {
    val focusCategories =
        collectTerms(draft.focusCategories, WatchRuleId.FocusCategory, violations).map(::CategoryCode).toSet()
    val focusRegionTerms = collectTerms(draft.focusRegionTerms, WatchRuleId.FocusRegion, violations)
    val excludeRegionTerms = collectTerms(draft.excludeRegionTerms, WatchRuleId.ExcludeRegion, violations)
    val requiredKeywordTerms = collectTerms(draft.requiredKeywordTerms, WatchRuleId.RequiredKeyword, violations)
    val excludeKeywordTerms = collectTerms(draft.excludeKeywordTerms, WatchRuleId.ExcludeKeyword, violations)

    checkBudgetLimitDeclaration(draft.minBudget, WatchRuleId.MinBudget, violations)
    checkBudgetLimitDeclaration(draft.maxBudget, WatchRuleId.MaxBudget, violations)
    checkBudgetOrder(draft.minBudget, draft.maxBudget, violations)
    val budget = BudgetBound(draft.minBudget, draft.maxBudget, policy.budgetBoundInclusivity)

    return WatchRules(
        focusCategories,
        focusRegionTerms,
        excludeRegionTerms,
        requiredKeywordTerms,
        excludeKeywordTerms,
        budget,
    )
}

private fun buildActionThresholds(
    draft: StrategyDraft,
    policy: StrategyPolicyData,
    violations: MutableList<StrategyViolation>,
): ActionThresholds {
    val minimumMatchScore =
        scoreOf(draft.minimumMatchScore, policy.matchScoreRange, ThresholdField.MinimumMatchScore, violations)
            ?.let(::MatchScore)
    val minimumProbabilityScore =
        scoreOf(
            draft.minimumProbabilityScore,
            policy.probabilityScoreRange,
            ThresholdField.MinimumProbabilityScore,
            violations,
        )?.let(::ProbabilityScore)
    val bidNowThreshold =
        scoreOf(draft.bidNowThreshold, policy.priorityScoreRange, ThresholdField.BidNowThreshold, violations)
            ?.let(::PriorityScore)
    val reviewThreshold =
        scoreOf(draft.reviewThreshold, policy.priorityScoreRange, ThresholdField.ReviewThreshold, violations)
            ?.let(::PriorityScore)

    val reviewAboveBidNow =
        bidNowThreshold != null && reviewThreshold != null && reviewThreshold.score.value > bidNowThreshold.score.value
    if (reviewAboveBidNow) {
        violations += StrategyViolation.ReviewAboveBidNow
    }
    return ActionThresholds(minimumMatchScore, minimumProbabilityScore, bidNowThreshold, reviewThreshold)
}

private fun buildCandidateLimit(
    draft: StrategyDraft,
    violations: MutableList<StrategyViolation>,
): CandidateLimit? =
    draft.candidateLimit?.let { raw ->
        if (raw <= 0) {
            violations += StrategyViolation.CandidateLimitNotPositive
            null
        } else {
            CandidateLimit(raw)
        }
    }

/**
 * 전략 값 validation의 유일한 진입점(D-10, ⑤) — 위반을 전부 모아 낸다(첫 위반에서
 * 멈추지 않는다 — 편집 화면이 한 번에 보여야 한다). [OperatorStrategy]는 이 함수를
 * 거쳐서만 생긴다.
 */
fun validate(
    draft: StrategyDraft,
    revision: StrategyRevision,
    policy: Resolution.Resolved<StrategyPolicyData>,
): StrategyValidation {
    val violations = mutableListOf<StrategyViolation>()

    val watchRules = buildWatchRules(draft, policy.value, violations)
    val actionThresholds = buildActionThresholds(draft, policy.value, violations)
    val candidateLimit = buildCandidateLimit(draft, violations)

    if (violations.isNotEmpty()) return StrategyValidation.Invalid(violations, policy.version)

    val strategy = OperatorStrategy(watchRules, actionThresholds, candidateLimit, revision)
    return StrategyValidation.Valid(strategy, policy.version)
}
