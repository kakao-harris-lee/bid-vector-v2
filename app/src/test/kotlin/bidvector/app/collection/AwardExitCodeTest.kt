package bidvector.app.collection

import bidvector.procurement.BudgetLimit
import bidvector.procurement.TruncationCause
import bidvector.workflow.collection.AwardCollectionHalt
import bidvector.workflow.collection.AwardCollectionReport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

    /**
     * **드롭·중복은 종료 코드를 움직이지 않는다**(팀장 결정 2026-11-06 · cr r3 M-1·OQ). 다 받고도 덜
     * 적재한 슬롯은 있지만 그 결측은 재실행으로 풀리지 않으므로, 미완으로 세면 exit 2 가 상주해
     * 「같은 조각을 다시 돌린다」가 되돌이가 된다. 그 축은 마침 줄의 계수와 runbook §3.1 이 든다.
     *
     * 이 단언이 그 결정을 **잠근다** — 뒤 라운드가 그 축을 종료 코드로 끌어올리려면 이 test 를 먼저
     * 고쳐야 하고, 그러면 되돌이의 대가를 같이 보게 된다.
     */
    @Test
    fun `드롭과 중복이 있어도 끝까지 걸은 실행은 0 이다 — 계수는 줄이 든다`() {
        val report = reportOf(unfinishedSlots = 0, halted = null, duplicates = 1, dropped = 2)

        awardExitCodeOf(report) shouldBe CollectionExitCode.COMPLETE
        awardFinishLine(report, CollectionExitCode.COMPLETE) shouldContain "duplicates=1 dropped=2"
    }

    /**
     * **멈춤 줄의 사유는 닫힌 어휘다**(cr r4 L-2) — 타입 `toString()` 이면 `QuotaExhausted` 가 실리고,
     * 값을 나르는 사유가 오는 날에는 그 필드값까지 줄에 실리고, 타입 이름을 바꾸면 앞 실행이 남긴 줄도
     * 읽지 못한다. `haltOf` 가 **다음 사유를 이 칸으로 보내기로 사전 결정**했으므로 그 자리가 먼저
     * 맞아야 한다.
     *
     * 회계 행(`truncation_cause` = 타입 단순 이름)과는 **일부러 다른 어휘**다(cr r5 M-2) — 두 자리를
     * 옮겨 읽는 대응표는 runbook §3.1 에 있다.
     */
    @Test
    fun `멈춤 줄의 사유는 닫힌 어휘로 적는다`() {
        val quota = AwardCollectionHalt(null, TruncationCause.QuotaExhausted, notAttemptedSlots = 1)

        awardHaltLine(quota) shouldContain "truncation=QUOTA_EXHAUSTED"
        // 예산 멈춤은 사유 칸이 비고 상한 칸이 그 자리를 든다.
        awardHaltLine(AwardCollectionHalt(BudgetLimit.TOTAL, null)) shouldContain "truncation=none"
    }

    /** 줄에 실리는 계수들 — 종료 코드가 보는 두 칸(`unfinishedSlots`·`halted`)만 호출부가 정한다. */
    private fun reportOf(
        unfinishedSlots: Int,
        halted: AwardCollectionHalt?,
        duplicates: Int = 0,
        dropped: Int = 0,
    ): AwardCollectionReport =
        AwardCollectionReport(
            slots = 2,
            received = 6,
            stored = 6 - duplicates - dropped,
            duplicates = duplicates,
            dropped = dropped,
            unfinishedSlots = unfinishedSlots,
            halted = halted,
        )
}
