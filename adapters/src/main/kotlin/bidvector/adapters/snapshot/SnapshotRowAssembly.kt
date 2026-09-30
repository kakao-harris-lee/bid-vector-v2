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
    tally: AssemblyTally,
): SnapshotRow =
    SnapshotRow(
        notice = tally.noticeOf(key, axes, canonical),
        outcome = tally.outcomeOf(axes),
    )

/**
 * 조립이 **형태를 어겨 버린 값**의 계수(D-6G2d-15). 조립 자체는 값 함수로 두고 계수만 밖으로 나른다 —
 * 소수부 금액이 한 번 오면 그 칸(또는 집계)이 조용히 비므로, 세지 않으면 백테스트 판정 보고에서 그
 * 제외가 보통의 값 결측과 구별되지 않는다(A-2: 스키마를 올리지 않고 **공시**한다).
 */
internal class AssemblyTally {
    var fractionalAmounts: Int = 0
        private set

    /**
     * 원 단위 정수만 싣는다 — 소수부가 있으면 **없는 값**이고 그 사실을 센다. 스칼라 금액 칸 다섯은
     * 스키마가 `int | null` 이라 `null` 이 합법이고 기존의 이름 있는 행 단위 제외로 떨어진다.
     */
    fun wonAmount(value: BigDecimal?): BigDecimal? =
        when {
            value == null -> null
            value.isWonInteger() -> value
            else -> {
                fractionalAmounts++
                null
            }
        }

    /**
     * **집계는 통째로 없어진다**(vr r1 H-1). `a_value.total` 과 `reserve_prices[i]` 는 스키마 §2.2 가
     * `int` 를 **필수**로 두는 자리다 — 원소만 `null` 로 두면 그 바이트는 `{total:int, …} | null` 도
     * `int[15] | null` 도 아니고, 판독은 그 행이 아니라 **스냅숏 전체**를 거부한다. 집계를 `null` 로
     * 내면 기존의 이름 있는 제외(A 결측 · 예비가격 결측)로 떨어진다.
     */
    fun <T> wonAggregate(
        values: List<BigDecimal>,
        fold: (List<BigDecimal>) -> T,
    ): T? =
        if (values.all { it.isWonInteger() }) {
            fold(values)
        } else {
            fractionalAmounts++
            null
        }
}

private fun AssemblyTally.noticeOf(
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
        noticeKeyHash = NoticeKeyHash.of(key.number, key.round.value).value,
        category = canonical.division,
        // **개찰일로 대체하지 않는다**(D-6G-28) — 그렇게 접으면 제외 ⑬ 이 개찰일로 돌아
        // 시행일 전에 공고되고 후에 개찰된 공고가 승인된다.
        noticedOn = noticeListRow?.localDateOf(FieldConcept.NOTICE_POSTED_AT),
        bidCloseAt = canonical.bidCloseAt,
        baseAmount = wonAmount(baseAmountRow?.takeIf { knownAtBidTime }?.amountOf(FieldConcept.BASE_AMOUNT)),
        baseAmountDisclosedAt = disclosedAt,
        floorRate = canonical.floorRate,
        reserveRangeBeginRate = baseAmountRow?.rateOf(FieldConcept.RESERVE_PRICE_RANGE_BEGIN_RATE),
        reserveRangeEndRate = baseAmountRow?.rateOf(FieldConcept.RESERVE_PRICE_RANGE_END_RATE),
        aValueTotal = formulaARow?.let { aValueTotalOf(it) },
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
        noticeOrdinal = key.round.value.toInt(),
        procurementClassCode = noticeListRow?.textOf(FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE),
        demandAgencyCode = noticeListRow?.textOf(FieldConcept.DEMAND_AGENCY_CODE),
        pureConstructionCost = wonAmount(baseAmountRow?.amountOf(FieldConcept.PURE_CONSTRUCTION_COST)),
    )
}

private fun AssemblyTally.outcomeOf(axes: Map<SourceEndpoint, List<RawRow>>): SnapshotOutcome {
    val reserveRows = axes[SourceEndpoint.RESERVE_PRICE_DETAIL].orEmpty()
    val listRow = axes[SourceEndpoint.OPENING_RESULT_LIST]?.firstOrNull()
    val bidders =
        axes[SourceEndpoint.OPENING_COMPLETE].orEmpty().map { row ->
            row.countOf(FieldConcept.OPENING_RANK) to wonAmount(row.amountOf(FieldConcept.BID_AMOUNT))
        }
    return SnapshotOutcome(
        // 대체값(EPOCH)을 지어내지 않는다 — 없으면 행 단위 제외의 입력이다(D-6G-28).
        openedOn = reserveRows.firstNotNullOfOrNull { it.localDateOf(FieldConcept.ACTUAL_OPENING_AT) },
        progressDivision = listRow?.textOf(FieldConcept.PROGRESS_DIVISION),
        plannedPrice = wonAmount(reserveRows.firstNotNullOfOrNull { it.amountOf(FieldConcept.RESERVE_PRICE) }),
        openingBaseAmount = wonAmount(reserveRows.firstNotNullOfOrNull { it.amountOf(FieldConcept.BASE_AMOUNT) }),
        reservePrices = reservePricesOf(reserveRows),
        drawnSerialNumbers = drawnSerialNumbersOf(reserveRows),
        participantCount = listRow?.countOf(FieldConcept.PARTICIPANT_COUNT),
        bidderRows = orderedBidderRows(bidders),
    )
}

/**
 * 추첨번호(D-6G-38) — **예비가격 상세에서 `drwtYn` 이 참인 행의 순번**이다. legacy 도 이렇게 읽는다.
 *
 * r1 은 개찰완료 투찰 행의 `drwtNo1`·`drwtNo2` 를 읽었는데 그것은 **투찰자가 고른 번호**이지 추첨된
 * 번호가 아니다. 둘은 대개 다르고, 예정가격은 뽑힌 넷의 평균이라 선택을 쓰면 사정률이 통째로 틀린다.
 * r1 의 golden 이 이 자리를 가린 것은 mock 이 투찰자 선택을 뽑힌 넷과 **같게** 맞춰 두었기 때문이다.
 *
 * 값은 1-기반 순번이고 투찰자 귀속은 보존하지 않는다 — 실현 사정률에는 집합만 있으면 된다.
 */
private fun drawnSerialNumbersOf(reserveRows: List<RawRow>): List<Int>? {
    val numbers =
        reserveRows
            .filter { row -> row.predicateOf(FieldConcept.DRAW_FLAG) == true }
            .mapNotNull { row -> row.textOf(FieldConcept.RESERVE_PRICE_SEQUENCE)?.trim()?.toIntOrNull() }
            .distinct()
            .sorted()
    return numbers.ifEmpty { null }
}

/** 15행이 온전할 때만 배열을 싣는다 — 부분 배열은 위치가 순번이라는 규약을 깬다(스키마 §3.2). */
private fun AssemblyTally.reservePricesOf(rows: List<RawRow>): List<BigDecimal>? {
    val bySequence =
        rows
            .mapNotNull { row ->
                val sequence = row.textOf(FieldConcept.RESERVE_PRICE_SEQUENCE)?.toIntOrNull()
                val amount = row.amountOf(FieldConcept.RESERVE_PRICE_PRELIMINARY)
                if (sequence == null || amount == null) null else sequence to amount
            }.toMap()
    val complete = (1..RESERVE_PRICE_SLOTS).all { it in bySequence }
    if (!complete) return null
    return wonAggregate((1..RESERVE_PRICE_SLOTS).map { bySequence.getValue(it) }) { it }
}

/** A 합산액 — **술어가 참인 항목만** 더한다. 표준시장단가금액은 근거 예규 미확보로 제외한다(§3.3). */
private fun AssemblyTally.aValueTotalOf(row: RawRow): BigDecimal? {
    val always = A_ALWAYS_SUMMED.mapNotNull(row::amountOf)
    val quality =
        row
            .amountOf(FieldConcept.A_QUALITY_MANAGEMENT_COST)
            ?.takeIf { row.predicateOf(FieldConcept.A_QUALITY_MANAGEMENT_COST_APPLICABLE) == true }
    val parts = always + listOfNotNull(quality)
    if (parts.isEmpty()) return null
    return wonAggregate(parts) { integral -> integral.reduce(BigDecimal::add) }
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
