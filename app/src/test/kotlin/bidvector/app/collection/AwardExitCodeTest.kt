package bidvector.app.collection

import bidvector.procurement.BudgetLimit
import bidvector.procurement.TruncationCause
import bidvector.workflow.collection.AwardCollectionHalt
import bidvector.workflow.collection.AwardCollectionReport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * cr H-1 회귀 — 낙찰 목록 실행의 종료 코드 술어. **절단은 멈춤과 같은 처방**(그 조각을 다시 돌린다)이고
 * 그래서 같은 미완 코드로 나간다. 0 은 「조각을 끝까지 걸었다」일 때만이다.
 *
 * 이 갈래에 슬롯 단위 이어 돌기가 없다는 것이 이 단언의 근거다 — 절단을 0 으로 내면 운영자가 다음
 * 조각으로 넘어가고 그 개찰일의 낙찰 원문은 사람이 다시 지목하지 않으면 영영 들어오지 않는다.
 */
class AwardExitCodeTest {
    @Test
    fun `끝까지 걸은 실행만 0 이다`() {
        awardExitCodeOf(reportOf(truncatedSlots = 0, halted = null)) shouldBe CollectionExitCode.COMPLETE
    }

    @Test
    fun `잘린 슬롯이 있으면 멈추지 않았어도 미완이다`() {
        awardExitCodeOf(reportOf(truncatedSlots = 1, halted = null)) shouldBe CollectionExitCode.INCOMPLETE
    }

    @Test
    fun `예산 멈춤도 쿼터 멈춤도 미완이다`() {
        val budget = AwardCollectionHalt(BudgetLimit.TOTAL, null, notAttemptedSlots = 2)
        val quota = AwardCollectionHalt(null, TruncationCause.QuotaExhausted, notAttemptedSlots = 1)

        awardExitCodeOf(reportOf(truncatedSlots = 0, halted = budget)) shouldBe CollectionExitCode.INCOMPLETE
        awardExitCodeOf(reportOf(truncatedSlots = 0, halted = quota)) shouldBe CollectionExitCode.INCOMPLETE
    }

    /** 줄에 실리는 계수들 — 종료 코드가 보는 두 칸(`truncatedSlots`·`halted`)만 호출부가 정한다. */
    private fun reportOf(
        truncatedSlots: Int,
        halted: AwardCollectionHalt?,
    ): AwardCollectionReport =
        AwardCollectionReport(
            slots = 2,
            received = 6,
            stored = 6,
            truncatedSlots = truncatedSlots,
            halted = halted,
        )
}
