package bidvector.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.PolicyVersion
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
 * 낸다(D-10, 「편집 경로마다 재구현 폐기」). `validate` 함수 자신은 별도 커밋(validation)이
 * 낸다.
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
