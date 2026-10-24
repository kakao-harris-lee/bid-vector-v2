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
    fun `원장 줄이 없는 목록 축은 가장 늦은 관측으로 물러선다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "3")),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_B, mapOf("prtcptCnum" to "9")),
                ),
                conclusions = emptyMap(),
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
    fun `결말 없는 상세 축의 행은 승격하지 않는다 — 원장 줄이 없어도 마찬가지다`() {
        // verifier r2 R2-1(VP3 모양). 앞 판은 「열린 라운드가 있는 축만」 비웠고, 줄이 **하나도 없는**
        // 상세 축은 행 시각으로 물러서서 승격했다 — 원장 이전 원문·다른 실행 상태 디렉터리의 원문이
        // 그 갈래다. 지금은 축으로 가르므로 결말이 없으면 상세 축은 둘 다 쓰지 않는다.
        val group = groupOf(axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")))

        promote(group, conclusions = emptyMap()) shouldBe OpeningPromotionOutcome.NoUsableAxis
    }

    @Test
    fun `결말 없는 상세 축이 있어도 목록 축은 물러서서 승격된다`() {
        val result =
            promotedResult(
                groupOf(
                    axisRow(SourceEndpoint.RESERVE_PRICE_DETAIL, WALK_A, mapOf("plnprc" to "100")),
                    axisRow(SourceEndpoint.OPENING_RESULT_LIST, WALK_A, mapOf("prtcptCnum" to "4")),
                ),
                conclusions = emptyMap(),
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
    fun `걷기 선택은 축과 결말로 갈린다`() {
        val listAxis = SourceEndpoint.OPENING_RESULT_LIST
        val detailAxis = SourceEndpoint.RESERVE_PRICE_DETAIL

        // 결말이 없다 — **목록 축만** 물러선다(D-7C5P-11).
        axisWalkOf(listAxis, null, listOf(WALK_A, WALK_B)) shouldBe AxisWalk.Use(WALK_B)
        axisWalkOf(listAxis, null, emptyList()) shouldBe AxisWalk.NoRows
        axisWalkOf(detailAxis, null, listOf(WALK_A, WALK_B)) shouldBe AxisWalk.Unusable
        axisWalkOf(detailAxis, null, emptyList()) shouldBe AxisWalk.Unusable
        val bothWalks = listOf(WALK_A, WALK_B)
        val emptyConclusion = AxisConclusion(AttemptOutcome.Empty, WALK_B)
        val failedConclusion = AxisConclusion(AttemptOutcome.Failed("X"), WALK_B)

        axisWalkOf(detailAxis, emptyConclusion, listOf(WALK_A)) shouldBe AxisWalk.NoRows
        axisWalkOf(detailAxis, failedConclusion, listOf(WALK_B)) shouldBe AxisWalk.Unusable
        axisWalkOf(detailAxis, succeededAt(WALK_A), bothWalks) shouldBe AxisWalk.Use(WALK_A)
        // 결말이 **있으면** 축이 무엇이든 그 결말이 이긴다.
        axisWalkOf(listAxis, succeededAt(WALK_A), bothWalks) shouldBe AxisWalk.Use(WALK_A)
    }
}
