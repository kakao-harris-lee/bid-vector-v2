package bidvector.adapters.snapshot

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

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
class SnapshotAmountContractTest : SnapshotAssemblyFixture() {
    @Test
    fun `온전한 금액은 전부 정수 리터럴로 실린다 — 양성 대조`() {
        val rendered =
            render(
                baseAmountFields = intactBaseAmount(),
                formulaAFields = intactFormulaA(),
                reserveFields = intactReserveRows(),
                openingFields = listOf(mapOf("opengRank" to "1", "bidprcAmt" to "1100000000")),
            )

        rendered.fractional shouldBe 0
        rendered.bytes shouldContain "\"a_value\":{\"total\":6000000,"
        rendered.bytes shouldContain "\"reserve_prices\":[$RESERVE_PRICE_AMOUNT,"
        rendered.bytes shouldContain "\"base_amount\":1234567890"
        rendered.bytes shouldContain "\"pure_construction_cost\":900000000"
        rendered.bytes shouldContain "\"planned_price\":$PLANNED_PRICE"
        rendered.bytes shouldContain "\"opening_base_amount\":$OPENING_BASE_AMOUNT"
        rendered.bytes shouldContain "\"amount\":1100000000"
    }

    /**
     * 스칼라 넷은 `int | null` 이므로 그 칸만 비운다 — 기초금액·순공사원가·예정가격·개찰 기초금액.
     * 행은 사라지지 않고 다른 칸은 그대로다. **투찰금액은 이 무리가 아니다**(D-6G2d-43): 그 칸을
     * 비우면 순번이 밀리므로 목록 전체가 사라진다(아래 판).
     */
    @Test
    fun `스칼라 금액 칸의 소수부는 그 칸만 비운다`() {
        val rendered =
            render(
                baseAmountFields =
                    intactBaseAmount(mapOf("bssamt" to "1234567890.01", "bssAmtPurcnstcst" to "900000000.5")),
                formulaAFields = intactFormulaA(),
                reserveFields =
                    intactReserveRows(
                        scalars = mapOf("plnprc" to "$PLANNED_PRICE.25", "bssamt" to "$OPENING_BASE_AMOUNT.5"),
                    ),
                openingFields = listOf(mapOf("opengRank" to "1", "bidprcAmt" to "1100000000")),
            )

        rendered.fractional shouldBe 4
        rendered.bytes shouldContain "\"base_amount\":null"
        rendered.bytes shouldContain "\"pure_construction_cost\":null"
        rendered.bytes shouldContain "\"planned_price\":null"
        rendered.bytes shouldContain "\"opening_base_amount\":null"
        // 투찰자는 온전하므로 그대로 실린다.
        rendered.bytes shouldContain "\"amount\":1100000000"
        // 집계 둘은 온전하므로 형태를 지킨다.
        rendered.bytes shouldContain "\"a_value\":{\"total\":6000000,"
        rendered.bytes shouldContain "\"reserve_prices\":[$RESERVE_PRICE_AMOUNT,"
    }

    /**
     * **D-6G2d-43 (cr r5 ④) — 투찰자 한 명의 소수 금액은 목록 전체를 비운다.** 그 한 명만 `null` 로
     * 두면 순번이 밀린다: 순번은 금액 오름차순에 `null` 을 뒤로 두어 매기므로(D-6G-2 §1.3) 비운 한 명이
     * 맨 뒤로 가고 그 뒤 순위가 한 칸씩 당겨진다 — 1위 투찰가와 참가자 구성이 조용히 달라지고 판독은
     * 사유까지 틀린다. 집계 둘과 같은 규율로 통째로 비우고 **한 번** 센다.
     */
    @Test
    fun `투찰자 금액의 소수부는 투찰자 목록 전체를 비운다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA(),
                reserveFields = intactReserveRows(),
                openingFields =
                    listOf(
                        mapOf("opengRank" to "1", "bidprcAmt" to "1100000000"),
                        mapOf("opengRank" to "2", "bidprcAmt" to "1200000000.5"),
                        mapOf("opengRank" to "3", "bidprcAmt" to "1300000000"),
                    ),
            )

        rendered.fractional shouldBe 1
        rendered.bytes shouldContain "\"bidder_rows\":[]"
        // 온전한 두 명도 남지 않는다 — 남기면 그 둘의 순번이 실제 순위가 아니다.
        rendered.bytes shouldNotContain "\"amount\":1100000000"
    }

    /** 반대 방향 — 금액이 **없는** 투찰자는 목록을 비우지 않는다(원천의 정직한 결측, 순번은 뒤로). */
    @Test
    fun `금액 없는 투찰자는 목록을 비우지 않는다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA(),
                reserveFields = intactReserveRows(),
                openingFields =
                    listOf(mapOf("opengRank" to "1", "bidprcAmt" to "1100000000"), mapOf("opengRank" to "2")),
            )

        rendered.fractional shouldBe 0
        rendered.bytes shouldContain "\"amount\":1100000000"
        rendered.bytes shouldContain "\"amount\":null"
    }

    /**
     * **A 는 통째로 비운다.** 구성 항목 하나가 소수부를 가지면 `total` 을 `null` 로 둘 수 없다 —
     * 스키마가 `{total:int, …}` 를 요구하므로 그 바이트는 형태 위반이고 스냅숏 전체가 거부된다.
     * 항목을 **빼고 나머지를 더하지도 않는다**: 그러면 A 가 조용히 과소 합산된다.
     */
    @Test
    fun `A 구성 항목의 소수부는 A 묶음 전체를 비운다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA(mapOf("sftyChckMngcst" to "1000000.5")),
                reserveFields = intactReserveRows(),
            )

        rendered.fractional shouldBe 1
        rendered.bytes shouldContain "\"a_value\":null"
        rendered.bytes.contains("\"total\":null") shouldBe false
        // 과소 합산이 아니다 — 남은 다섯의 합(5,000,000)이 실리지 않는다.
        rendered.bytes.contains("\"total\":5000000") shouldBe false
    }

    /**
     * **예비가격 배열도 통째로 비운다.** 위치가 순번이라는 규약 위에서 원소 하나를 `null` 로 두면
     * 그 배열은 `int[15]` 가 아니고, 판독은 원소 자리에서 형태 위반을 낸다.
     */
    @Test
    fun `예비가격 한 칸의 소수부는 배열 전체를 비운다`() {
        val rendered = render(formulaAFields = intactFormulaA(), reserveFields = intactReserveRows(7))

        rendered.fractional shouldBe 1
        rendered.bytes shouldContain "\"reserve_prices\":null"
        rendered.bytes.contains("\"reserve_prices\":[") shouldBe false
    }

    /** 끝자리 0 은 소수부가 아니다 — 원천 표기가 `.00` 이어도 정수 리터럴로 실린다. */
    @Test
    fun `끝자리 0 은 소수부가 아니다`() {
        val rendered =
            render(
                baseAmountFields = intactBaseAmount(mapOf("bssamt" to "1234567890.00")),
                formulaAFields = intactFormulaA(A_COMPONENT_KEYS.associateWith { "$A_COMPONENT_AMOUNT.000" }),
                reserveFields = intactReserveRows(),
            )

        rendered.fractional shouldBe 0
        rendered.bytes shouldContain "\"base_amount\":1234567890"
        rendered.bytes shouldContain "\"a_value\":{\"total\":6000000,"
    }
}
