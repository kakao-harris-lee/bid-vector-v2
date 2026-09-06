package bidvector.strategy

/**
 * 운영자 감시 필드 일곱(STR-01) — 카테고리는 완전일치 집합, 지역·키워드는 부분문자열
 * 규칙 목록, 예산은 [BudgetBound]. 필드 전부 비어 있으면 [isEmpty]가 참이다(D-15, ⑥
 * 「감시 범위를 좁히는 규칙이 있는가」). 판정 함수([bidvector.strategy.evaluate])는
 * 별도 커밋(감시 predicate)이 낸다. DB·시계·ML port 접근이 없다 — `strategy` 모듈은
 * shared-kernel만 참조한다(ADR 0006 D-4, 모듈 의존 게이트가 구조로 보장).
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
