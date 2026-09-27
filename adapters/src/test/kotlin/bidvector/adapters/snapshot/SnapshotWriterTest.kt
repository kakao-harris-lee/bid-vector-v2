package bidvector.adapters.snapshot

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private fun notice(
    hash: String,
    awardStandard: String? = null,
): SnapshotNotice =
    SnapshotNotice(
        noticeKeyHash = hash,
        category = "SERVICE",
        noticedOn = LocalDate.of(2026, 6, 3),
        bidCloseAt = Instant.parse("2026-06-16T05:00:00Z"),
        baseAmount = BigDecimal("1234567890"),
        baseAmountDisclosedAt = Instant.parse("2026-06-10T00:00:00Z"),
        floorRate = BigDecimal("0.87745"),
        reserveRangeBeginRate = BigDecimal("-0.02"),
        reserveRangeEndRate = BigDecimal("0.02"),
        aValueTotal = null,
        aValueOpenAt = null,
        bidPriceFormulaAApplicable = null,
        successfulBidMethodCode = "낙030001",
        successfulBidMethodName = "적격심사제",
        prearrangedPriceDecisionMethod = "복수예가",
        awardMethodApplicationStandard = awardStandard,
        applicationBasisContent = null,
        noticeOrdinal = 0,
        progressDivision = "개찰완료",
        procurementClassCode = "81112200",
        demandAgencyCode = "6110000",
        pureConstructionCost = null,
    )

private fun outcome(bidders: List<Pair<Int?, BigDecimal?>>): SnapshotOutcome =
    SnapshotOutcome(
        openedOn = LocalDate.of(2026, 6, 17),
        plannedPrice = BigDecimal("1250000000"),
        openingBaseAmount = BigDecimal("1234567890"),
        reservePrices = null,
        drawnSerialNumbers = listOf(3, 7, 11, 14),
        participantCount = bidders.size,
        bidderRows = orderedBidderRows(bidders),
    )

private fun rowOf(
    hash: String,
    bidders: List<Pair<Int?, BigDecimal?>> = listOf(1 to BigDecimal("1100000000")),
): SnapshotRow = SnapshotRow(notice(hash), outcome(bidders))

/** D-6G-2 — 같은 입력이면 같은 바이트. 그 성질이 재현성의 고정점이다. */
class SnapshotWriterTest {
    @Test
    fun `행은 공고 키 해시 오름차순으로 쓰인다 — 입력 순서가 바이트에 새지 않는다`() {
        val rows = listOf(rowOf("cc"), rowOf("aa"), rowOf("bb"))

        val forward = SnapshotWriter.renderRows(rows)
        val reversed = SnapshotWriter.renderRows(rows.reversed())

        forward shouldBe reversed
        forward.lines().filter { it.isNotEmpty() }.map { it.substringAfter("\"notice_key_hash\":\"").take(2) } shouldBe
            listOf("aa", "bb", "cc")
    }

    @Test
    fun `줄마다 개행 하나로 끝나고 줄 안에는 개행이 없다`() {
        val rendered = SnapshotWriter.renderRows(listOf(rowOf("aa"), rowOf("bb")))

        rendered.endsWith("\n") shouldBe true
        rendered.trimEnd('\n').lines().size shouldBe 2
    }

    @Test
    fun `자유텍스트의 따옴표·개행·제어문자가 이스케이프된다 — 한 줄이 깨지지 않는다`() {
        val nasty = "조달청 \"시설공사\" 기준\n두 줄\t탭\\역슬래시\u0001제어"

        val rendered = SnapshotWriter.renderRows(listOf(rowOf("aa").let { it.copy(notice = notice("aa", nasty)) }))

        rendered.trimEnd('\n').lines().size shouldBe 1
        rendered shouldContain "\\\"시설공사\\\""
        rendered shouldContain "\\n"
        rendered shouldContain "\\t"
        rendered shouldContain "\\\\"
        rendered shouldContain "\\u0001"
    }

    @Test
    fun `금액은 소수점 없는 정수 리터럴이고 비율은 지수 표기를 쓰지 않는다`() {
        val row =
            rowOf("aa").let {
                it.copy(notice = it.notice.copy(floorRate = BigDecimal("1E-2"), baseAmount = BigDecimal("1200.00")))
            }

        val rendered = SnapshotWriter.renderRows(listOf(row))

        rendered shouldContain "\"base_amount\":1200"
        rendered shouldNotContain "1200.00"
        rendered shouldContain "\"floor_rate\":0.01"
        rendered shouldNotContain "1E-2"
    }

    @Test
    fun `투찰자 순번은 금액 오름차순이고 금액 없는 행은 뒤로 간다`() {
        val bidders = listOf<Pair<Int?, BigDecimal?>>(3 to BigDecimal("300"), null to null, 1 to BigDecimal("100"))

        val ordered = orderedBidderRows(bidders)

        ordered.map { it.ordinal } shouldBe listOf(1, 2, 3)
        ordered.map { it.amount } shouldBe listOf(BigDecimal("100"), BigDecimal("300"), null)
        ordered.map { it.rank } shouldBe listOf(1, 3, null)
    }

    @Test
    fun `동가는 원문 행 순서로 갈린다 — 순번이 흔들리지 않는다`() {
        val tied = listOf<Pair<Int?, BigDecimal?>>(9 to BigDecimal("100"), 4 to BigDecimal("100"))

        orderedBidderRows(tied).map { it.rank } shouldBe listOf(9, 4)
    }

    @Test
    fun `manifest 는 rows 바이트의 sha256 을 싣고 표본 목록 해시는 그대로 옮긴다`() {
        val rows = SnapshotWriter.renderRows(listOf(rowOf("aa")))

        val manifest =
            SnapshotWriter.renderManifest(
                snapshotId = "2026-09-28T01-00-00Z",
                rowsBytes = rows,
                rowCount = 1,
                periodStart = LocalDate.of(2026, 2, 6),
                periodEnd = LocalDate.of(2026, 9, 26),
                sampleListSha256 = "feedface",
            )

        manifest shouldContain "\"schema_version\":\"snapshot-v1\""
        manifest shouldContain "\"rows_sha256\":\"${sha256Hex(rows)}\""
        manifest shouldContain "\"sample_list_sha256\":\"feedface\""
    }

    @Test
    fun `투찰자 행에 상호·사업자번호 칸이 없다 — 타입에 자리가 없다`() {
        val rendered = SnapshotWriter.renderRows(listOf(rowOf("aa")))

        rendered shouldNotContain "prcbdr"
        rendered shouldNotContain "bizno"
        rendered.substringAfter("\"bidder_rows\":") shouldContain "\"ordinal\":1"
    }
}
