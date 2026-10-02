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
 * 스냅숏이 싣는 금액은 **원 단위 정수**다(스키마 §1·§2.2) — 소수점이 없어야 양쪽 언어에서 왕복이
 * exact 하다. 끝자리 0 은 소수부가 아니다(`1200.00` 은 정수다).
 *
 * **판정하는 자리 옆에 둔다** — 소비자는 둘이지만(조립이 거르고, 렌더의 마지막 방어가 같은 술어를
 * 본다) 값을 버리는 결정은 이 파일의 [AssemblyTally] 가 한다.
 */
internal fun BigDecimal.isWonInteger(): Boolean = stripTrailingZeros().scale() <= 0

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
            value == null -> {
                null
            }

            value.isWonInteger() -> {
                value
            }

            else -> {
                fractionalAmounts++
                null
            }
        }

    /**
     * A 묶음이 **전부 아니면 무**의 규율로 사라진 수(D-6G2d-21) — 소수부와 다른 원인이라 칸을 따로
     * 둔다. 하나는 「원천이 소수를 냈다」이고 이것은 「원문이 반쪽이다」다.
     */
    var incompleteAValues: Int = 0
        private set

    fun countIncompleteAValue() {
        incompleteAValues++
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

    /**
     * **투찰자 목록도 전부 아니면 무다**(D-6G2d-43). 한 명의 금액만 `null` 로 두면 순번이 **밀린다** —
     * 순번은 금액 오름차순에 `null` 을 뒤로 두어 매기므로(D-6G-2 §1.3), 비운 한 명이 맨 뒤로 가고 그
     * 뒤의 모든 순위가 한 칸씩 당겨진다. 1위 투찰가와 참가자 구성이 조용히 달라지고, 판독은 그 행을
     * 「수의계약」 계열 사유로 빼 **사유까지 틀린다**. 목록을 통째로 비우면 기존의 행 단위 제외로
     * 떨어지고 계수는 한 번이다(집계 둘과 같은 규율).
     *
     * 금액이 **없는** 투찰자는 여기서 비우지 않는다 — 원천의 정직한 결측이고 순번 규칙이 이미 그
     * 자리를 정해 둔다(`null` 은 뒤로).
     */
    fun wonBidderRows(raw: List<Pair<Int?, BigDecimal?>>): List<SnapshotBidderRow> =
        if (raw.any { (_, amount) -> amount != null && !amount.isWonInteger() }) {
            fractionalAmounts++
            emptyList()
        } else {
            orderedBidderRows(raw)
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
    val formulaAApplies = baseAmountRow?.predicateOf(FieldConcept.BID_PRICE_FORMULA_A_APPLICABLE)
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
        // 계수의 정본은 기초금액 축 술어이고, 그 축이 없을 때만 A 행이 가른다(D-6G2e-15).
        aValueTotal = formulaARow?.let { aValueTotalOf(it, formulaAApplies) },
        aValueOpenAt = formulaARow?.instantOf(FieldConcept.BID_PRICE_FORMULA_A_DISCLOSED_AT),
        standardMarketPriceApplicable =
            formulaARow?.predicateOf(FieldConcept.A_STANDARD_MARKET_UNIT_PRICE_APPLICABLE),
        bidPriceFormulaAApplicable = formulaAApplies,
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
    // 금액을 칸 단위로 비우지 않는다(D-6G2d-43) — 목록 전체의 판정은 [AssemblyTally.wonBidderRows] 가
    // 한 자리에서 한다. 여기서 `wonAmount` 를 쓰면 한 명만 비어 순번이 밀린다.
    val bidders =
        axes[SourceEndpoint.OPENING_COMPLETE].orEmpty().map { row ->
            row.countOf(FieldConcept.OPENING_RANK) to row.amountOf(FieldConcept.BID_AMOUNT)
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
        bidderRows = wonBidderRows(bidders),
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

/**
 * A 합산액 — **술어가 참인 항목만** 더한다. 표준시장단가금액은 근거 예규 미확보로 제외한다(§3.3).
 *
 * **A 묶음은 전부 아니면 무다**(D-6G2d-21). 두 자리에서 통째로 사라진다.
 *
 * ⓐ **공개일시가 없으면** 없다. 스키마 §2.2 는 `a_value` 를 `{total:int, open_at:datetime, …} | null`
 * 로 두고 `open_at` 은 널을 허용하지 않는다 — 합산액만 싣고 공개일시를 `null` 로 두면 그 바이트는 두
 * 모양 어느 쪽도 아니고 판독이 **스냅숏 전체**를 거부한다(D-6G2d-15 와 같은 계열). 공개일시는 누출
 * 판정의 입력이므로(D-6G-13 ⑥) 없는 A 는 채점에 쓸 수도 없다.
 *
 * ⓑ **구성 항목 하나라도 결측이면** 없다. 앞 판은 결측 항목을 `mapNotNull` 로 빼고 나머지를 더해 A 를
 * **조용히 줄였다**(6G 부터의 부채) — 줄어든 A 는 오류도 결측도 아닌 **틀린 값**이라 채점에 그대로
 * 들어간다. 공사 하한가가 `(예정가격 − A) × r + A` 라 A 가 작으면 하한가를 낮게 잡고 적격 판정 자체가
 * 틀린다. 술어가 거짓인 품질관리비는 결측이 아니다(합산 대상이 아니다).
 *
 * ⓒ **합산 대상 술어가 `Y`/`N` 밖이면** 없다(D-6G2d-28). 술어가 부재·빈 문자열·제3의 값이면 그 항목이
 * 합산에 드는지 **모르는** 것이고, 「모름」은 「대상 아님」이 아니라 **「A 를 낼 수 없음」**이다. 앞 판은
 * 술어가 참일 때만 항목을 넣어 모름을 조용히 거짓으로 접었고, A 가 그 금액만큼 작게 실렸다(실측
 * 6,000,000 vs 6,500,000, 계수 0). 필드 계약이 술어를 boolean 으로 접지 않는 이유와 같은 방향이다 —
 * 그 자리에서 값을 지어내지 않는다.
 *
 * 셋 다 기존의 이름 있는 행 단위 제외(A 부재)로 떨어지고 그 수는 로그가 공시한다.
 *
 * **계수의 정본은 기초금액 축 술어다**(D-6G2e-15 — vr r1 H-1). 그 술어가 `Y` 면 센다: 원천이
 * A 행을 통째로 비워 보내거나 금액을 읽을 수 없게 보내는 것이 가장 흔한 반쪽이고, 그것은 「A 가
 * 없는 공고」가 아니라 **결손**이다. 그 축이 **걷히지 않았거나 술어를 모를 때만** A 행이 싣는
 * 입력으로 가른다(D-6G2e-4 가 고친 자리 — 그 축이 미완인 공고의 결손이 세어지지 않았다).
 * 술어가 `N` 이면 세지 않는다: 그 공고에 A 가 없다.
 *
 * **A 안의 다른 `*Yn` 술어**(표준시장단가 적용 여부)는 합산을 가르지 않는다 — 그 금액은 근거 예규
 * 미확보로 §3.3 이 합산에서 **항상** 빼고, 술어 자신은 스키마가 `bool | null` 로 두어 모름이 합법이다.
 * 그래서 이 규칙의 대상은 합산을 가르는 술어 하나다(실측: 합산 목록을 가르는 술어는 그것뿐).
 */
private fun AssemblyTally.aValueTotalOf(
    row: RawRow,
    appliesByBaseAmount: Boolean?,
): BigDecimal? {
    val qualityApplies = row.predicateOf(FieldConcept.A_QUALITY_MANAGEMENT_COST_APPLICABLE)
    val parts = aValuePartsOf(row, qualityApplies)
    val disclosedAt = row.instantOf(FieldConcept.BID_PRICE_FORMULA_A_DISCLOSED_AT)
    return when {
        qualityApplies == null || disclosedAt == null || parts.any { it == null } -> {
            if (appliesByBaseAmount ?: row.carriesAValueInput()) countIncompleteAValue()
            null
        }

        else -> {
            wonAggregate(parts.filterNotNull()) { integral -> integral.reduce(BigDecimal::add) }
        }
    }
}

/**
 * 이 행이 A 합산의 **입력을 하나라도** 싣는가 — 기초금액 축 술어를 모를 때만 쓰는 대체 판정이다
 * (D-6G2e-15). 구성 항목 여섯 · 공개일시 · 품질관리비의 **금액과 술어**를 본다.
 *
 * 품질관리비 금액은 **그 술어와 무관하게** 입력이다. 술어가 없거나 빈 값이면 합산 목록에 들어가지
 * 않지만(`aValuePartsOf`), 그 금액이 실렸다는 것 자체가 「이 공고에 A 가 있다」의 증거다 — 합산
 * 목록으로만 보면 그 행이 「입력 없음」으로 보여 결손이 세어지지 않았다(vr r1 H-1 의 두 모양).
 *
 * 금액은 **읽힌 값**으로 본다 — 칸은 있는데 숫자로 읽히지 않는 원문은 입력이 아니다(그 모양에서는
 * 기초금액 축 술어가 답한다).
 */
private fun RawRow.carriesAValueInput(): Boolean =
    A_ALWAYS_SUMMED.any { amountOf(it) != null } ||
        amountOf(FieldConcept.A_QUALITY_MANAGEMENT_COST) != null ||
        predicateOf(FieldConcept.A_QUALITY_MANAGEMENT_COST_APPLICABLE) != null ||
        instantOf(FieldConcept.BID_PRICE_FORMULA_A_DISCLOSED_AT) != null

/**
 * A 합산의 구성 항목 — `null` 원소는 **결측**이고 빼지 않는다(D-6G2d-21 ⓑ). 품질관리비는 [qualityApplies]
 * 가 **참일 때만** 목록에 들어온다: 거짓이면 합산 대상이 아니므로 결측이 아니고, **모름**(`null`)은 이
 * 함수가 답할 물음이 아니다 — 부르는 쪽이 A 를 비운다(D-6G2d-28).
 */
private fun aValuePartsOf(
    row: RawRow,
    qualityApplies: Boolean?,
): List<BigDecimal?> =
    A_ALWAYS_SUMMED.map(row::amountOf) +
        if (qualityApplies == true) {
            listOf(row.amountOf(FieldConcept.A_QUALITY_MANAGEMENT_COST))
        } else {
            emptyList()
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
