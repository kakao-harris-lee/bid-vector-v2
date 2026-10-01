package bidvector.adapters.snapshot

import bidvector.workflow.collection.sha256Hex
import io.kotest.assertions.throwables.shouldThrow
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
        standardMarketPriceApplicable = null,
        bidPriceFormulaAApplicable = null,
        successfulBidMethodCode = "낙030001",
        successfulBidMethodName = "적격심사제",
        prearrangedPriceDecisionMethod = "복수예가",
        hasAwardMethodApplicationStandard = awardStandard != null,
        hasApplicationBasisContent = false,
        noticeOrdinal = 0,
        procurementClassCode = "81112200",
        demandAgencyCode = "6110000",
        pureConstructionCost = null,
    )

private fun outcome(bidders: List<Pair<Int?, BigDecimal?>>): SnapshotOutcome =
    SnapshotOutcome(
        openedOn = LocalDate.of(2026, 6, 17),
        progressDivision = "개찰완료",
        plannedPrice = BigDecimal("1250000000"),
        openingBaseAmount = BigDecimal("1239999999"),
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
    fun `문자열 칸의 따옴표·개행·제어문자가 이스케이프된다 — 한 줄이 깨지지 않는다`() {
        val nasty = "조달청 \"시설공사\" 기준\n두 줄\t탭\\역슬래시\u0001제어"

        val rendered =
            SnapshotWriter.renderRows(
                listOf(rowOf("aa").let { it.copy(notice = it.notice.copy(successfulBidMethodName = nasty)) }),
            )

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

    /**
     * **cr r4 ⑥ — 조립을 거치지 않은 소수 금액은 조용한 `null` 이 되지 않는다.** 소수부의 처분은
     * 조립이 정하고 그 자리가 계수한다(D-6G2d-15) — 렌더까지 소수가 왔다는 것은 **계수되지 않는
     * 생산자**가 생겼다는 뜻이고, 그것을 `null` 로 접으면 판독이 「값이 없는 칸」과 「셈에서 빠진 칸」을
     * 구별할 수 없다. 이 slice 가 고치고 있는 결함 계열 그대로다.
     *
     * 값 칸과 투찰자 칸 **둘 다** 본다 — 같은 함수를 지나므로, 한쪽만 재면 그 함수가 아니라 그 칸을
     * 잠근 것이 된다. 실 경로에서는 발화하지 않는다(조립이 이미 비웠다).
     */
    @Test
    fun `조립을 거치지 않은 소수 금액은 렌더에서 던진다`() {
        val noticeCell = rowOf("aa").let { it.copy(notice = it.notice.copy(baseAmount = BigDecimal("1234567890.01"))) }
        val bidderCell = rowOf("bb", bidders = listOf(1 to BigDecimal("1100000000.5")))

        shouldThrow<IllegalStateException> { SnapshotWriter.renderRows(listOf(noticeCell)) }
        shouldThrow<IllegalStateException> { SnapshotWriter.renderRows(listOf(bidderCell)) }
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
    fun `두 기초금액은 각자 자기 칸에 실린다 — 값이 다를 때만 드러나는 자리다`() {
        val rendered = SnapshotWriter.renderRows(listOf(rowOf("aa")))

        // 투찰 시점 칸과 개찰 출처 칸이 **서로의 대용이 아니다**(D-6G-19 provenance 분리).
        // 둘을 같은 값으로 둔 fixture 에서는 뒤바뀜 변이가 살아남는다.
        rendered shouldContain "\"base_amount\":1234567890"
        rendered shouldContain "\"opening_base_amount\":1239999999"
        rendered.substringBefore("\"outcome\"") shouldNotContain "1239999999"
        rendered.substringAfter("\"outcome\"") shouldNotContain "1234567890"
    }

    @Test
    fun `A 묶음은 합산액·공개일시·표준시장단가 술어 셋을 함께 싣는다 — D-6G-23`() {
        val withA =
            rowOf("aa").let {
                it.copy(
                    notice =
                        it.notice.copy(
                            aValueTotal = BigDecimal("98000000"),
                            aValueOpenAt = Instant.parse("2026-06-15T00:00:00Z"),
                            standardMarketPriceApplicable = true,
                        ),
                )
            }

        val rendered = SnapshotWriter.renderRows(listOf(withA))

        rendered shouldContain
            "\"a_value\":{\"total\":98000000,\"open_at\":\"2026-06-15T00:00:00Z\"," +
            "\"standard_market_price_applicable\":true}"
    }

    @Test
    fun `A 가 없으면 술어도 없다 — 배제의 계수 분모는 A 값을 가진 공고다`() {
        val rendered = SnapshotWriter.renderRows(listOf(rowOf("aa")))

        rendered shouldContain "\"a_value\":null"
        rendered shouldNotContain "standard_market_price_applicable"
    }

    @Test
    fun `manifest 는 rows 바이트의 sha256 을 싣고 표본 목록 해시는 그대로 옮긴다`() {
        val rows = SnapshotWriter.renderRows(listOf(rowOf("aa")))

        val manifest =
            SnapshotWriter.renderManifest(
                snapshotId = "2026-09-28T01-00-00Z",
                rowsBytes = rows,
                counts =
                    SnapshotCounts(
                        sampleSize = 4,
                        rowCount = 1,
                        sampledWithoutDetail = 2,
                        sampledWithoutNotice = 1,
                        incompleteAxis = 0,
                    ),
                period = LocalDate.of(2026, 2, 6)..LocalDate.of(2026, 9, 26),
                sampleListSha256 = "feedface",
                sampleScopeDivisions = setOf("SERVICE", "CONSTRUCTION"),
            )

        manifest shouldContain "\"schema_version\":\"snapshot-v5\""
        manifest shouldContain "\"rows_sha256\":\"${sha256Hex(rows)}\""
        // 표본 목록 해시는 **파일 바이트**의 해시다 — 이 함수는 행에서 역산하지 않고 그대로 옮긴다.
        manifest shouldContain "\"sample_list_sha256\":\"feedface\""
        manifest shouldContain "\"sample_size\":4"
        manifest shouldContain "\"sampled_without_detail\":2"
        manifest shouldContain "\"sampled_without_notice\":1"
        manifest shouldContain "\"incomplete_axis\":0"
        // D-6G-66 — 설정된 업무 집합이다(행에서 센 distinct 가 아니다). 순서는 정렬로 고정한다.
        manifest shouldContain "\"sample_scope_divisions\":[\"CONSTRUCTION\",\"SERVICE\"]"
    }

    /**
     * 계수가 행을 설명하지 못하는 manifest 는 **만들어지지 않는다**(스키마 §2 닫힌 항등식). 판독이
     * 구조 실패로 거부하기 전에 생산이 멈춰야, 어느 계수가 틀렸는지 아는 자리에서 실패한다.
     */
    @Test
    fun `표본 계수가 행을 설명하지 못하면 manifest 를 만들지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            SnapshotCounts(
                sampleSize = 4,
                rowCount = 1,
                sampledWithoutDetail = 1,
                sampledWithoutNotice = 1,
                incompleteAxis = 0,
            )
        }
    }

    @Test
    fun `자유텍스트는 원문이 아니라 존재 여부만 실린다 — 무엇이 실릴지 모르는 칸이다`() {
        val nasty = "조달청 \"시설공사\" 기준"

        val rendered = SnapshotWriter.renderRows(listOf(rowOf("aa").let { it.copy(notice = notice("aa", nasty)) }))

        rendered shouldContain "\"has_award_method_application_standard\":true"
        rendered shouldNotContain "시설공사"
    }

    /**
     * Python 판독은 이 둘을 **non-null `bool`** 로 읽는다 — `null` 이 나오면 값 결측 갈래가 아니라
     * **구조 갈래**로 가서 스냅숏 전체가 거부된다. 타입이 `Boolean`(nullable 아님)이라 오늘은 불가능
     * 하지만, 그 성질이 리팩터링을 넘어 살아 있는지를 재는 것이 이 test 다.
     */
    @Test
    fun `자유텍스트 존재 여부 두 칸은 언제나 true 또는 false 다 — null 이 나오지 않는다`() {
        val present = rowOf("aa").let { it.copy(notice = notice("aa", "조달청 기준")) }
        val absent = rowOf("bb")

        val rendered = SnapshotWriter.renderRows(listOf(present, absent))

        rendered shouldContain "\"has_award_method_application_standard\":true"
        rendered shouldContain "\"has_award_method_application_standard\":false"
        rendered shouldNotContain "\"has_award_method_application_standard\":null"
        rendered shouldNotContain "\"has_application_basis_content\":null"
        // 두 칸이 **모든** 행에 있다(빠지면 미지 키가 아니라 결측 키로 거부된다).
        rendered.trimEnd('\n').lines().forEach { it shouldContain "has_application_basis_content" }
    }

    @Test
    fun `공고일·개찰일이 없으면 null 로 나간다 — 대체값을 지어내지 않는다`() {
        val blank =
            rowOf("aa").let {
                it.copy(
                    notice = it.notice.copy(noticedOn = null),
                    outcome = it.outcome.copy(openedOn = null, plannedPrice = null),
                )
            }

        val rendered = SnapshotWriter.renderRows(listOf(blank))

        rendered shouldContain "\"noticed_on\":null"
        rendered shouldContain "\"opened_on\":null"
        rendered shouldContain "\"planned_price\":null"
    }

    @Test
    fun `진행구분은 개찰 쪽에 실린다 — 투찰 시점 타입에 개찰 출처 칸이 없다`() {
        val rendered = SnapshotWriter.renderRows(listOf(rowOf("aa")))

        rendered.substringAfter("\"outcome\"") shouldContain "\"progress_division\":\"개찰완료\""
        rendered.substringBefore("\"outcome\"") shouldNotContain "progress_division"
    }

    @Test
    fun `투찰자 행에 상호·사업자번호 칸이 없다 — 타입에 자리가 없다`() {
        val rendered = SnapshotWriter.renderRows(listOf(rowOf("aa")))

        rendered shouldNotContain "prcbdr"
        rendered shouldNotContain "bizno"
        rendered.substringAfter("\"bidder_rows\":") shouldContain "\"ordinal\":1"
    }
}
