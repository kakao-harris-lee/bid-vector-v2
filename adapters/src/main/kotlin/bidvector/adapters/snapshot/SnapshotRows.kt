package bidvector.adapters.snapshot

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * 스냅숏 한 행의 **투찰 시점** 절반(D-6G-2) — 개찰로 드러나는 값의 이름이 여기 없다. 전략에 넘어가는
 * 것은 이쪽뿐이라, 전략이 예정가격을 보려면 이 타입을 고쳐야 하고 그 편집은 diff 에 드러난다.
 *
 * `baseAmount` 는 **기초금액 조회 출처**이고 공개일시가 입찰 마감보다 이른 행만 값을 갖는다(D-6G-19
 * provenance 분리) — 개찰결과 출처의 기초금액은 [SnapshotOutcome] 쪽이다.
 */
data class SnapshotNotice(
    val noticeKeyHash: String,
    val category: String,
    val noticedOn: LocalDate,
    val bidCloseAt: Instant?,
    val baseAmount: BigDecimal?,
    val baseAmountDisclosedAt: Instant?,
    val floorRate: BigDecimal?,
    val reserveRangeBeginRate: BigDecimal?,
    val reserveRangeEndRate: BigDecimal?,
    val aValueTotal: BigDecimal?,
    val aValueOpenAt: Instant?,
    /** `smkpAmtYn` 의 술어(D-6G-23) — `Y`/`N` 밖의 값이나 부재는 `null`(판정 불가)이다. 지어내지 않는다. */
    val standardMarketPriceApplicable: Boolean?,
    val bidPriceFormulaAApplicable: Boolean?,
    val successfulBidMethodCode: String?,
    val successfulBidMethodName: String?,
    val prearrangedPriceDecisionMethod: String?,
    val awardMethodApplicationStandard: String?,
    val applicationBasisContent: String?,
    val noticeOrdinal: Int,
    val progressDivision: String?,
    val procurementClassCode: String?,
    val demandAgencyCode: String?,
    val pureConstructionCost: BigDecimal?,
)

/** 투찰자 한 행 — 상호·사업자번호·대표자명이 **없다**(D-6G-10). */
data class SnapshotBidderRow(
    val ordinal: Int,
    val rank: Int?,
    val amount: BigDecimal?,
)

/** 개찰로 드러나는 절반 — 채점만 읽는다. */
data class SnapshotOutcome(
    val openedOn: LocalDate,
    val plannedPrice: BigDecimal?,
    val openingBaseAmount: BigDecimal?,
    val reservePrices: List<BigDecimal>?,
    val drawnSerialNumbers: List<Int>?,
    val participantCount: Int?,
    val bidderRows: List<SnapshotBidderRow>,
)

data class SnapshotRow(
    val notice: SnapshotNotice,
    val outcome: SnapshotOutcome,
)

/**
 * 투찰자 순번 매김(D-6G-2 §1.3) — **투찰금액 오름차순, null 은 뒤로, 동값은 원문 행 순서**. 1부터.
 * `opengRank` 는 결측·중복이 흔해 자연 키로 못 쓰므로 이 순번이 투찰자의 유일한 식별이다.
 *
 * 원문 행 순서를 타이브레이크로 쓰는 것이 결정적인 이유는 입력 목록의 순서가 저장 순서(관측 순서)로
 * 고정되기 때문이다 — 같은 스냅숏을 다시 뽑으면 같은 순번이 나온다.
 */
fun orderedBidderRows(raw: List<Pair<Int?, BigDecimal?>>): List<SnapshotBidderRow> =
    raw
        .mapIndexed { index, (rank, amount) -> Triple(index, rank, amount) }
        .sortedWith(BIDDER_ORDER)
        .mapIndexed { position, (_, rank, amount) -> SnapshotBidderRow(position + 1, rank, amount) }

private val BIDDER_ORDER =
    Comparator<Triple<Int, Int?, BigDecimal?>> { left, right ->
        val byAmount = compareAmounts(left.third, right.third)
        if (byAmount != 0) byAmount else left.first.compareTo(right.first)
    }

/** null 은 **뒤로** — 「투찰금액이 없다」는 협상계약의 신호이지 0원 투찰이 아니다. */
private fun compareAmounts(
    left: BigDecimal?,
    right: BigDecimal?,
): Int =
    when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        else -> left.compareTo(right)
    }
