package bidvector.adapters.strategy

import bidvector.adapters.persistence.ProvenanceCodec
import bidvector.adapters.persistence.getTextList
import bidvector.adapters.persistence.setNullableInt
import bidvector.adapters.persistence.setTextArray
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import bidvector.strategy.CategoryCode
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyDraft
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * `operator_strategy`/`operator_strategy_revision` 행 하나의 원시 컬럼 값(M6/6F-1 D-6F1-2) —
 * 어댑터가 다루는 것은 이 형태까지다. [OperatorStrategy]를 직접 만들지 않는다 — 이 행은
 * [toDraft]로 **초안**([StrategyDraft])이 되어 `bidvector.strategy.validate()`를 지나야
 * 전략이 된다(D-6F1-3). 두 표가 같은 열 형태를 공유한다(현재 값 vs 이력 한 줄, D-6F1-1 ①).
 */
internal data class StrategyRow(
    val focusCategories: List<String>,
    val focusRegionTerms: List<String>,
    val excludeRegionTerms: List<String>,
    val requiredKeywordTerms: List<String>,
    val excludeKeywordTerms: List<String>,
    val minBudgetWon: BigDecimal?,
    val minBudgetCurrency: String?,
    val minBudgetVat: String?,
    val minBudgetProvenance: String?,
    val minBudgetProvenanceDetail: String?,
    val maxBudgetWon: BigDecimal?,
    val maxBudgetCurrency: String?,
    val maxBudgetVat: String?,
    val maxBudgetProvenance: String?,
    val maxBudgetProvenanceDetail: String?,
    val minimumMatchScore: BigDecimal?,
    val minimumProbabilityScore: BigDecimal?,
    val bidNowThreshold: BigDecimal?,
    val reviewThreshold: BigDecimal?,
    val candidateLimit: Int?,
    val maxActiveBids: Int?,
    val revision: Int,
)

internal fun ResultSet.toStrategyRow(): StrategyRow =
    StrategyRow(
        focusCategories = getTextList("focus_categories"),
        focusRegionTerms = getTextList("focus_region_terms"),
        excludeRegionTerms = getTextList("exclude_region_terms"),
        requiredKeywordTerms = getTextList("required_keyword_terms"),
        excludeKeywordTerms = getTextList("exclude_keyword_terms"),
        minBudgetWon = getBigDecimal("min_budget_won"),
        minBudgetCurrency = getString("min_budget_currency"),
        minBudgetVat = getString("min_budget_vat"),
        minBudgetProvenance = getString("min_budget_provenance"),
        minBudgetProvenanceDetail = getString("min_budget_provenance_detail"),
        maxBudgetWon = getBigDecimal("max_budget_won"),
        maxBudgetCurrency = getString("max_budget_currency"),
        maxBudgetVat = getString("max_budget_vat"),
        maxBudgetProvenance = getString("max_budget_provenance"),
        maxBudgetProvenanceDetail = getString("max_budget_provenance_detail"),
        minimumMatchScore = getBigDecimal("minimum_match_score"),
        minimumProbabilityScore = getBigDecimal("minimum_probability_score"),
        bidNowThreshold = getBigDecimal("bid_now_threshold"),
        reviewThreshold = getBigDecimal("review_threshold"),
        candidateLimit = getInt("candidate_limit").takeUnless { wasNull() },
        maxActiveBids = getInt("max_active_bids").takeUnless { wasNull() },
        revision = getInt("revision"),
    )

/**
 * 한 예산 한계(min 또는 max)의 저장 표현 다섯 컬럼 — [toBudgetAmount]·[budgetRowOf]가 이
 * 그룹으로 주고받아 인자 목록을 줄인다(줄 길이·중복 축소, sizeGate와 같은 원칙).
 */
private data class BudgetColumns(
    val won: BigDecimal?,
    val currency: String?,
    val vat: String?,
    val provenance: String?,
    val provenanceDetail: String?,
)

/**
 * 어댑터가 만드는 유일한 통로 — 행 → **초안**(D-6F1-2). [bidvector.strategy.OperatorStrategy]
 * 생성자는 여기서도, 다른 어디서도 부르지 않는다 — 그 유일한 문은
 * [bidvector.strategy.validate]다.
 */
internal fun StrategyRow.toDraft(): StrategyDraft {
    val min =
        BudgetColumns(minBudgetWon, minBudgetCurrency, minBudgetVat, minBudgetProvenance, minBudgetProvenanceDetail)
    val max =
        BudgetColumns(maxBudgetWon, maxBudgetCurrency, maxBudgetVat, maxBudgetProvenance, maxBudgetProvenanceDetail)
    return StrategyDraft(
        focusCategories = focusCategories,
        focusRegionTerms = focusRegionTerms,
        excludeRegionTerms = excludeRegionTerms,
        requiredKeywordTerms = requiredKeywordTerms,
        excludeKeywordTerms = excludeKeywordTerms,
        minBudget = toBudgetAmount(min),
        maxBudget = toBudgetAmount(max),
        minimumMatchScore = minimumMatchScore,
        minimumProbabilityScore = minimumProbabilityScore,
        bidNowThreshold = bidNowThreshold,
        reviewThreshold = reviewThreshold,
        candidateLimit = candidateLimit,
        maxActiveBids = maxActiveBids,
    )
}

/**
 * 예산 한계 왕복(D-6F1-2 우회 (6)) — `won`이 없으면 그 한계 자체가 「규칙 없음」이다(V9
 * CHECK가 나머지 넷도 함께 NULL임을 이미 보장한다). `BaseAmount` 생성자는 `shared-kernel`의
 * 평범한 public 생성자다([bidvector.strategy.OperatorStrategy]와 달리 이 타입은 D-6F1-2가
 * 닫는 대상이 아니다) — [ProvenanceCodec]을 그대로 재사용한다(`NoticeRow.kt`와 같은 경로,
 * 중복 금지).
 */
private fun toBudgetAmount(columns: BudgetColumns): BaseAmount? {
    val won = columns.won ?: return null
    val currencyName = requireNotNull(columns.currency) { "예산 한계는 currency 없이 저장될 수 없다" }
    val vatName = requireNotNull(columns.vat) { "예산 한계는 vat 없이 저장될 수 없다" }
    val currency = Currency.valueOf(currencyName)
    val vat = VatTreatment.valueOf(vatName)
    val provenanceKind =
        requireNotNull(columns.provenance) { "예산 한계는 provenance 없이 저장될 수 없다" }
    val provenance = ProvenanceCodec.decode(provenanceKind, columns.provenanceDetail)
    return BaseAmount(won.toExactStrategyWon(), currency, vat, provenance)
}

/**
 * `NUMERIC(20,0)` 컬럼(20자리)을 `Long`(19자리 상한)으로 **정확하게** 복원한다(Codex 심판
 * HIGH, M6/6F-1) — `toLong()`은 범위를 벗어나면 예외 없이 하위 64비트만 남겨 전혀 다른
 * 금액을 조용히 만든다. V9가 이제 저장 시점에 범위 CHECK를 걸어(`0..Long.MAX_VALUE`) 정상
 * 경로에서는 이 실패가 일어나지 않지만, 복원 쪽도 스스로 정확성을 확인한다(D-6F1-3 「지어내지
 * 않고 실패한다」와 같은 경계 — CHECK 하나만 믿지 않는다).
 */
private fun BigDecimal.toExactStrategyWon(): Long =
    try {
        longValueExact()
    } catch (overflow: ArithmeticException) {
        // detekt SwallowedException — 원인 예외를 cause 로 실어 보존한다.
        throw IllegalStateException(
            "예산 won 값이 Long 범위를 벗어나 정확히 복원할 수 없다(저장된 값 손상 의심): $this",
            overflow,
        )
    }

/** [BudgetColumns]의 역함수 — [OperatorStrategy.toRow]가 min·max 각각에 대해 부른다. */
private fun budgetColumnsOf(amount: BaseAmount?): BudgetColumns =
    BudgetColumns(
        won = amount?.let { BigDecimal.valueOf(it.exportWon()) },
        currency = amount?.currency?.name,
        vat = amount?.vatTreatment?.name,
        provenance = amount?.provenance?.let(ProvenanceCodec::kindNameOf),
        provenanceDetail = amount?.provenance?.let(ProvenanceCodec::detailOf),
    )

/**
 * 저장 대상 행 — [OperatorStrategy]의 필드를 그대로 옮긴다(D-6F1-3 우회 (6), 「자기 값을
 * 지어 쓰지 않는다」). `Money.export()`로 `won`을 얻는다(`won`이 `shared-kernel` 밖에
 * `internal`이라 [NoticeRow.kt]와 같은 경로, [exportWon] 재사용).
 */
internal fun OperatorStrategy.toRow(): StrategyRow {
    val min = budgetColumnsOf(watchRules.budget.min)
    val max = budgetColumnsOf(watchRules.budget.max)
    return StrategyRow(
        focusCategories = watchRules.focusCategories.map(CategoryCode::value),
        focusRegionTerms = watchRules.focusRegionTerms,
        excludeRegionTerms = watchRules.excludeRegionTerms,
        requiredKeywordTerms = watchRules.requiredKeywordTerms,
        excludeKeywordTerms = watchRules.excludeKeywordTerms,
        minBudgetWon = min.won,
        minBudgetCurrency = min.currency,
        minBudgetVat = min.vat,
        minBudgetProvenance = min.provenance,
        minBudgetProvenanceDetail = min.provenanceDetail,
        maxBudgetWon = max.won,
        maxBudgetCurrency = max.currency,
        maxBudgetVat = max.vat,
        maxBudgetProvenance = max.provenance,
        maxBudgetProvenanceDetail = max.provenanceDetail,
        minimumMatchScore = actionThresholds.minimumMatchScore?.score?.value,
        minimumProbabilityScore = actionThresholds.minimumProbabilityScore?.score?.value,
        bidNowThreshold = actionThresholds.bidNowThreshold?.score?.value,
        reviewThreshold = actionThresholds.reviewThreshold?.score?.value,
        candidateLimit = candidateLimit?.value,
        maxActiveBids = maxActiveBids?.value,
        revision = revision.value,
    )
}

/** `shared-kernel`이 `won`을 `internal`로 닫는다 — 다른 모듈은 export 경로로만 읽는다([NoticeRow.kt]와 같은 자리). */
private fun BaseAmount.exportWon(): Long = this.export().won

/**
 * [StrategyRow]를 바인딩 순서대로 인자에 싣는다 — `revision`을 먼저 싣는다(`Sql.UPSERT_STRATEGY`·
 * `Sql.INSERT_STRATEGY_REVISION`이 둘 다 이 순서를 쓴다, 두 표가 같은 플레이스홀더 순서를
 * 공유해 한 바인더로 충분하다).
 */
internal fun PreparedStatement.bindStrategyRow(
    startIndex: Int,
    row: StrategyRow,
): Int {
    var index = startIndex
    setInt(index++, row.revision)
    setTextArray(index++, row.focusCategories)
    setTextArray(index++, row.focusRegionTerms)
    setTextArray(index++, row.excludeRegionTerms)
    setTextArray(index++, row.requiredKeywordTerms)
    setTextArray(index++, row.excludeKeywordTerms)
    setBigDecimal(index++, row.minBudgetWon)
    setString(index++, row.minBudgetCurrency)
    setString(index++, row.minBudgetVat)
    setString(index++, row.minBudgetProvenance)
    setString(index++, row.minBudgetProvenanceDetail)
    setBigDecimal(index++, row.maxBudgetWon)
    setString(index++, row.maxBudgetCurrency)
    setString(index++, row.maxBudgetVat)
    setString(index++, row.maxBudgetProvenance)
    setString(index++, row.maxBudgetProvenanceDetail)
    setBigDecimal(index++, row.minimumMatchScore)
    setBigDecimal(index++, row.minimumProbabilityScore)
    setBigDecimal(index++, row.bidNowThreshold)
    setBigDecimal(index++, row.reviewThreshold)
    setNullableInt(index++, row.candidateLimit)
    setNullableInt(index++, row.maxActiveBids)
    return index
}
