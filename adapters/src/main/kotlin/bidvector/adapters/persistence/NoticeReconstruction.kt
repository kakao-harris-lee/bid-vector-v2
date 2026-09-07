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

/**
 * `Open`에서 각 종단(또는 중간) 상태로 가는 고정 경로 — [NoticeStatus] 전이표(`Canonicalize.kt`)와
 * 같은 표.
 *
 * **verifier r2 N-5 뒤 개정** — 이전 판은 이 표를 `Map<NoticeStatus, List<NoticeEvent>>`로
 * 두고 [applyStatusPath]가 `Map.getValue`(표에 없으면 `NoSuchElementException`)로 읽었다.
 * 표에서 항목 하나를 실수로 지워도(예: `Cancelled`) **컴파일은 그대로 성공했다** — 런타임에
 * 그 상태를 실제로 만나야만 예외가 드러나는데, `NoticeStatus`가 여섯 값뿐이고 표도 우연히
 * 여섯을 다 담고 있어 test suite가 그 결손을 잡지 못했다(F-8이 바꾼 술어를 지키는 test가
 * 없었다). `when`으로 바꾸면 [NoticeStatus]에 새 값이 추가되거나 이 함수의 분기가 하나
 * 빠지면 **컴파일 자체가 실패한다**(Kotlin의 exhaustive `when` — sealed/enum 소진 검사) —
 * 런타임 예외보다 훨씬 이른 지점에서, 그리고 무조건 잡히는 형태로 같은 결손을 막는다
 * (「회귀 구조적 방지」, CLAUDE.md).
 */
private fun eventPathFor(status: NoticeStatus): List<NoticeEvent> =
    when (status) {
        NoticeStatus.Open -> emptyList()
        NoticeStatus.Renoticed -> listOf(NoticeEvent.RenoticeObserved)
        NoticeStatus.Closed -> listOf(NoticeEvent.DeadlineReached)
        NoticeStatus.Awarded -> listOf(NoticeEvent.DeadlineReached, NoticeEvent.AwardObserved)
        NoticeStatus.Failed -> listOf(NoticeEvent.DeadlineReached, NoticeEvent.FailureObserved)
        NoticeStatus.Cancelled -> listOf(NoticeEvent.CancellationObserved)
    }

private fun businessCategoryOf(row: NoticeRow): BusinessCategory? {
    val code = row.businessCategoryCode ?: return null
    return BusinessCategory(CategoryCode(code), row.businessCategoryLabel?.let(::CategoryLabel))
}

/**
 * verifier r1 F-8 뒤 개정 — 이전 판(`EVENT_PATH_TO_STATUS[status] ?: return collected`)은
 * 표에 없는 상태를 만나면 예외 대신 **조용히 `Open`을 냈다**. [eventPathFor]의 exhaustive
 * `when`이 이제 그 결손 자체를 컴파일 시점에 막는다(verifier r2 N-5, 위 KDoc).
 */
private fun applyStatusPath(
    collected: Notice,
    status: NoticeStatus,
): Notice {
    val path = eventPathFor(status)
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
