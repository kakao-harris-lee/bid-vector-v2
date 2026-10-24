package bidvector.app.collection

import bidvector.workflow.collection.OpeningPromotionReport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M7/7C⑤-0 — 승격 로그 줄의 **칸↔계수 배선**을 잠근다. 이 줄이 계수들의 유일한 공시 자리라, 배선이
 * 어긋나면 운영자가 읽는 수가 딴 것을 가리킨다.
 *
 * 값을 **서로 겹치지 않는 수**로 준다 — 그래야 두 계수를 서로 바꾼 변이도, 칸을 리터럴로 바꾼 변이도
 * 붉어진다(형제 러너의 같은 판단: 전 계수가 0 인 fixture 로는 리터럴 `0` 변이가 초록이었다).
 */
class OpeningPromotionRunnerTest {
    private fun distinctCounts(): OpeningPromotionReport =
        OpeningPromotionReport(
            groupsRead = 19,
            promoted = 3,
            skippedUnchanged = 5,
            outsideSample = 7,
            noUsableAxis = 2,
            rejected = 2,
            unkeyedRows = 11,
            maskedRows = 13,
            expiredRows = 17,
        )

    @Test
    fun `계수마다 다른 수를 주면 줄의 칸이 그 수를 그대로 싣는다`() {
        promotionFinishedLine(distinctCounts()) shouldBe
            "opening-promote finished groupsRead=19 promoted=3 skippedUnchanged=5 outsideSample=7 " +
            "noUsableAxis=2 rejected=2 unkeyedRows=11 maskedRows=13 expiredRows=17"
    }

    @Test
    fun `읽은 묶음 수와 사유별 계수의 합이 어긋나면 보고를 지을 수 없다`() {
        val thrown =
            runCatching {
                OpeningPromotionReport(
                    groupsRead = 5,
                    promoted = 1,
                    skippedUnchanged = 0,
                    outsideSample = 0,
                    noUsableAxis = 0,
                    rejected = 0,
                    unkeyedRows = 0,
                    maskedRows = 0,
                    expiredRows = 0,
                )
            }.exceptionOrNull()

        (thrown is IllegalArgumentException) shouldBe true
    }

    @Test
    fun `표본이 없어 건너뛴 줄은 승격 0 을 명시한다`() {
        PROMOTION_SKIPPED_LINE shouldBe "opening-promote skipped reason=SAMPLE_NOT_CONFIRMED promoted=0"
    }
}
