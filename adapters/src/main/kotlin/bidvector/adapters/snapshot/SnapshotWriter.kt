package bidvector.adapters.snapshot

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/**
 * `snapshot-v2`(D-6G-23) — A 묶음에 표준시장단가 적용 여부를 더하며 올렸다. 소비 쪽 판독은 **버전이
 * 다르면 스냅숏 전체를 거부한다**(의도된 동작이다 — 두 레인이 같이 움직여야 한다는 신호).
 */
const val SNAPSHOT_SCHEMA_VERSION: String = "snapshot-v2"

private const val HEX_MASK = 0xff

/**
 * 명시 Comparator 다 — `sortedBy` 는 stdlib 출처의 합성 비교자 클래스를 산출물에 남겨 jarContentGate
 * (게이트를 통과한 소스만 아카이브에 든다)가 거부한다.
 */
private val ROW_ORDER =
    Comparator<SnapshotRow> { left, right ->
        left.notice.noticeKeyHash.compareTo(right.notice.noticeKeyHash)
    }

/**
 * 스냅숏 두 파일의 바이트를 만든다(D-6G-2) — **같은 입력이면 같은 바이트**다. 그 성질이 재현성의
 * 고정점이라, 정렬·키 순서·수치 표기 셋을 전부 여기서 못박는다:
 *
 * - 행은 `notice_key_hash` **오름차순**(입력 목록의 순서가 판정에 새지 않게).
 * - 키는 **선언 순서**(맵 순회에 맡기지 않는다).
 * - 금액은 **정수 리터럴**(소수점 없음), 비율은 `BigDecimal.toPlainString`(지수 표기 없음).
 *
 * 저장은 하지 않는다 — 바이트만 낸다. 어디에 쓰는지는 호출부(저장소 **밖** 경로)가 정한다.
 */
object SnapshotWriter {
    /** `rows.jsonl` — 줄마다 한 공고, 끝에 개행 하나. */
    fun renderRows(rows: List<SnapshotRow>): String =
        rows
            .sortedWith(ROW_ORDER)
            .joinToString("") { row -> rowJson(row).render() + "\n" }

    /**
     * `manifest.json`. [sampleListSha256] 은 **수집 전에** 확정된 표본 목록의 해시이고 이 함수가
     * 다시 계산하지 않는다 — 결과에서 역산한 값을 싣지 않기 위해서다(우회 ⑦).
     */
    fun renderManifest(
        snapshotId: String,
        rowsBytes: String,
        rowCount: Int,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        sampleListSha256: String,
    ): String =
        SnapshotJson
            .Obj(
                listOf(
                    "schema_version" to SnapshotJson.Text(SNAPSHOT_SCHEMA_VERSION),
                    "snapshot_id" to SnapshotJson.Text(snapshotId),
                    "row_count" to SnapshotJson.Number(rowCount.toString()),
                    "period_start" to SnapshotJson.Text(periodStart.toString()),
                    "period_end" to SnapshotJson.Text(periodEnd.toString()),
                    "rows_sha256" to SnapshotJson.Text(sha256Hex(rowsBytes)),
                    "sample_list_sha256" to SnapshotJson.Text(sampleListSha256),
                ),
            ).render()
}

internal fun sha256Hex(text: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and HEX_MASK) }

private fun rowJson(row: SnapshotRow): SnapshotJson =
    SnapshotJson.Obj(
        listOf(
            "notice" to noticeJson(row.notice),
            "outcome" to outcomeJson(row.outcome),
        ),
    )

private fun noticeJson(notice: SnapshotNotice): SnapshotJson =
    SnapshotJson.Obj(
        listOf(
            "notice_key_hash" to SnapshotJson.Text(notice.noticeKeyHash),
            "category" to SnapshotJson.Text(notice.category),
            "noticed_on" to jsonDate(notice.noticedOn),
            "bid_close_at" to jsonInstant(notice.bidCloseAt),
            "base_amount" to jsonAmount(notice.baseAmount),
            "base_amount_disclosed_at" to jsonInstant(notice.baseAmountDisclosedAt),
            "floor_rate" to jsonRate(notice.floorRate),
            "reserve_range_begin_rate" to jsonRate(notice.reserveRangeBeginRate),
            "reserve_range_end_rate" to jsonRate(notice.reserveRangeEndRate),
            "a_value" to aValue(notice),
            "bid_price_formula_a_applicable" to jsonBool(notice.bidPriceFormulaAApplicable),
            "successful_bid_method_code" to jsonText(notice.successfulBidMethodCode),
            "successful_bid_method_name" to jsonText(notice.successfulBidMethodName),
            "prearranged_price_decision_method" to jsonText(notice.prearrangedPriceDecisionMethod),
            "award_method_application_standard" to jsonText(notice.awardMethodApplicationStandard),
            "application_basis_content" to jsonText(notice.applicationBasisContent),
            "notice_ordinal" to SnapshotJson.Number(notice.noticeOrdinal.toString()),
            "progress_division" to jsonText(notice.progressDivision),
            "procurement_class_code" to jsonText(notice.procurementClassCode),
            "demand_agency_code" to jsonText(notice.demandAgencyCode),
            "pure_construction_cost" to jsonAmount(notice.pureConstructionCost),
        ),
    )

/**
 * A 는 합산액과 **그 자신의 공개일시**가 한 묶음이다 — 둘을 떼면 누출 판정을 못 한다(D-6G-13 ⑥).
 *
 * 표준시장단가 적용 여부(D-6G-23)도 이 묶음 안이다. `a_value` 가 `null` 이면 이 술어도 없고 그것이
 * 옳다 — 그 배제는 A 의 **합산액**에만 영향을 주므로 A 가 없는 공고는 영향 범위 밖이다(계수의 분모가
 * 「A 값을 가진 공고」인 이유).
 */
private fun aValue(notice: SnapshotNotice): SnapshotJson =
    notice.aValueTotal?.let { total ->
        SnapshotJson.Obj(
            listOf(
                "total" to jsonAmount(total),
                "open_at" to jsonInstant(notice.aValueOpenAt),
                "standard_market_price_applicable" to jsonBool(notice.standardMarketPriceApplicable),
            ),
        )
    } ?: SnapshotJson.Null

private fun outcomeJson(outcome: SnapshotOutcome): SnapshotJson =
    SnapshotJson.Obj(
        listOf(
            "opened_on" to jsonDate(outcome.openedOn),
            "planned_price" to jsonAmount(outcome.plannedPrice),
            "opening_base_amount" to jsonAmount(outcome.openingBaseAmount),
            "reserve_prices" to outcome.reservePrices.jsonArrayOrNull { list -> list.map(::jsonAmount) },
            "drawn_serial_numbers" to
                outcome.drawnSerialNumbers.jsonArrayOrNull { list ->
                    list.map { SnapshotJson.Number(it.toString()) }
                },
            "participant_count" to jsonCount(outcome.participantCount),
            "bidder_rows" to SnapshotJson.Arr(outcome.bidderRows.map(::bidderJson)),
        ),
    )

private fun bidderJson(row: SnapshotBidderRow): SnapshotJson =
    SnapshotJson.Obj(
        listOf(
            "ordinal" to SnapshotJson.Number(row.ordinal.toString()),
            "rank" to jsonCount(row.rank),
            "amount" to jsonAmount(row.amount),
        ),
    )
