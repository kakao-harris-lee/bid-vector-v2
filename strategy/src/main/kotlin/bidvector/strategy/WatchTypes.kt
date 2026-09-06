package bidvector.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.ReasonCode

/**
 * 예산 한계 경계 포함성(`data-dictionary.md` §1.4.3 「경계 포함성(이상/초과)을 값과 함께
 * 선언한다」, D-15) — 초기값은 [Inclusive](legacy `budget == min`·`budget == max` 통과,
 * 스카우트 §1.3 `:65-68`). 정책 데이터 슬롯([StrategyPolicyData.budgetBoundInclusivity]) —
 * main 이 비교 연산자로 굳히지 않는다.
 */
sealed interface BudgetBoundInclusivity {
    data object Inclusive : BudgetBoundInclusivity

    data object Exclusive : BudgetBoundInclusivity
}

/**
 * 감시 예산 한계(D-6) — `min`·`max` 각각 `null`이면 「규칙 없음」이다. `0`원 한계는
 * 「무제한」 sentinel이 아니라 실제 한계다(scope.md 위협 모델 (g)) — `null`과
 * `BaseAmount(0, …)`은 다른 결과를 낸다. 한계 값의 과세 처리(`OperatorDeclared`·
 * `INCLUSIVE`)는 이 타입이 구조적으로 강제하지 않는다 — [bidvector.strategy.validate]가
 * 그 요건을 검사해 `StrategyViolation`으로 낸다(D-6 「validation 거부」).
 */
data class BudgetBound(
    val min: BaseAmount?,
    val max: BaseAmount?,
    val inclusivity: BudgetBoundInclusivity,
)

/**
 * 감시 predicate 커널 입력(D-5) — 어댑터(M3)가 두 텍스트를 조립해 넘긴다. 커널은
 * 조립하지 않는다. `baseAmount`가 `Fact.Absent`면 예산 규칙이 설정돼 있을 때만
 * [WatchUndeterminableReason.BaseAmountAbsent]로 이어진다(예산 규칙이 없으면 그 축을
 * 보지 않는다 — D-8).
 */
data class WatchSubject(
    val categories: Set<CategoryCode>,
    val keywordText: KeywordScopeText,
    val fullText: FullScopeText,
    val baseAmount: Fact<BaseAmount>,
)

/** 감시 필드 일곱(D-7) — [WatchVerdict.Passed.matched]·[WatchVerdict.Rejected.failed]가 참조하는 어휘. */
sealed interface WatchRuleId {
    data object FocusCategory : WatchRuleId

    data object FocusRegion : WatchRuleId

    data object ExcludeRegion : WatchRuleId

    data object RequiredKeyword : WatchRuleId

    data object ExcludeKeyword : WatchRuleId

    data object MinBudget : WatchRuleId

    data object MaxBudget : WatchRuleId
}

/** [WatchVerdict.Undeterminable]의 사유(D-8). */
sealed interface WatchUndeterminableReason {
    data class BaseAmountAbsent(
        val reason: ReasonCode,
    ) : WatchUndeterminableReason

    /** `compareKnownVat`의 `Absent` 사유(`VAT_TREATMENT_MISMATCH`·`UNDECLARED_PROVENANCE`)를 그대로 싣는다. */
    data class BudgetNotComparable(
        val reason: ReasonCode,
    ) : WatchUndeterminableReason
}

/**
 * 감시 predicate 판정 결과(③, `data-dictionary.md` §2.2.6) — boolean이 아니다. 「게이트
 * 없음」(`NoGate`)과 「모든 공고 통과」(규칙이 하나 이상이고 전부 만족한 `Passed`)가
 * 결과 타입에서 갈린다(STR-01 acceptance 셋째).
 */
sealed interface WatchVerdict {
    /** 유일한 생성 경로는 [WatchRules.evaluate]다(`internal constructor`). */
    @ConsistentCopyVisibility
    data class Passed internal constructor(
        val matched: Set<WatchRuleId>,
    ) : WatchVerdict

    data class Rejected(
        val failed: Set<WatchRuleId>,
    ) : WatchVerdict {
        init {
            require(failed.isNotEmpty()) { "Rejected.failed는 비어 있을 수 없다" }
        }
    }

    data object NoGate : WatchVerdict

    data class Undeterminable(
        val reason: WatchUndeterminableReason,
    ) : WatchVerdict
}
