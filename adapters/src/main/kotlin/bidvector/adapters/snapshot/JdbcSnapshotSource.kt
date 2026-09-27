package bidvector.adapters.snapshot

import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.SourceEndpoint
import bidvector.workflow.collection.NoticeKeyHash
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate
import javax.sql.DataSource

/**
 * dev DB → 실험 스냅숏 행(D-6G-2) — **두 출처를 잇는다.**
 *
 * - `raw_observation`: 6G 수집 갈래가 적재한 개찰 축 원문(예비가격 상세·개찰완료·기초금액·A값).
 *   이 갈래는 canonical 승격을 하지 않으므로 그 값들은 여기에만 있다(D-6G-1).
 * - `notice`: 공고 목록 갈래가 세운 canonical fact. **업무 대분류·낙찰하한율·마감일시**가 여기에만
 *   있다 — 개찰 축 응답에는 그 셋이 없다.
 *
 * 그래서 **두 갈래가 다 돌아야 스냅숏이 선다.** 공고 목록 관측이 없는 공고는 대분류를 알 수 없어
 * 행이 만들어지지 않는다(지어내지 않는다) — 그 수는 [SnapshotExtraction.skippedWithoutNotice] 가 센다.
 *
 * 값은 **계약 경유**로만 읽는다(raw 키 리터럴이 이 파일에 없다) — 키를 아는 것은 계약이고, 이 어댑터는
 * 개념 이름만 안다.
 */
class JdbcSnapshotSource(
    private val dataSource: DataSource,
    private val policy: KonepsCollectionPolicyData,
) {
    fun extract(
        from: LocalDate,
        to: LocalDate,
    ): SnapshotExtraction {
        val observations = readObservations(from, to)
        val notices = readNotices()
        val rows = mutableListOf<SnapshotRow>()
        var skipped = 0
        for ((key, axes) in observations) {
            val canonical = notices[key]
            if (canonical == null) {
                skipped++
            } else {
                rows += assemble(key, axes, canonical)
            }
        }
        return SnapshotExtraction(rows, skipped)
    }

    private fun assemble(
        key: NoticeKey,
        axes: Map<SourceEndpoint, List<RawRow>>,
        canonical: CanonicalNotice,
    ): SnapshotRow =
        SnapshotRow(
            notice = noticeOf(key, axes, canonical),
            outcome = outcomeOf(axes, canonical),
        )

    private fun noticeOf(
        key: NoticeKey,
        axes: Map<SourceEndpoint, List<RawRow>>,
        canonical: CanonicalNotice,
    ): SnapshotNotice {
        val baseAmountRow = axes[SourceEndpoint.BASE_AMOUNT_DETAIL]?.firstOrNull()
        val formulaARow = axes[SourceEndpoint.BID_PRICE_FORMULA_A]?.firstOrNull()
        val listRow = axes[SourceEndpoint.OPENING_RESULT_LIST]?.firstOrNull()
        val disclosedAt = baseAmountRow?.instantOf(FieldConcept.BASE_AMOUNT_DISCLOSED_AT)
        // D-6G-19 provenance 분리 — 마감 뒤 공개된 기초금액은 투찰 시점에 없던 값이다.
        val knownAtBidTime = disclosedAt != null && canonical.bidCloseAt != null && disclosedAt < canonical.bidCloseAt
        return SnapshotNotice(
            noticeKeyHash = NoticeKeyHash.of(key.number, key.round).value,
            category = canonical.division,
            noticedOn = canonical.noticedOn ?: canonical.openedOn,
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
            successfulBidMethodCode = canonical.awardMethodCode,
            successfulBidMethodName = canonical.awardMethodName,
            prearrangedPriceDecisionMethod = formulaARow?.textOf(FieldConcept.PLANNED_PRICE_DECISION_METHOD),
            awardMethodApplicationStandard = canonical.awardMethodStandard,
            applicationBasisContent = canonical.applicationBasis,
            noticeOrdinal = key.round.toIntOrNull() ?: 0,
            progressDivision = listRow?.textOf(FieldConcept.PROGRESS_DIVISION),
            procurementClassCode = canonical.procurementClassCode,
            demandAgencyCode = canonical.demandAgencyCode,
            pureConstructionCost = baseAmountRow?.amountOf(FieldConcept.PURE_CONSTRUCTION_COST),
        )
    }

    private fun outcomeOf(
        axes: Map<SourceEndpoint, List<RawRow>>,
        canonical: CanonicalNotice,
    ): SnapshotOutcome {
        val reserveRows = axes[SourceEndpoint.RESERVE_PRICE_DETAIL].orEmpty()
        val listRow = axes[SourceEndpoint.OPENING_RESULT_LIST]?.firstOrNull()
        val bidders =
            axes[SourceEndpoint.OPENING_COMPLETE].orEmpty().map { row ->
                row.countOf(FieldConcept.OPENING_RANK) to row.amountOf(FieldConcept.BID_AMOUNT)
            }
        return SnapshotOutcome(
            openedOn = canonical.openedOn,
            plannedPrice = reserveRows.firstNotNullOfOrNull { it.amountOf(FieldConcept.RESERVE_PRICE) },
            openingBaseAmount = reserveRows.firstNotNullOfOrNull { it.amountOf(FieldConcept.BASE_AMOUNT) },
            reservePrices = reservePricesOf(reserveRows),
            drawnSerialNumbers = null,
            participantCount = listRow?.countOf(FieldConcept.PARTICIPANT_COUNT),
            bidderRows = orderedBidderRows(bidders),
        )
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

    private fun readObservations(
        from: LocalDate,
        to: LocalDate,
    ): Map<NoticeKey, Map<SourceEndpoint, List<RawRow>>> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(OBSERVATION_SQL).use { statement ->
                statement.setObject(1, from)
                statement.setObject(2, to.plusDays(1))
                statement.executeQuery().use { rows -> groupObservations(rows) }
            }
        }

    private fun groupObservations(rows: ResultSet): Map<NoticeKey, Map<SourceEndpoint, List<RawRow>>> {
        val byKey = linkedMapOf<NoticeKey, MutableMap<SourceEndpoint, MutableList<RawRow>>>()
        while (rows.next()) {
            keyAndEndpointOf(rows)?.let { (key, endpoint) ->
                byKey
                    .getOrPut(key) { linkedMapOf() }
                    .getOrPut(endpoint) { mutableListOf() }
                    .add(RawRow(parseFields(rows.getString("payload_fields")), policy))
            }
        }
        return byKey
    }

    /** 식별자나 엔드포인트 어휘가 서지 않는 행은 조용히 지나간다 — 지어내지 않는다. */
    private fun keyAndEndpointOf(rows: ResultSet): Pair<NoticeKey, SourceEndpoint>? {
        val number = rows.getString("notice_number")
        val round = rows.getString("notice_round")
        val endpoint = runCatching { SourceEndpoint.valueOf(rows.getString("source_endpoint")) }.getOrNull()
        return if (number == null || round == null || endpoint == null) null else NoticeKey(number, round) to endpoint
    }

    private fun readNotices(): Map<NoticeKey, CanonicalNotice> =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(NOTICE_SQL).use(::collectNotices)
            }
        }

    private fun collectNotices(rows: ResultSet): Map<NoticeKey, CanonicalNotice> {
        val out = linkedMapOf<NoticeKey, CanonicalNotice>()
        while (rows.next()) {
            val key = NoticeKey(rows.getString("notice_number"), rows.getString("notice_round"))
            canonicalNoticeOf(rows, policy)?.let { out[key] = it }
        }
        return out
    }
}

internal const val RESERVE_PRICE_SLOTS = 15

private val A_ALWAYS_SUMMED =
    listOf(
        FieldConcept.A_NATIONAL_PENSION_PREMIUM,
        FieldConcept.A_HEALTH_INSURANCE_PREMIUM,
        FieldConcept.A_LONG_TERM_CARE_INSURANCE_PREMIUM,
        FieldConcept.A_RETIREMENT_MUTUAL_AID_CONTRIBUTION,
        FieldConcept.A_INDUSTRIAL_SAFETY_HEALTH_COST,
        FieldConcept.A_SAFETY_MANAGEMENT_COST,
    )

/** 추출 결과 — 행과, 공고 목록 관측이 없어 만들지 못한 공고 수(지어내지 않은 계수). */
data class SnapshotExtraction(
    val rows: List<SnapshotRow>,
    val skippedWithoutNotice: Int,
)

internal data class NoticeKey(
    val number: String,
    val round: String,
)
