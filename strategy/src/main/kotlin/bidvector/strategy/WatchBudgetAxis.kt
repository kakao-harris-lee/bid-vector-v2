package bidvector.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.compareKnownVat

private fun boundFails(
    cmp: Int,
    inclusivity: BudgetBoundInclusivity,
    failIfBelow: Boolean,
): Boolean =
    when (inclusivity) {
        BudgetBoundInclusivity.Inclusive -> if (failIfBelow) cmp < 0 else cmp > 0
        BudgetBoundInclusivity.Exclusive -> if (failIfBelow) cmp <= 0 else cmp >= 0
    }

/** 한계 하나(min 또는 max)의 판정. `limit`이 `null`이면 그 한계는 규칙이 없다는 뜻이라 `null`을 낸다. */
private fun singleBoundOutcome(
    amount: BaseAmount,
    limit: BaseAmount?,
    inclusivity: BudgetBoundInclusivity,
    failIfBelow: Boolean,
    rule: WatchRuleId,
): AxisOutcome? {
    if (limit == null) return null
    return when (val cmp = compareKnownVat(amount, limit)) {
        is Fact.Absent -> {
            AxisOutcome.Undeterminable(WatchUndeterminableReason.BudgetNotComparable(cmp.reason))
        }

        is Fact.Known -> {
            if (boundFails(cmp.value, inclusivity, failIfBelow)) {
                AxisOutcome.Failed(rule)
            } else {
                AxisOutcome.Passed(setOf(rule))
            }
        }
    }
}

private fun combineBoundOutcomes(outcomes: List<AxisOutcome>): AxisOutcome {
    val blocking = outcomes.firstOrNull { it is AxisOutcome.Failed || it is AxisOutcome.Undeterminable }
    return blocking ?: AxisOutcome.Passed(outcomes.filterIsInstance<AxisOutcome.Passed>().flatMap { it.rules }.toSet())
}

/**
 * 예산 축 판정(④, D-6·D-8) — `compareKnownVat(BaseAmount, BaseAmount)` 하나만 쓴다.
 * 공고 금액이 `Fact.Absent`면 [WatchUndeterminableReason.BaseAmountAbsent], 한계와
 * 비교가 성립하지 않으면(과세 처리 불일치 등) [WatchUndeterminableReason.BudgetNotComparable]이다.
 */
internal fun budgetAxisOutcome(
    rules: WatchRules,
    subject: WatchSubject,
): AxisOutcome {
    val bound = rules.budget
    if (bound.min == null && bound.max == null) return AxisOutcome.NotConfigured
    return when (val amount = subject.baseAmount) {
        is Fact.Absent -> {
            AxisOutcome.Undeterminable(WatchUndeterminableReason.BaseAmountAbsent(amount.reason))
        }

        is Fact.Known -> {
            val minOutcome =
                singleBoundOutcome(
                    amount.value,
                    bound.min,
                    bound.inclusivity,
                    failIfBelow = true,
                    WatchRuleId.MinBudget,
                )
            val maxOutcome =
                singleBoundOutcome(
                    amount.value,
                    bound.max,
                    bound.inclusivity,
                    failIfBelow = false,
                    WatchRuleId.MaxBudget,
                )
            combineBoundOutcomes(listOfNotNull(minOutcome, maxOutcome))
        }
    }
}
