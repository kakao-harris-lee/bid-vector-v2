package bidvector.adapters.snapshot

import bidvector.procurement.BusinessDivision
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeNumber
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

private fun policy(): KonepsCollectionPolicyData =
    (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 7)) as Resolution.Resolved).value

private val KEY = NoticeKey(NoticeNumber.of("20260617001-00").value, NoticeRound.of("000"))

private val CANONICAL =
    CanonicalNotice(
        division = BusinessDivision.CONSTRUCTION.name,
        bidCloseAt = Instant.parse("2026-06-16T05:00:00Z"),
        floorRate = null,
    )

/** A 합산액의 구성 항목 — 술어가 참인 품질관리비까지 일곱이다(스키마 §3.3). */
private val A_COMPONENT_KEYS =
    listOf("npnInsrprm", "mrfnHealthInsrprm", "odsnLngtrmrcprInsrprm", "rtrfundNon", "sftyMngcst", "sftyChckMngcst")

private const val A_COMPONENT_AMOUNT = "1000000"

private const val RESERVE_PRICE_AMOUNT = "1200000000"

private const val PLANNED_PRICE = "1250000000"

private const val OPENING_BASE_AMOUNT = "1239999999"

/** 기초금액은 **마감 전에 공개된** 값만 싣는다(D-6G-19) — 공개일시가 없으면 그 칸은 읽히지도 않는다. */
private const val BASE_AMOUNT_DISCLOSED_AT = "2026-06-10 09:00:00"

/**
 * **D-6G2d-15 (vr r1 H-1 · cr r1 H-2) — 렌더된 금액 칸은 스키마 §2.2 형태 집합 안이다.**
 *
 * 금액 칸 일곱 중 다섯은 `int | null` 이라 `null` 이 합법이고 기존의 이름 있는 행 단위 제외로
 * 떨어진다. 나머지 둘은 다르다: `a_value` 는 `{total:int, …} | null` 이고 `reserve_prices` 는
 * `int[15] | null` 이라 **원소 자리의 `null` 이 형태 위반**이고, 판독은 그 행이 아니라 스냅숏
 * **전체**를 거부한다(`INVALID_VALUE`). 그래서 소수부가 집계에 들어가면 집계를 통째로 비운다.
 *
 * 조립을 직접 부른다 — 이 test 가 재는 것은 「원문의 소수 금액이 어떤 바이트가 되는가」이고, 그
 * 판정은 조립이 한다(렌더는 이미 정수만 받는다). 실 Postgres 가 필요 없는 자리다.
 */
class SnapshotAmountContractTest {
    private fun render(
        baseAmountFields: Map<String, String> = emptyMap(),
        formulaAFields: Map<String, String> = emptyMap(),
        reserveFields: List<Map<String, String>> = emptyList(),
        openingFields: List<Map<String, String>> = emptyList(),
    ): Pair<String, Int> {
        val tally = AssemblyTally()
        val axes =
            buildMap<SourceEndpoint, List<RawRow>> {
                put(SourceEndpoint.BASE_AMOUNT_DETAIL, listOf(rawRow(baseAmountFields)))
                put(SourceEndpoint.BID_PRICE_FORMULA_A, listOf(rawRow(formulaAFields)))
                put(SourceEndpoint.RESERVE_PRICE_DETAIL, reserveFields.map(::rawRow))
                put(SourceEndpoint.OPENING_COMPLETE, openingFields.map(::rawRow))
            }
        val row = assembleSnapshotRow(KEY, axes, CANONICAL, tally)
        return SnapshotWriter.renderRows(listOf(row)) to tally.fractionalAmounts
    }

    private fun rawRow(fields: Map<String, String>): RawRow = RawRow(fields.mapValues { it.value }, policy())

    /** 온전한 A 묶음 — 구성 항목 여섯과 공개일시. */
    private fun intactFormulaA(overrides: Map<String, String> = emptyMap()): Map<String, String> =
        A_COMPONENT_KEYS.associateWith { A_COMPONENT_AMOUNT } +
            mapOf("bidPrceCalclAOpenDt" to "2026-06-10 09:00:00") + overrides

    /** 기초금액 축의 온전한 행 — 공개일시가 마감보다 앞이라 기초금액이 실린다. */
    private fun intactBaseAmount(overrides: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "bssamt" to "1234567890",
            "bssAmtPurcnstcst" to "900000000",
            "bssamtOpenDt" to BASE_AMOUNT_DISCLOSED_AT,
        ) + overrides

    /**
     * 온전한 예비가격 15행 — 위치가 순번이라는 규약을 지킨다. 예정가격과 개찰 기초금액도 이 축에서
     * 오므로 첫 행이 함께 나른다(스키마 §3.4).
     */
    private fun intactReserveRows(
        fractionalAt: Int? = null,
        scalars: Map<String, String> = mapOf("plnprc" to PLANNED_PRICE, "bssamt" to OPENING_BASE_AMOUNT),
    ): List<Map<String, String>> =
        (1..RESERVE_PRICE_SLOTS).map { sequence ->
            mapOf(
                "compnoRsrvtnPrceSno" to sequence.toString(),
                "bsisPlnprc" to if (sequence == fractionalAt) "$RESERVE_PRICE_AMOUNT.5" else RESERVE_PRICE_AMOUNT,
            ) + if (sequence == 1) scalars else emptyMap()
        }

    @Test
    fun `온전한 금액은 전부 정수 리터럴로 실린다 — 양성 대조`() {
        val (rendered, fractional) =
            render(
                baseAmountFields = intactBaseAmount(),
                formulaAFields = intactFormulaA(),
                reserveFields = intactReserveRows(),
                openingFields = listOf(mapOf("opengRank" to "1", "bidprcAmt" to "1100000000")),
            )

        fractional shouldBe 0
        rendered shouldContain "\"a_value\":{\"total\":6000000,"
        rendered shouldContain "\"reserve_prices\":[$RESERVE_PRICE_AMOUNT,"
        rendered shouldContain "\"base_amount\":1234567890"
        rendered shouldContain "\"pure_construction_cost\":900000000"
        rendered shouldContain "\"planned_price\":$PLANNED_PRICE"
        rendered shouldContain "\"opening_base_amount\":$OPENING_BASE_AMOUNT"
        rendered shouldContain "\"amount\":1100000000"
    }

    /**
     * 스칼라 다섯은 `int | null` 이므로 그 칸만 비운다 — 기초금액·순공사원가·예정가격·개찰 기초금액·
     * 투찰금액. 행은 사라지지 않고 다른 칸은 그대로다.
     */
    @Test
    fun `스칼라 금액 칸의 소수부는 그 칸만 비운다`() {
        val (rendered, fractional) =
            render(
                baseAmountFields =
                    intactBaseAmount(mapOf("bssamt" to "1234567890.01", "bssAmtPurcnstcst" to "900000000.5")),
                formulaAFields = intactFormulaA(),
                reserveFields =
                    intactReserveRows(
                        scalars = mapOf("plnprc" to "$PLANNED_PRICE.25", "bssamt" to "$OPENING_BASE_AMOUNT.5"),
                    ),
                openingFields = listOf(mapOf("opengRank" to "1", "bidprcAmt" to "1100000000.5")),
            )

        fractional shouldBe 5
        rendered shouldContain "\"base_amount\":null"
        rendered shouldContain "\"pure_construction_cost\":null"
        rendered shouldContain "\"planned_price\":null"
        rendered shouldContain "\"opening_base_amount\":null"
        rendered shouldContain "\"amount\":null"
        // 집계 둘은 온전하므로 형태를 지킨다.
        rendered shouldContain "\"a_value\":{\"total\":6000000,"
        rendered shouldContain "\"reserve_prices\":[$RESERVE_PRICE_AMOUNT,"
    }

    /**
     * **A 는 통째로 비운다.** 구성 항목 하나가 소수부를 가지면 `total` 을 `null` 로 둘 수 없다 —
     * 스키마가 `{total:int, …}` 를 요구하므로 그 바이트는 형태 위반이고 스냅숏 전체가 거부된다.
     * 항목을 **빼고 나머지를 더하지도 않는다**: 그러면 A 가 조용히 과소 합산된다.
     */
    @Test
    fun `A 구성 항목의 소수부는 A 묶음 전체를 비운다`() {
        val (rendered, fractional) =
            render(
                formulaAFields = intactFormulaA(mapOf("sftyChckMngcst" to "1000000.5")),
                reserveFields = intactReserveRows(),
            )

        fractional shouldBe 1
        rendered shouldContain "\"a_value\":null"
        rendered.contains("\"total\":null") shouldBe false
        // 과소 합산이 아니다 — 남은 다섯의 합(5,000,000)이 실리지 않는다.
        rendered.contains("\"total\":5000000") shouldBe false
    }

    /**
     * **예비가격 배열도 통째로 비운다.** 위치가 순번이라는 규약 위에서 원소 하나를 `null` 로 두면
     * 그 배열은 `int[15]` 가 아니고, 판독은 원소 자리에서 형태 위반을 낸다.
     */
    @Test
    fun `예비가격 한 칸의 소수부는 배열 전체를 비운다`() {
        val (rendered, fractional) = render(formulaAFields = intactFormulaA(), reserveFields = intactReserveRows(7))

        fractional shouldBe 1
        rendered shouldContain "\"reserve_prices\":null"
        rendered.contains("\"reserve_prices\":[") shouldBe false
    }

    /** 끝자리 0 은 소수부가 아니다 — 원천 표기가 `.00` 이어도 정수 리터럴로 실린다. */
    @Test
    fun `끝자리 0 은 소수부가 아니다`() {
        val (rendered, fractional) =
            render(
                baseAmountFields = intactBaseAmount(mapOf("bssamt" to "1234567890.00")),
                formulaAFields = intactFormulaA(A_COMPONENT_KEYS.associateWith { "$A_COMPONENT_AMOUNT.000" }),
                reserveFields = intactReserveRows(),
            )

        fractional shouldBe 0
        rendered shouldContain "\"base_amount\":1234567890"
        rendered shouldContain "\"a_value\":{\"total\":6000000,"
    }
}
