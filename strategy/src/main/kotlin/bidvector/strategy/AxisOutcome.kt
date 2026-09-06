package bidvector.strategy

/**
 * 감시 필드 한 축의 판정(D-9) — `NotConfigured`는 그 축을 보지 않는다(포함 축은 vacuous
 * true, 제외 축은 no-op). `Passed.rules`는 예산처럼 한 축이 필드 둘(min·max)을 가질 수
 * 있어 집합이다. `internal`이다 — `WatchRules.kt`(평가 결합)와 `WatchBudgetAxis.kt`(예산
 * 축)가 함께 참조한다.
 */
internal sealed interface AxisOutcome {
    data object NotConfigured : AxisOutcome

    data class Failed(
        val rule: WatchRuleId,
    ) : AxisOutcome

    data class Passed(
        val rules: Set<WatchRuleId>,
    ) : AxisOutcome

    data class Undeterminable(
        val reason: WatchUndeterminableReason,
    ) : AxisOutcome
}

private const val ASCII_CASE_OFFSET = 'a' - 'A'

/**
 * ASCII 대소문자 접기(legacy `.lower()` 상당, D-5) — `String.lowercase()`는 `Locale`을
 * 참조해 아키텍처 T-C에 걸린다(1C 실측, `LicensePolicy.kt`). `CharArray` 직접 조립만
 * 쓴다(1C 관례를 따르되, 1C의 helper는 `private`이라 모듈 간 재사용이 불가능해 이 모듈이
 * 다시 짠다 — `OPEN-1E-TEXTFOLD`, CPD 실측 결과 위반 없음).
 */
private fun foldCase(value: String): String {
    val chars = CharArray(value.length)
    for (i in value.indices) {
        val c = value[i]
        chars[i] = if (c in 'A'..'Z') c + ASCII_CASE_OFFSET else c
    }
    return String(chars)
}

private fun matchesAny(
    text: String,
    terms: List<String>,
): Boolean {
    val foldedText = foldCase(text)
    return terms.any { foldedText.contains(foldCase(it)) }
}

private fun categoryMatches(
    subjectCategories: Set<CategoryCode>,
    focusCategories: Set<CategoryCode>,
): Boolean {
    val foldedFocus = focusCategories.mapTo(mutableSetOf()) { foldCase(it.value.trim()) }
    return subjectCategories.any { foldCase(it.value.trim()) in foldedFocus }
}

internal fun excludeRegionOutcome(
    rules: WatchRules,
    subject: WatchSubject,
): AxisOutcome =
    when {
        rules.excludeRegionTerms.isEmpty() -> AxisOutcome.NotConfigured
        matchesAny(subject.fullText.value, rules.excludeRegionTerms) -> AxisOutcome.Failed(WatchRuleId.ExcludeRegion)
        else -> AxisOutcome.Passed(emptySet())
    }

internal fun excludeKeywordOutcome(
    rules: WatchRules,
    subject: WatchSubject,
): AxisOutcome {
    if (rules.excludeKeywordTerms.isEmpty()) return AxisOutcome.NotConfigured
    val matched = matchesAny(subject.keywordText.value, rules.excludeKeywordTerms)
    return if (matched) AxisOutcome.Failed(WatchRuleId.ExcludeKeyword) else AxisOutcome.Passed(emptySet())
}

internal fun categoryOutcome(
    rules: WatchRules,
    subject: WatchSubject,
): AxisOutcome {
    if (rules.focusCategories.isEmpty()) return AxisOutcome.NotConfigured
    val matched = categoryMatches(subject.categories, rules.focusCategories)
    return if (matched) {
        AxisOutcome.Passed(setOf(WatchRuleId.FocusCategory))
    } else {
        AxisOutcome.Failed(WatchRuleId.FocusCategory)
    }
}

internal fun focusRegionOutcome(
    rules: WatchRules,
    subject: WatchSubject,
): AxisOutcome {
    if (rules.focusRegionTerms.isEmpty()) return AxisOutcome.NotConfigured
    val matched = matchesAny(subject.fullText.value, rules.focusRegionTerms)
    return if (matched) {
        AxisOutcome.Passed(setOf(WatchRuleId.FocusRegion))
    } else {
        AxisOutcome.Failed(WatchRuleId.FocusRegion)
    }
}

internal fun requiredKeywordOutcome(
    rules: WatchRules,
    subject: WatchSubject,
): AxisOutcome {
    if (rules.requiredKeywordTerms.isEmpty()) return AxisOutcome.NotConfigured
    val matched = matchesAny(subject.keywordText.value, rules.requiredKeywordTerms)
    return if (matched) {
        AxisOutcome.Passed(setOf(WatchRuleId.RequiredKeyword))
    } else {
        AxisOutcome.Failed(WatchRuleId.RequiredKeyword)
    }
}
