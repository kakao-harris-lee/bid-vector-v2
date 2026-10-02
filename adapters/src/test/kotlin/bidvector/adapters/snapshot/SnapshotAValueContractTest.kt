package bidvector.adapters.snapshot

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/**
 * **D-6G2d-21·28·34 · D-6G2e-4·15 — A 묶음은 전부 아니면 무이고, 낼 수 없는 A 는 계수로 공시한다.**
 *
 * 소수 금액 축(`SnapshotAmountContractTest`)과 재는 것이 다르다: 저쪽은 「원천이 소수를 냈다」이고
 * 이쪽은 **「원문이 반쪽이다」**다. 두 계수(`fractionalAmounts`·`incompleteAValues`)를 한 수로 접지
 * 않는 이유와 같은 경계이고, 파일 500 줄 한도가 그 자리에서 갈라졌다.
 *
 * 줄어든 A 는 오류도 결측도 아닌 **틀린 값**이다 — 공사 하한가가 `(예정가격 − A) × r + A` 라
 * A 가 작으면 하한가를 낮게 잡고 적격 판정 자체가 틀린다. 그래서 이 축의 판들이 전부 「통째로
 * 비운다」를 단언한다.
 */
class SnapshotAValueContractTest : SnapshotAssemblyFixture() {
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
     * **D-6G2e-15 (vr r1 H-1 = cr r1 M-1·M-2) — 「A 가 적용되는가」의 정본은 기초금액 축 술어다.**
     *
     * D-6G2e-4 는 계수를 **A 행 입력 유무**로만 갈랐고, 그 바꿈이 다른 모양에서 같은 과소를 만들었다:
     * 기초금액 축이 `Y` 라고 말하는데 A 행이 통째로 비었거나 금액이 읽히지 않는 공고(원천이 반쪽을
     * 보낸 **가장 흔한** 모양)가 1 에서 0 으로 줄었고, 품질관리비 금액만 실린 행도 그 술어가 없으면
     * 합산 목록에 들어가지 않아 「입력 없음」으로 보였다.
     *
     * 정본은 **기초금액 축 술어**다. 그 축이 걷히지 않았거나 술어를 모를 때만 A 행이 싣는 입력으로
     * 가른다(그것이 D-4 가 고친 자리다) — 두 처방이 같은 함수에서 각자의 자리를 갖는다.
     *
     * 판을 **한 표로** 둔다: 사유별로 test 를 쪼개면 어느 한 모양을 빠뜨린 변이가 나머지 판에서
     * 초록으로 지나간다(이 파일의 「계수 여덟」 판과 같은 이유).
     */
    @Test
    fun `A 결손 계수는 기초금액 축 술어가 정본이고 그 축이 없을 때만 A 행이 가른다`() {
        val costOnly = mapOf(QUALITY_COST_KEY to "500000")
        val unreadable = A_COMPONENT_KEYS.associateWith { "금액-아님" }
        listOf(
            Board("기초 Y + A 행 빔", emptyMap(), applicable = true, collected = true, expected = 1),
            Board("기초 Y + A 행 해석 불가", unreadable, applicable = true, collected = true, expected = 1),
            Board("품질관리비만 + 술어 부재", costOnly, applicable = true, collected = true, expected = 1),
            Board(
                "품질관리비만 + 술어 빈 값",
                costOnly + mapOf(QUALITY_PREDICATE_KEY to ""),
                applicable = true,
                collected = true,
                expected = 1,
            ),
            Board("기초 축 부재 + A 행 빔", emptyMap(), applicable = true, collected = false, expected = 0),
            Board("기초 N + 품질관리비만", costOnly, applicable = false, collected = true, expected = 0),
            // 계약의 여섯에 하나 더한다 — 「품질관리비 금액은 그 술어와 무관하게 입력」이 기초금액 축이
            // 없을 때 실제로 쓰이는 자리다. 이 판이 없으면 그 절의 변이가 초록으로 지나간다.
            Board("기초 축 부재 + 품질관리비만", costOnly, applicable = true, collected = false, expected = 1),
        ).forEach { board ->
            val rendered =
                render(
                    formulaAFields = board.formulaAFields,
                    reserveFields = intactReserveRows(),
                    formulaAApplicable = board.applicable,
                    baseAmountAxisCollected = board.collected,
                )

            withClue(board.name) {
                rendered.incompleteAValues shouldBe board.expected
                // 값은 일곱 판 모두 같다 — 갈리는 것은 **공시 계수**뿐이다.
                rendered.bytes shouldContain "\"a_value\":null"
            }
        }
    }

    /** D-6G2e-15 의 판 하나 — 이름과 기대값을 함께 들어 변이가 어느 모양을 빠뜨렸는지 말하게 한다. */
    private class Board(
        val name: String,
        val formulaAFields: Map<String, String>,
        val applicable: Boolean,
        val collected: Boolean,
        val expected: Int,
    )

    /** 반대 방향 — A 입력이 있는데 합산을 낼 수 없는 행은 **센다**(그것이 결측이다). */
    @Test
    fun `A 입력이 있는데 합산을 낼 수 없으면 센다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA() - "bidPrceCalclAOpenDt",
                reserveFields = intactReserveRows(),
            )

        rendered.incompleteAValues shouldBe 1
        rendered.bytes shouldContain "\"a_value\":null"
    }

    /**
     * **D-6G2e-4 (★② — 6G-2d cr 4차 ③) — 기초금액 축이 걷히지 않아도 A 결손은 세어진다.** 앞 판은
     * 계수를 그 축 행의 `bidPrceCalclAYn` 에 묶어, 축이 비면 술어가 「모름」이고 모름이 「미적용」으로
     * 접혀 **계수만 조용히 과소**했다(값은 맞다). 실수집에서 기초금액 축이 상한·일시 실패로 미완인
     * 공고는 드물지 않고, 그 공고들의 A 결손이 판정 보고에서 사라지면 공시가 사실이 아니게 된다.
     */
    @Test
    fun `기초금액 축이 걷히지 않은 공고의 A 결손도 센다`() {
        val rendered =
            render(
                formulaAFields = intactFormulaA() - A_COMPONENT_KEYS.last(),
                reserveFields = intactReserveRows(),
                baseAmountAxisCollected = false,
            )

        rendered.incompleteAValues shouldBe 1
        rendered.bytes shouldContain "\"a_value\":null"
        // 기초금액 축이 없으므로 그 축의 칸들은 비어 있다 — 계수만 달라지고 값은 그대로다.
        rendered.bytes shouldContain "\"bid_price_formula_a_applicable\":null"
    }
}
