package bidvector.strategy

/**
 * 운영자 감시 필드 일곱(STR-01) — 카테고리는 완전일치 집합, 지역·키워드는 부분문자열
 * 규칙 목록, 예산은 [BudgetBound]. 필드 전부 비어 있으면 [isEmpty]가 참이고 [evaluate]는
 * `NoGate`를 낸다. DB·시계·ML port 접근이 없다 — `strategy` 모듈은 shared-kernel만
 * 참조한다(ADR 0006 D-4, 모듈 의존 게이트가 구조로 보장).
 */
data class WatchRules(
    val focusCategories: Set<CategoryCode>,
    val focusRegionTerms: List<String>,
    val excludeRegionTerms: List<String>,
    val requiredKeywordTerms: List<String>,
    val excludeKeywordTerms: List<String>,
    val budget: BudgetBound,
) {
    /** 「감시 범위를 좁히는 규칙이 있는가」(D-15, ⑥) — 「전략이 설정됐는가」와 다른 물음. */
    fun isEmpty(): Boolean =
        focusCategories.isEmpty() &&
            focusRegionTerms.isEmpty() &&
            excludeRegionTerms.isEmpty() &&
            requiredKeywordTerms.isEmpty() &&
            excludeKeywordTerms.isEmpty() &&
            budget.min == null &&
            budget.max == null

    companion object {
        /** 규칙이 하나도 없는 [WatchRules] — 「게이트 없음」 판정의 표준 입력. */
        fun empty(inclusivity: BudgetBoundInclusivity): WatchRules =
            WatchRules(
                focusCategories = emptySet(),
                focusRegionTerms = emptyList(),
                excludeRegionTerms = emptyList(),
                requiredKeywordTerms = emptyList(),
                excludeKeywordTerms = emptyList(),
                budget = BudgetBound(null, null, inclusivity),
            )
    }
}

/**
 * 감시 필드 일곱을 D-9 순서(제외 둘 먼저, 그 다음 카테고리·지역·키워드·예산)로 판정하고
 * three-valued AND로 결합한다(단락 평가 — 앞선 축이 확정적으로 실패거나 미확정이면 뒤
 * 축은 보지 않고 그 결과를 낸다).
 */
private fun combineAxes(outcomes: List<AxisOutcome>): WatchVerdict {
    val blocking = outcomes.firstOrNull { it is AxisOutcome.Failed || it is AxisOutcome.Undeterminable }
    return when (blocking) {
        is AxisOutcome.Failed -> WatchVerdict.Rejected(setOf(blocking.rule))
        is AxisOutcome.Undeterminable -> WatchVerdict.Undeterminable(blocking.reason)
        else -> WatchVerdict.Passed(outcomes.filterIsInstance<AxisOutcome.Passed>().flatMap { it.rules }.toSet())
    }
}

/**
 * 감시 predicate 커널(①, STR-01·02) — 유일한 진입점. 결합 순서(D-9): 제외 규칙(지역·
 * 키워드) 하나라도 매치하면 `Rejected`(제외가 우선), 그 다음 포함 규칙(카테고리·지역·
 * 키워드·예산)을 순서대로 검사해 첫 실패에서 `Rejected`를 낸다. 규칙이 하나도 없으면
 * `NoGate`. **판단이 갈린 지점**: 제외 규칙 둘이 동시에 매치해도 `Rejected.failed`는
 * 우선순위상 먼저인 규칙 하나만 싣는다(D-7이 요구하는 것은 「부분집합」이지 「전체
 * 집합」이 아니다).
 */
fun WatchRules.evaluate(subject: WatchSubject): WatchVerdict {
    if (this.isEmpty()) return WatchVerdict.NoGate
    return combineAxes(
        listOf(
            excludeRegionOutcome(this, subject),
            excludeKeywordOutcome(this, subject),
            categoryOutcome(this, subject),
            focusRegionOutcome(this, subject),
            requiredKeywordOutcome(this, subject),
            budgetAxisOutcome(this, subject),
        ),
    )
}
