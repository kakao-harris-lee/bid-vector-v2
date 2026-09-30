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
import io.kotest.matchers.string.shouldNotContain
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

/** 품질관리비의 합산 대상 술어와 그 금액 — 이 둘의 짝이 D-6G2d-28 의 자리다. */
private const val QUALITY_PREDICATE_KEY = "qltyMngcstAObjYn"

/** A 적용 술어의 원문 키 — 기초금액 축이 나른다(공사 전용). */
private const val A_APPLICABLE_KEY = "bidPrceCalclAYn"

private const val QUALITY_COST_KEY = "qltyMngcst"

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
        /**
         * A 적용 여부(D-6G2d-48 ④) — `incompleteAValues` 는 **적용되는 공고에서만** 센다. 기본이 참인
         * 이유: 이 test 셋이 재는 것은 A 값의 규율이고, 그 판들은 A 가 적용되는 공고다.
         */
        formulaAApplicable: Boolean = true,
    ): Rendered {
        val tally = AssemblyTally()
        val baseAmount = baseAmountFields + mapOf(A_APPLICABLE_KEY to if (formulaAApplicable) "Y" else "N")
        val axes =
            buildMap<SourceEndpoint, List<RawRow>> {
                put(SourceEndpoint.BASE_AMOUNT_DETAIL, listOf(rawRow(baseAmount)))
                put(SourceEndpoint.BID_PRICE_FORMULA_A, listOf(rawRow(formulaAFields)))
                put(SourceEndpoint.RESERVE_PRICE_DETAIL, reserveFields.map(::rawRow))
                put(SourceEndpoint.OPENING_COMPLETE, openingFields.map(::rawRow))
            }
        val row = assembleSnapshotRow(KEY, axes, CANONICAL, tally)
        return Rendered(SnapshotWriter.renderRows(listOf(row)), tally.fractionalAmounts, tally.incompleteAValues)
    }

    /** 렌더 결과와 그때의 계수 둘 — 원인이 다른 계수를 한 수로 접지 않는다. */
    private class Rendered(
        val bytes: String,
        val fractional: Int,
        val incompleteAValues: Int,
    )

    private fun rawRow(fields: Map<String, String>): RawRow = RawRow(fields.mapValues { it.value }, policy())

    /**
     * 온전한 A 묶음 — 구성 항목 여섯과 공개일시, 그리고 **품질관리비 술어**다. 술어가 `Y`/`N` 밖이면
     * A 를 낼 수 없으므로(D-6G2d-28) 온전한 판은 그 술어를 명시한다: `N` 이면 그 항목은 합산 대상이
     * 아니고, 대상이 아닌 것의 부재는 결측이 아니다.
     */
    private fun intactFormulaA(overrides: Map<String, String> = emptyMap()): Map<String, String> =
        A_COMPONENT_KEYS.associateWith { A_COMPONENT_AMOUNT } +
            mapOf("bidPrceCalclAOpenDt" to "2026-06-10 09:00:00", QUALITY_PREDICATE_KEY to "N") + overrides

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

    /**
     * **D-6G2d-21 ⓐ — 공개일시가 없는 A 는 없는 A 다.** 스키마 §2.2 는 `open_at` 에 널을 허용하지
     * 않으므로 합산액만 싣고 공개일시를 비우면 그 바이트는 `{total:int, open_at:datetime, …} | null`
     * 어느 쪽도 아니고 판독이 스냅숏 **전체**를 거부한다. 공개일시는 누출 판정의 입력이라(D-6G-13 ⑥)
     * 없는 A 는 채점에 쓸 수도 없다.
     */
    @Test
    fun `A 공개일시가 없으면 A 묶음 전체가 없다`() {
        val rendered =
            render(
                formulaAFields = A_COMPONENT_KEYS.associateWith { A_COMPONENT_AMOUNT },
                reserveFields = intactReserveRows(),
            )

        rendered.incompleteAValues shouldBe 1
        rendered.bytes shouldContain "\"a_value\":null"
        rendered.bytes.contains("\"open_at\":null") shouldBe false
    }

    /**
     * **D-6G2d-21 ⓑ — 구성 항목이 하나라도 결측이면 A 묶음 전체가 없다.** 앞 판은 결측 항목을 빼고
     * 나머지를 더해 A 를 **조용히 줄였다**(6G 부터의 부채). 줄어든 A 는 오류도 결측도 아닌 **틀린 값**
     * 이라 채점에 그대로 들어간다 — 공사 하한가가 `(예정가격 − A) × r + A` 라 A 가 작으면 하한가를
     * 낮게 잡고 적격 판정 자체가 틀린다. 결측보다 나쁘다.
     */
    @Test
    fun `A 구성 항목이 하나라도 결측이면 A 묶음 전체가 없다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA() - A_COMPONENT_KEYS.last(),
                reserveFields = intactReserveRows(),
            )

        rendered.incompleteAValues shouldBe 1
        rendered.bytes shouldContain "\"a_value\":null"
        // 과소 합산이 아니다 — 남은 다섯의 합이 실리지 않는다.
        rendered.bytes.contains("\"total\":5000000") shouldBe false
    }

    /**
     * **D-6G2d-28 (vr r2 H-1) — 품질관리비 술어가 `Y`/`N` 밖이면 A 묶음을 비운다.** 앞 판은 술어가
     * 참일 때만 항목을 합산 목록에 넣어, 술어가 **없거나 빈 문자열이거나 제3의 값**이면 「합산 대상
     * 아님」으로 접혔다 — A 가 그 금액만큼 작게 실리고 계수도 0 이었다(실측 6,000,000 vs 6,500,000).
     *
     * 「모름」은 「대상 아님」이 아니라 **「A 를 낼 수 없음」**이다. 필드 계약이 술어를 boolean 으로 접지
     * 않는 이유(「세 번째 값이 오면 조용히 false 가 되는 자리를 만들지 않는다」)와 같은 방향이고, 줄어든
     * A 는 오류도 결측도 아닌 **틀린 값**이라 공사 적격 판정이 그대로 틀린다.
     */
    @Test
    fun `품질관리비 술어가 Y 도 N 도 아니면 A 묶음 전체가 없다`() {
        val unknown = listOf("", "   ", "X", "1")
        val amountCases = listOf(emptyMap<String, String>(), mapOf(QUALITY_COST_KEY to "500000"))

        unknown.forEach { predicate ->
            amountCases.forEach { amount ->
                val rendered =
                    render(
                        formulaAFields = intactFormulaA(mapOf(QUALITY_PREDICATE_KEY to predicate) + amount),
                        reserveFields = intactReserveRows(),
                    )

                rendered.incompleteAValues shouldBe 1
                rendered.bytes shouldContain "\"a_value\":null"
            }
        }
    }

    /** 술어 **칸 자체가 없는** 원문도 같다 — 부재와 빈 값을 가르지 않는다(둘 다 「모름」이다). */
    @Test
    fun `품질관리비 술어 칸이 없으면 A 묶음 전체가 없다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA() - QUALITY_PREDICATE_KEY,
                reserveFields = intactReserveRows(),
            )

        rendered.incompleteAValues shouldBe 1
        rendered.bytes shouldContain "\"a_value\":null"
    }

    /** 술어가 참이면 그 금액은 **필수**다 — 대상인데 없으면 결측이고 A 묶음이 비는 쪽이다. */
    @Test
    fun `품질관리비가 대상인데 금액이 없으면 A 묶음 전체가 없다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA(mapOf(QUALITY_PREDICATE_KEY to "Y")),
                reserveFields = intactReserveRows(),
            )

        rendered.incompleteAValues shouldBe 1
        rendered.bytes shouldContain "\"a_value\":null"
    }

    /** 술어가 참이고 금액이 있으면 **합산에 든다** — 일곱 항목의 합이다. */
    @Test
    fun `품질관리비가 대상이고 금액이 있으면 합산에 든다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA(mapOf(QUALITY_PREDICATE_KEY to "Y", QUALITY_COST_KEY to "500000")),
                reserveFields = intactReserveRows(),
            )

        rendered.incompleteAValues shouldBe 0
        rendered.bytes shouldContain "\"a_value\":{\"total\":6500000,"
    }

    /** 술어가 거짓인 품질관리비는 합산 대상이 아니다 — 그 부재로 A 가 흔들리지 않는다. */
    @Test
    fun `합산 대상이 아닌 항목의 부재는 결측이 아니다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA(mapOf("qltyMngcstAObjYn" to "N")),
                reserveFields = intactReserveRows(),
            )

        rendered.incompleteAValues shouldBe 0
        rendered.bytes shouldContain "\"a_value\":{\"total\":6000000,"
    }

    /**
     * 술어가 거짓이면 **금액이 있어도** 합산에 들지 않는다(D-6G2d-34 항목 4). 앞 자리는 술어가 거짓일 때
     * 금액을 **비워** 재서, 「금액이 있으면 넣는다」로 바꾼 변이가 초록이었다 — 배제가 술어에 달렸는지
     * 값의 존재에 달렸는지를 그 판은 가르지 못한다.
     */
    @Test
    fun `술어가 거짓이면 품질관리비 금액이 있어도 합산에 들지 않는다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA(mapOf(QUALITY_PREDICATE_KEY to "N", QUALITY_COST_KEY to "500000")),
                reserveFields = intactReserveRows(),
            )

        rendered.incompleteAValues shouldBe 0
        rendered.bytes shouldContain "\"a_value\":{\"total\":6000000,"
    }

    /**
     * **D-6G2d-48 ④ — A 가 적용되지 않는 공고의 빈 A 행은 계수가 아니다.** 값은 같은 `null` 이지만
     * 뜻이 다르다: 결측이 아니라 「그 공고에 A 가 없다」다. 세면 계수가 미적용 공고 수로 부풀어
     * 백테스트 판정 보고에서 그 공시가 뜻을 잃는다.
     */
    @Test
    fun `A 가 적용되지 않는 공고의 빈 A 행은 세지 않는다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA() - "bidPrceCalclAOpenDt",
                reserveFields = intactReserveRows(),
                formulaAApplicable = false,
            )

        rendered.incompleteAValues shouldBe 0
        rendered.bytes shouldContain "\"a_value\":null"
    }

    /** 반대 방향 — 적용되는 공고의 같은 빈 행은 **센다**(그것이 결측이다). */
    @Test
    fun `A 가 적용되는 공고의 빈 A 행은 센다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA() - "bidPrceCalclAOpenDt",
                reserveFields = intactReserveRows(),
                formulaAApplicable = true,
            )

        rendered.incompleteAValues shouldBe 1
        rendered.bytes shouldContain "\"a_value\":null"
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
