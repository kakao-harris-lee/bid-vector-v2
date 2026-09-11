package bidvector.decision.priority.derive

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * ⑦ [workloadNotCollected]·⑤ [competitivenessNotCollected](scope.md, 계약 갱신
 * 2026-09-10 #1 `OPEN-4B5-COMPETITIVENESS`) — 집계 port·시장 평균 fact 는 각각 4B-6·
 * 미수집이라 이 slice 는 상수 Absent 만 낸다.
 */
class WorkloadDerivationTest {
    @Test
    fun `항상 WorkloadNotCollected`() {
        workloadNotCollected() shouldBe DerivationOutcome.Absent(DerivationAbsence.WorkloadNotCollected)
    }

    @Test
    fun `항상 MarketAverageMissing`() {
        competitivenessNotCollected() shouldBe DerivationOutcome.Absent(DerivationAbsence.MarketAverageMissing)
    }
}
