package bidvector.adapters.snapshot

import bidvector.procurement.FieldConcept
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.collection.NoticeKeyHash
import java.math.BigDecimal

/**
 * 관측 묶음 하나 → 스냅숏 행(D-6G-2). `JdbcSnapshotSource` 에서 분리한 파일이다(detekt 클래스당 함수
 * 상한) — 조회·묶기와 **행 조립**은 다른 관심사다.
 */
internal fun assembleSnapshotRow(
    key: NoticeKey,
    axes: Map<SourceEndpoint, List<RawRow>>,
    canonical: CanonicalNotice,
): SnapshotRow =
    SnapshotRow(
        notice = noticeOf(key, axes, canonical),
        outcome = outcomeOf(axes),
    )

private fun noticeOf(
    key: NoticeKey,
    axes: Map<SourceEndpoint, List<RawRow>>,
    canonical: CanonicalNotice,
): SnapshotNotice {
    val baseAmountRow = axes[SourceEndpoint.BASE_AMOUNT_DETAIL]?.firstOrNull()
    val formulaARow = axes[SourceEndpoint.BID_PRICE_FORMULA_A]?.firstOrNull()
    val listRow = axes[SourceEndpoint.OPENING_RESULT_LIST]?.firstOrNull()
    val noticeListRow = axes[SourceEndpoint.NOTICE_LIST]?.firstOrNull()
    val disclosedAt = baseAmountRow?.instantOf(FieldConcept.BASE_AMOUNT_DISCLOSED_AT)
    // D-6G-19 provenance 분리 — 마감 뒤 공개된 기초금액은 투찰 시점에 없던 값이다.
    val knownAtBidTime = disclosedAt != null && canonical.bidCloseAt != null && disclosedAt < canonical.bidCloseAt
    return SnapshotNotice(
        noticeKeyHash = NoticeKeyHash.of(key.number, key.round).value,
        category = canonical.division,
        // **개찰일로 대체하지 않는다**(D-6G-28) — 그렇게 접으면 제외 ⑬ 이 개찰일로 돌아
        // 시행일 전에 공고되고 후에 개찰된 공고가 승인된다.
        noticedOn = noticeListRow?.localDateOf(FieldConcept.NOTICE_POSTED_AT),
        bidCloseAt = canonical.bidCloseAt,
        baseAmount = baseAmountRow?.takeIf { knownAtBidTime }?.amountOf(FieldConcept.BASE_AMOUNT),
        baseAmountDisclosedAt = disclosedAt,
        floorRate = canonical.floorRate,
        reserveRangeBeginRate = baseAmountRow?.rateOf(FieldConcept.RESERVE_PRICE_RANGE_BEGIN_RATE),
        reserveRangeEndRate = baseAmountRow?.rateOf(FieldConcept.RESERVE_PRICE_RANGE_END_RATE),
        aValueTotal = formulaARow?.let(::aValueTotalOf),
        aValueOpenAt = formulaARow?.instantOf(FieldConcept.BID_PRICE_FORMULA_A_DISCLOSED_AT),
        standardMarketPriceApplicable =
            formulaARow?.predicateOf(FieldConcept.A_STANDARD_MARKET_UNIT_PRICE_APPLICABLE),
        bidPriceFormulaAApplicable = baseAmountRow?.predicateOf(FieldConcept.BID_PRICE_FORMULA_A_APPLICABLE),
        successfulBidMethodCode = noticeListRow?.textOf(FieldConcept.AWARD_METHOD_CODE),
        successfulBidMethodName = noticeListRow?.textOf(FieldConcept.AWARD_METHOD_NAME),
        prearrangedPriceDecisionMethod = formulaARow?.textOf(FieldConcept.PLANNED_PRICE_DECISION_METHOD),
        hasAwardMethodApplicationStandard =
            noticeListRow?.textOf(FieldConcept.AWARD_METHOD_APPLICATION_STANDARD) != null,
        hasApplicationBasisContent = noticeListRow?.textOf(FieldConcept.APPLICATION_BASIS_CONTENT) != null,
        noticeOrdinal = key.round.toIntOrNull() ?: 0,
        procurementClassCode = noticeListRow?.textOf(FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE),
        demandAgencyCode = noticeListRow?.textOf(FieldConcept.DEMAND_AGENCY_CODE),
        pureConstructionCost = baseAmountRow?.amountOf(FieldConcept.PURE_CONSTRUCTION_COST),
    )
}

private fun outcomeOf(axes: Map<SourceEndpoint, List<RawRow>>): SnapshotOutcome {
    val reserveRows = axes[SourceEndpoint.RESERVE_PRICE_DETAIL].orEmpty()
    val listRow = axes[SourceEndpoint.OPENING_RESULT_LIST]?.firstOrNull()
    val bidders =
        axes[SourceEndpoint.OPENING_COMPLETE].orEmpty().map { row ->
            row.countOf(FieldConcept.OPENING_RANK) to row.amountOf(FieldConcept.BID_AMOUNT)
        }
    return SnapshotOutcome(
        // 대체값(EPOCH)을 지어내지 않는다 — 없으면 행 단위 제외의 입력이다(D-6G-28).
        openedOn = reserveRows.firstNotNullOfOrNull { it.localDateOf(FieldConcept.ACTUAL_OPENING_AT) },
        progressDivision = listRow?.textOf(FieldConcept.PROGRESS_DIVISION),
        plannedPrice = reserveRows.firstNotNullOfOrNull { it.amountOf(FieldConcept.RESERVE_PRICE) },
        openingBaseAmount = reserveRows.firstNotNullOfOrNull { it.amountOf(FieldConcept.BASE_AMOUNT) },
        reservePrices = reservePricesOf(reserveRows),
        drawnSerialNumbers = drawnSerialNumbersOf(axes[SourceEndpoint.OPENING_COMPLETE].orEmpty()),
        participantCount = listRow?.countOf(FieldConcept.PARTICIPANT_COUNT),
        bidderRows = orderedBidderRows(bidders),
    )
}

/**
 * 추첨번호(D-6G-28) — 개찰완료 투찰 행이 `drwtNo1`·`drwtNo2` 로 싣는다. v2 는 이 자리를 상수
 * `null` 로 두어, 실 추출이면 **전 행이 제외 ⑤ 에 걸려 승인 0건**이었다(code-review H-1).
 * 값은 1-기반 순번이고 투찰자 귀속은 보존하지 않는다 — 실현 사정률에는 집합만 있으면 된다.
 */
private fun drawnSerialNumbersOf(bidderRows: List<RawRow>): List<Int>? {
    val numbers =
        bidderRows
            .flatMap { row -> row.allTextOf(FieldConcept.DRAW_NUMBER) }
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .sorted()
    return numbers.ifEmpty { null }
}

/** 15행이 온전할 때만 배열을 싣는다 — 부분 배열은 위치가 순번이라는 규약을 깬다(스키마 §3.2). */
private fun reservePricesOf(rows: List<RawRow>): List<BigDecimal>? {
    val bySequence =
        rows
            .mapNotNull { row ->
                val sequence = row.textOf(FieldConcept.RESERVE_PRICE_SEQUENCE)?.toIntOrNull()
                val amount = row.amountOf(FieldConcept.RESERVE_PRICE_PRELIMINARY)
                if (sequence == null || amount == null) null else sequence to amount
            }.toMap()
    val complete = (1..RESERVE_PRICE_SLOTS).all { it in bySequence }
    return if (complete) (1..RESERVE_PRICE_SLOTS).map { bySequence.getValue(it) } else null
}

/** A 합산액 — **술어가 참인 항목만** 더한다. 표준시장단가금액은 근거 예규 미확보로 제외한다(§3.3). */
private fun aValueTotalOf(row: RawRow): BigDecimal? {
    val always = A_ALWAYS_SUMMED.mapNotNull(row::amountOf)
    val quality =
        row
            .amountOf(FieldConcept.A_QUALITY_MANAGEMENT_COST)
            ?.takeIf { row.predicateOf(FieldConcept.A_QUALITY_MANAGEMENT_COST_APPLICABLE) == true }
    val parts = always + listOfNotNull(quality)
    return if (parts.isEmpty()) null else parts.reduce(BigDecimal::add)
}

private val A_ALWAYS_SUMMED =
    listOf(
        FieldConcept.A_NATIONAL_PENSION_PREMIUM,
        FieldConcept.A_HEALTH_INSURANCE_PREMIUM,
        FieldConcept.A_LONG_TERM_CARE_INSURANCE_PREMIUM,
        FieldConcept.A_RETIREMENT_MUTUAL_AID_CONTRIBUTION,
        FieldConcept.A_INDUSTRIAL_SAFETY_HEALTH_COST,
        FieldConcept.A_SAFETY_MANAGEMENT_COST,
    )
