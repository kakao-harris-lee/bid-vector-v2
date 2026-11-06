package bidvector.app.collection

import bidvector.procurement.BudgetLimit
import bidvector.procurement.TruncationCause
import bidvector.workflow.collection.AwardCollectionHalt
import bidvector.workflow.collection.AwardCollectionReport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * cr r1 H-1 · cr r2 H-2 회귀 — 낙찰 목록 실행의 종료 코드 술어. **끝내지 못한 슬롯은 멈춤과 같은
 * 처방**(그 조각을 다시 돌린다)이고 그래서 같은 미완 코드로 나간다. 0 은 「조각을 끝까지 걸었다」일
 * 때만이다.
 *
 * 이 갈래에 슬롯 단위 이어 돌기가 없다는 것이 이 단언의 근거다 — 미완을 0 으로 내면 운영자가 다음
 * 조각으로 넘어가고 그 개찰일의 낙찰 원문은 사람이 다시 지목하지 않으면 영영 들어오지 않는다.
 *
 * 「끝내지 못했는가」를 **무엇이 정하는가**는 use case 쪽 test 가 잰다(`CollectAwardListUseCaseTest` —
 * 절단 사유와 사유 없는 짧은 걷기 둘 다). 여기는 그 계수를 받아 코드로 바꾸는 술어만 본다.
 */
class AwardExitCodeTest {
    @Test
    fun `끝까지 걸은 실행만 0 이다`() {
        awardExitCodeOf(reportOf(unfinishedSlots = 0, halted = null)) shouldBe CollectionExitCode.COMPLETE
    }

    @Test
    fun `끝내지 못한 슬롯이 있으면 멈추지 않았어도 미완이다`() {
        awardExitCodeOf(reportOf(unfinishedSlots = 1, halted = null)) shouldBe CollectionExitCode.INCOMPLETE
    }

    /**
     * 술어의 두 항을 **따로** 잠근다(각 항을 지우는 변이를 각각 잡는다). 아래 두 보고의 모양
     * (`unfinishedSlots = 0` 인데 `halted != null`)은 **실행이 내는 모양이 아니다**(cr r1 L-5) —
     * 멈춘 슬롯 자신이 사유를 달고 있어 실제 멈춤은 언제나 미완 계수 1 이상이다. 축 분리용 값이므로
     * 뒤 라운드가 이것을 로그 모양으로 읽지 않는다.
     */
    @Test
    fun `예산 멈춤도 쿼터 멈춤도 미완이다`() {
        val budget = AwardCollectionHalt(BudgetLimit.TOTAL, null, notAttemptedSlots = 2)
        val quota = AwardCollectionHalt(null, TruncationCause.QuotaExhausted, notAttemptedSlots = 1)

        awardExitCodeOf(reportOf(unfinishedSlots = 0, halted = budget)) shouldBe CollectionExitCode.INCOMPLETE
        awardExitCodeOf(reportOf(unfinishedSlots = 0, halted = quota)) shouldBe CollectionExitCode.INCOMPLETE
    }

    /** 줄에 실리는 계수들 — 종료 코드가 보는 두 칸(`unfinishedSlots`·`halted`)만 호출부가 정한다. */
    private fun reportOf(
        unfinishedSlots: Int,
        halted: AwardCollectionHalt?,
    ): AwardCollectionReport =
        AwardCollectionReport(
            slots = 2,
            received = 6,
            stored = 6,
            unfinishedSlots = unfinishedSlots,
            halted = halted,
        )
}
