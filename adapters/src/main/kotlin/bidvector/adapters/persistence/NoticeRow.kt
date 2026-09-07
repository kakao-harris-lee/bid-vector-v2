package bidvector.adapters.persistence

import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeStatus
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.export
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant

/** `notice` 행 하나의 원시 컬럼 값 — Kotlin 규칙(정책 값 X, 저장 표현)이 이 형태로 merge를 잰다. */
internal data class NoticeRow(
    val status: String,
    val businessCategoryCode: String?,
    val businessCategoryLabel: String?,
    val baseAmountWon: BigDecimal?,
    val baseAmountCurrency: String?,
    val baseAmountVat: String?,
    val baseAmountProvenance: String?,
    val baseAmountProvenanceDetail: String?,
    val estimatedAmountWon: BigDecimal?,
    val estimatedAmountCurrency: String?,
    val estimatedAmountVat: String?,
    val estimatedAmountProvenance: String?,
    val estimatedAmountProvenanceDetail: String?,
    val estimatedAmountSourceKey: String?,
    val allocatedBudgetWon: BigDecimal?,
    val allocatedBudgetProvenance: String?,
    val allocatedBudgetProvenanceDetail: String?,
    val floorRateFraction: BigDecimal?,
    val floorRateOriginKind: String?,
    val floorRateOriginDetail: String?,
    val deadlineAt: Instant?,
    val revision: Long,
)

internal fun ResultSet.toNoticeRow(): NoticeRow =
    NoticeRow(
        status = getString("status"),
        businessCategoryCode = getString("business_category_code"),
        businessCategoryLabel = getString("business_category_label"),
        baseAmountWon = getBigDecimal("base_amount_won"),
        baseAmountCurrency = getString("base_amount_currency"),
        baseAmountVat = getString("base_amount_vat"),
        baseAmountProvenance = getString("base_amount_provenance"),
        baseAmountProvenanceDetail = getString("base_amount_provenance_detail"),
        estimatedAmountWon = getBigDecimal("estimated_amount_won"),
        estimatedAmountCurrency = getString("estimated_amount_currency"),
        estimatedAmountVat = getString("estimated_amount_vat"),
        estimatedAmountProvenance = getString("estimated_amount_provenance"),
        estimatedAmountProvenanceDetail = getString("estimated_amount_provenance_detail"),
        estimatedAmountSourceKey = getString("estimated_amount_source_key"),
        allocatedBudgetWon = getBigDecimal("allocated_budget_won"),
        allocatedBudgetProvenance = getString("allocated_budget_provenance"),
        allocatedBudgetProvenanceDetail = getString("allocated_budget_provenance_detail"),
        floorRateFraction = getBigDecimal("floor_rate_fraction"),
        floorRateOriginKind = getString("floor_rate_origin_kind"),
        floorRateOriginDetail = getString("floor_rate_origin_detail"),
        deadlineAt = getTimestamp("deadline_at")?.toInstant(),
        revision = getLong("revision"),
    )

/** `shared-kernel`이 `won`을 `internal`로 닫아 다른 모듈은 export 경로로만 값을 읽는다(`Money.export()`). */
private fun Money.exportWon(): Long = this.export().won

/** [NoticeCollected]를 신규 행 형태로 편다 — `Sql.INSERT_NOTICE`의 열 순서와 맞춘다(status 고정 "Open"). */
internal fun NoticeCollected.toNoticeRow(): NoticeRow =
    NoticeRow(
        status = NoticeStatus.Open.name,
        businessCategoryCode = businessCategory?.code?.value,
        businessCategoryLabel = businessCategory?.label?.value,
        baseAmountWon = baseAmount?.amount?.let { BigDecimal.valueOf(it.exportWon()) },
        baseAmountCurrency = baseAmount?.amount?.currency?.name,
        baseAmountVat = baseAmount?.amount?.vatTreatment?.name,
        baseAmountProvenance = baseAmount?.amount?.provenance?.let { ProvenanceCodec.kindOf(it).name },
        baseAmountProvenanceDetail = baseAmount?.amount?.provenance?.let(ProvenanceCodec::detailOf),
        estimatedAmountWon = estimatedAmount?.amount?.let { BigDecimal.valueOf(it.exportWon()) },
        estimatedAmountCurrency = estimatedAmount?.amount?.currency?.name,
        estimatedAmountVat = estimatedAmount?.amount?.vatTreatment?.name,
        estimatedAmountProvenance = estimatedAmount?.amount?.provenance?.let { ProvenanceCodec.kindOf(it).name },
        estimatedAmountProvenanceDetail = estimatedAmount?.amount?.provenance?.let(ProvenanceCodec::detailOf),
        estimatedAmountSourceKey = estimatedAmount?.sourceKey?.name,
        allocatedBudgetWon = allocatedBudget?.let { BigDecimal.valueOf(it.exportWon()) },
        allocatedBudgetProvenance = allocatedBudget?.provenance?.let { ProvenanceCodec.kindOf(it).name },
        allocatedBudgetProvenanceDetail = allocatedBudget?.provenance?.let(ProvenanceCodec::detailOf),
        floorRateFraction = floorRate?.rate?.fraction,
        floorRateOriginKind = floorRate?.origin?.let(FloorRateOriginCodec::kindOf),
        floorRateOriginDetail = floorRate?.origin?.let(FloorRateOriginCodec::detailOf),
        deadlineAt = deadlineAt,
        revision = 1L,
    )
