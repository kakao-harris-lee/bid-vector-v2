package bidvector.procurement

import bidvector.sharedkernel.export
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M7/7C⑤-0 — **걷기 선택과 멱등**. 걷기 권위는 시도 원장이고(D-6G-68·80), 쓸 걷기가 없으면 행을
 * 만들지 않는다. 멱등 술어는 upsert 의 `WHERE` 와 같은 축이다(D-7C5P-4·8).
 */
class OpeningPromotionWalkTest {
    // =========================================================================
    // 걷기 선택 — 원장이 정한다(D-6G-68)
    // =========================================================================
    @Test
    fun `원장 결말이 가리킨 걷기의 행만 쓴다`() {
        val group =
            groupOf(
                axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")),
                axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_B, mapOf("plnprc" to "200")),
            )

        val older = promotedResult(group, mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to succeededAt(WALK_A)))
        val newer = promotedResult(group, mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to succeededAt(WALK_B)))

        older.plannedPrice?.export()?.won shouldBe 100L
        newer.plannedPrice?.export()?.won shouldBe 200L
    }

    @Test
    fun `두 걷기의 자식 행은 합쳐지지 않는다`() {
        val group =
            groupOf(
                axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("compnoRsrvtnPrceSno" to "1")),
                axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("compnoRsrvtnPrceSno" to "2")),
                axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_B, mapOf("compnoRsrvtnPrceSno" to "9")),
            )

        val result = promotedResult(group, mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to succeededAt(WALK_B)))

        result.reservePrices.map { it.sequenceNumber } shouldContainExactly listOf("9")
    }

    @Test
    fun `원장 줄이 없는 축은 가장 늦은 관측으로 물러선다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "3")),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_B, mapOf("prtcptCnum" to "9")),
                ),
            )

        result.participantCount shouldBe 9
    }

    @Test
    fun `빈 응답으로 끝난 축은 0 행이다`() {
        val group = groupOf(axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")))

        val outcome =
            promote(
                group,
                mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to AxisConclusion(AttemptOutcome.Empty, WALK_A)),
            )

        outcome shouldBe OpeningPromotionOutcome.NoUsableAxis
    }

    @Test
    fun `실패로 끝난 축의 행은 쓰지 않는다`() {
        val group = groupOf(axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")))

        val outcome =
            promote(
                group,
                mapOf(
                    SourceEndpoint.RESERVE_PRICE_DETAIL to
                        AxisConclusion(AttemptOutcome.Failed("SHORT_WALK"), WALK_A),
                ),
            )

        outcome shouldBe OpeningPromotionOutcome.NoUsableAxis
    }

    @Test
    fun `미완 축이 있어도 다른 축은 승격된다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "4")),
                ),
                mapOf(
                    SourceEndpoint.RESERVE_PRICE_DETAIL to
                        AxisConclusion(AttemptOutcome.Failed("SHORT_WALK"), WALK_A),
                ),
            )

        result.plannedPrice.shouldBeNull()
        result.participantCount shouldBe 4
    }

    @Test
    fun `결말 없이 끊긴 축의 행은 승격하지 않는다`() {
        // verifier r1 F1 — 적재와 결말 사이에서 죽은 걷기다. 추출이 같은 원문을 incomplete_axis 로
        // 빼는 것과 같은 판정이고, 승격이 그보다 느슨할 이유가 없다.
        val group = groupOf(axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")))

        promote(group, interruptedAxes = setOf(SourceEndpoint.RESERVE_PRICE_DETAIL)) shouldBe
            OpeningPromotionOutcome.NoUsableAxis
    }

    @Test
    fun `끊긴 축이 있어도 온전한 다른 축은 승격된다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "4")),
                ),
                interruptedAxes = setOf(SourceEndpoint.RESERVE_PRICE_DETAIL),
            )

        result.plannedPrice.shouldBeNull()
        result.participantCount shouldBe 4
    }

    @Test
    fun `원장이 가리킨 걷기의 행이 읽기 집합에 없으면 던지지 않는다`() {
        // verifier r1 F2 — 그 걷기의 행이 보존 창 밖이거나 마스킹돼 빠진 경우다. 빈 축을 남기면
        // 부모 관측 시각을 고르는 maxOf 가 던져 공고 하나가 실행 전체를 멈춘다.
        val group = groupOf(axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_B, mapOf("plnprc" to "100")))

        promote(group, mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to succeededAt(WALK_A))) shouldBe
            OpeningPromotionOutcome.NoUsableAxis
    }

    @Test
    fun `걷기 선택은 네 갈래다`() {
        axisWalkOf(null, axisInterrupted = false, listOf(WALK_A, WALK_B)) shouldBe AxisWalk.Use(WALK_B)
        axisWalkOf(null, axisInterrupted = false, emptyList()) shouldBe AxisWalk.NoRows
        // 결말이 없는데 **열린 라운드가 있다** — 끊긴 걷기다(verifier r1 F1). 물러서지 않는다.
        axisWalkOf(null, axisInterrupted = true, listOf(WALK_A, WALK_B)) shouldBe AxisWalk.Unusable
        val bothWalks = listOf(WALK_A, WALK_B)
        val emptyConclusion = AxisConclusion(AttemptOutcome.Empty, WALK_B)
        val failedConclusion = AxisConclusion(AttemptOutcome.Failed("X"), WALK_B)

        axisWalkOf(emptyConclusion, axisInterrupted = false, listOf(WALK_A)) shouldBe AxisWalk.NoRows
        axisWalkOf(failedConclusion, axisInterrupted = false, listOf(WALK_B)) shouldBe AxisWalk.Unusable
        axisWalkOf(succeededAt(WALK_A), axisInterrupted = false, bothWalks) shouldBe AxisWalk.Use(WALK_A)
        // 결말이 **있으면** 열린 라운드(재걷기 진행)는 판정을 바꾸지 않는다 — 그 결말의 행은 온전하다.
        axisWalkOf(succeededAt(WALK_A), axisInterrupted = true, bothWalks) shouldBe AxisWalk.Use(WALK_A)
    }
}
