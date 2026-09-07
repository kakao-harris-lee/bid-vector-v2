package bidvector.adapters.persistence

import bidvector.procurement.BusinessCategory
import bidvector.procurement.CategoryCode
import bidvector.procurement.CategoryLabel
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeEvent
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeStatus
import bidvector.procurement.NoticeTransitionOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.ResolvedEstimatedAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import java.time.Instant

/** `Open`에서 각 종단(또는 중간) 상태로 가는 고정 경로 — [NoticeStatus] 전이표(`Canonicalize.kt`)와 같은 표. */
private val EVENT_PATH_TO_STATUS: Map<NoticeStatus, List<NoticeEvent>> =
    mapOf(
        NoticeStatus.Open to emptyList(),
        NoticeStatus.Renoticed to listOf(NoticeEvent.RenoticeObserved),
        NoticeStatus.Closed to listOf(NoticeEvent.DeadlineReached),
        NoticeStatus.Awarded to listOf(NoticeEvent.DeadlineReached, NoticeEvent.AwardObserved),
        NoticeStatus.Failed to listOf(NoticeEvent.DeadlineReached, NoticeEvent.FailureObserved),
        NoticeStatus.Cancelled to listOf(NoticeEvent.CancellationObserved),
    )

private fun businessCategoryOf(row: NoticeRow): BusinessCategory? {
    val code = row.businessCategoryCode ?: return null
    return BusinessCategory(CategoryCode(code), row.businessCategoryLabel?.let(::CategoryLabel))
}

private fun applyStatusPath(
    collected: Notice,
    status: NoticeStatus,
): Notice {
    val path = EVENT_PATH_TO_STATUS[status] ?: return collected
    return path.fold(collected) { notice, event ->
        when (val outcome = notice.applyEvent(event)) {
            is NoticeTransitionOutcome.Applied -> outcome.notice
            is NoticeTransitionOutcome.Rejected -> error("상태 재구성 경로가 유효하지 않다: $outcome")
        }
    }
}

/** DB 행 → [Notice] — `Notice`의 유일한 공개 생성 경로(`collected` + `applyEvent`)만 쓴다. */
internal fun NoticeId.reconstructNotice(row: NoticeRow): Notice {
    val placeholderRaw =
        RawNoticeObservation.of(emptyMap(), SourceEndpoint.NOTICE_LIST, row.deadlineAt ?: Instant.EPOCH)
    val command =
        NoticeCollected(
            id = this,
            businessCategory = businessCategoryOf(row),
            baseAmount = row.toResolvedBaseAmount(),
            estimatedAmount = row.toResolvedEstimatedAmount(),
            allocatedBudget = row.toAllocatedBudget(),
            floorRate = row.toFloorRate(),
            deadlineAt = row.deadlineAt,
            openingScheduledAt = null,
            raw = placeholderRaw,
        )
    return applyStatusPath(Notice.collected(command), NoticeStatus.valueOf(row.status))
}

private fun NoticeRow.toResolvedBaseAmount(): ResolvedBaseAmount? {
    val won = baseAmountWon ?: return null
    val provenance = ProvenanceCodec.decode(requireNotNull(baseAmountProvenance), baseAmountProvenanceDetail)
    val currency = Currency.valueOf(requireNotNull(baseAmountCurrency))
    val vat = VatTreatment.valueOf(requireNotNull(baseAmountVat))
    return when (provenance) {
        is Provenance.Published -> {
            ResolvedBaseAmount.Direct.of(won.toLong(), currency, vat, provenance)
        }

        is Provenance.FilledFromBudgetKey -> {
            val amount = BaseAmount(won.toLong(), currency, vat, provenance)
            ResolvedBaseAmount.FallbackFromBudget(RawKey(provenance.key), amount)
        }

        Provenance.DerivedFromOpening -> {
            ResolvedBaseAmount.DerivedFromOpeningAmount(BaseAmount(won.toLong(), currency, vat, provenance))
        }

        else -> {
            error("base_amount에 올 수 없는 provenance: $provenance")
        }
    }
}

private fun NoticeRow.toResolvedEstimatedAmount(): ResolvedEstimatedAmount? {
    val won = estimatedAmountWon ?: return null
    val provenance = ProvenanceCodec.decode(requireNotNull(estimatedAmountProvenance), estimatedAmountProvenanceDetail)
    val currency = Currency.valueOf(requireNotNull(estimatedAmountCurrency))
    val vat = VatTreatment.valueOf(requireNotNull(estimatedAmountVat))
    val sourceKey = RawKey(requireNotNull(estimatedAmountSourceKey))
    return ResolvedEstimatedAmount(sourceKey, EstimatedAmount(won.toLong(), currency, vat, provenance))
}

private fun NoticeRow.toAllocatedBudget(): AllocatedBudget? {
    val won = allocatedBudgetWon ?: return null
    val provenance = ProvenanceCodec.decode(requireNotNull(allocatedBudgetProvenance), allocatedBudgetProvenanceDetail)
    return AllocatedBudget(won.toLong(), Currency.KRW, provenance)
}

private fun NoticeRow.toFloorRate(): FloorRate? {
    val fraction = floorRateFraction ?: return null
    val origin = FloorRateOriginCodec.decode(requireNotNull(floorRateOriginKind), requireNotNull(floorRateOriginDetail))
    return FloorRate(Rate.ofFraction(fraction), origin)
}
