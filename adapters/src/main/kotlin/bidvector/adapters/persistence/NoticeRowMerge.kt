package bidvector.adapters.persistence

import bidvector.procurement.NoticeCollected
import bidvector.procurement.mayOverwrite
import bidvector.sharedkernel.Provenance
import java.math.BigDecimal

private fun decodeProvenance(
    kind: String?,
    detail: String?,
): Provenance? = kind?.let { ProvenanceCodec.decode(it, detail) }

/** 유입이 없으면 기존을 지키고, 있으면 [mayOverwrite]가 허용할 때만 새 값을 쓴다(④, §5.1 규율 1). */
private fun overwriteAllowed(
    existingProvenance: Provenance?,
    incomingProvenance: Provenance?,
): Boolean = incomingProvenance != null && mayOverwrite(existingProvenance, incomingProvenance)

private data class BaseAmountRow(
    val won: BigDecimal?,
    val currency: String?,
    val vat: String?,
    val provenance: String?,
    val provenanceDetail: String?,
)

private fun NoticeRow.baseAmountRow() =
    BaseAmountRow(baseAmountWon, baseAmountCurrency, baseAmountVat, baseAmountProvenance, baseAmountProvenanceDetail)

private fun mergeBaseAmount(
    existing: NoticeRow,
    incomingRow: NoticeRow,
    incomingProvenance: Provenance?,
): BaseAmountRow {
    val existingProvenance = decodeProvenance(existing.baseAmountProvenance, existing.baseAmountProvenanceDetail)
    val overwrite = overwriteAllowed(existingProvenance, incomingProvenance)
    return if (overwrite) incomingRow.baseAmountRow() else existing.baseAmountRow()
}

private data class EstimatedAmountRow(
    val won: BigDecimal?,
    val currency: String?,
    val vat: String?,
    val provenance: String?,
    val provenanceDetail: String?,
    val sourceKey: String?,
)

private fun NoticeRow.estimatedAmountRow() =
    EstimatedAmountRow(
        estimatedAmountWon,
        estimatedAmountCurrency,
        estimatedAmountVat,
        estimatedAmountProvenance,
        estimatedAmountProvenanceDetail,
        estimatedAmountSourceKey,
    )

private fun mergeEstimatedAmount(
    existing: NoticeRow,
    incomingRow: NoticeRow,
    incomingProvenance: Provenance?,
): EstimatedAmountRow {
    val existingProvenance =
        decodeProvenance(existing.estimatedAmountProvenance, existing.estimatedAmountProvenanceDetail)
    val overwrite = overwriteAllowed(existingProvenance, incomingProvenance)
    return if (overwrite) incomingRow.estimatedAmountRow() else existing.estimatedAmountRow()
}

private data class AllocatedBudgetRow(
    val won: BigDecimal?,
    val provenance: String?,
    val provenanceDetail: String?,
)

private fun NoticeRow.allocatedBudgetRow() =
    AllocatedBudgetRow(allocatedBudgetWon, allocatedBudgetProvenance, allocatedBudgetProvenanceDetail)

private fun mergeAllocatedBudget(
    existing: NoticeRow,
    incomingRow: NoticeRow,
    incomingProvenance: Provenance?,
): AllocatedBudgetRow {
    val existingProvenance =
        decodeProvenance(existing.allocatedBudgetProvenance, existing.allocatedBudgetProvenanceDetail)
    val overwrite = overwriteAllowed(existingProvenance, incomingProvenance)
    return if (overwrite) incomingRow.allocatedBudgetRow() else existing.allocatedBudgetRow()
}

/**
 * 권위 계층 merge(④, §5.1 규율 1) — 세 금액 축(base·estimated·allocated)을 각자의 헬퍼로
 * 독립 판단한다(`mergeBaseAmount`·`mergeEstimatedAmount`·`mergeAllocatedBudget`). floor_rate는
 * provenance 축이 없어(V1 migration KDoc) 존재 가드만 받는다 — 유입 값이 있으면 항상 쓴다.
 */
internal fun mergeNoticeRow(
    existing: NoticeRow,
    incoming: NoticeCollected,
): NoticeRow {
    val incomingRow = incoming.toNoticeRow()
    val base = mergeBaseAmount(existing, incomingRow, incoming.baseAmount?.amount?.provenance)
    val estimated = mergeEstimatedAmount(existing, incomingRow, incoming.estimatedAmount?.amount?.provenance)
    val allocated = mergeAllocatedBudget(existing, incomingRow, incoming.allocatedBudget?.provenance)

    return existing.copy(
        businessCategoryCode = incomingRow.businessCategoryCode ?: existing.businessCategoryCode,
        businessCategoryLabel = incomingRow.businessCategoryLabel ?: existing.businessCategoryLabel,
        baseAmountWon = base.won,
        baseAmountCurrency = base.currency,
        baseAmountVat = base.vat,
        baseAmountProvenance = base.provenance,
        baseAmountProvenanceDetail = base.provenanceDetail,
        estimatedAmountWon = estimated.won,
        estimatedAmountCurrency = estimated.currency,
        estimatedAmountVat = estimated.vat,
        estimatedAmountProvenance = estimated.provenance,
        estimatedAmountProvenanceDetail = estimated.provenanceDetail,
        estimatedAmountSourceKey = estimated.sourceKey,
        allocatedBudgetWon = allocated.won,
        allocatedBudgetProvenance = allocated.provenance,
        allocatedBudgetProvenanceDetail = allocated.provenanceDetail,
        floorRateFraction = incomingRow.floorRateFraction ?: existing.floorRateFraction,
        floorRateOriginKind = incomingRow.floorRateOriginKind ?: existing.floorRateOriginKind,
        floorRateOriginDetail = incomingRow.floorRateOriginDetail ?: existing.floorRateOriginDetail,
        deadlineAt = incomingRow.deadlineAt ?: existing.deadlineAt,
    )
}
