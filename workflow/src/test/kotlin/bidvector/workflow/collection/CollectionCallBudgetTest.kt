package bidvector.workflow.collection

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

private val DAY = LocalDate.of(2026, 9, 28)

/**
 * A-1 승인 수치(일 20,000 · 총 80,000)는 **설정값**이고 코드에 기본값이 없다 — 이 test 는
 * 한도가 실제로 호출을 멈추는지를 잰다. 「상한이 있다」와 「상한이 무른다」는 다른 것이다.
 */
class CollectionCallBudgetTest {
    @Test
    fun `일 한도에 닿으면 더 쓰지 못하고 사유가 일 한도로 나온다`() {
        val ledger = CallBudgetLedger(CollectionCallBudget(perDay = 3, total = 100), DAY)

        repeat(3) { ledger.consume(DAY, 1).shouldBeInstanceOf<BudgetOutcome.Allowed>() }

        val exhausted = ledger.consume(DAY, 1).shouldBeInstanceOf<BudgetOutcome.Exhausted>()
        exhausted.limit shouldBe BudgetLimit.DAILY
    }

    @Test
    fun `총 한도에 닿으면 날이 바뀌어도 더 쓰지 못한다`() {
        val ledger = CallBudgetLedger(CollectionCallBudget(perDay = 2, total = 4), DAY)
        ledger.consume(DAY, 2).shouldBeInstanceOf<BudgetOutcome.Allowed>()
        ledger.consume(DAY.plusDays(1), 2).shouldBeInstanceOf<BudgetOutcome.Allowed>()

        val nextDay = ledger.consume(DAY.plusDays(2), 1).shouldBeInstanceOf<BudgetOutcome.Exhausted>()

        nextDay.limit shouldBe BudgetLimit.TOTAL
    }

    @Test
    fun `날이 바뀌면 일 회계는 0 에서 다시 센다 — 총 회계는 이어진다`() {
        val ledger = CallBudgetLedger(CollectionCallBudget(perDay = 2, total = 10), DAY)
        ledger.consume(DAY, 2)
        ledger.consume(DAY, 1).shouldBeInstanceOf<BudgetOutcome.Exhausted>()

        ledger.consume(DAY.plusDays(1), 2).shouldBeInstanceOf<BudgetOutcome.Allowed>()

        ledger.spentTotal shouldBe 4
        ledger.spentToday shouldBe 2
    }

    @Test
    fun `한도를 넘기는 한 걸음은 통째로 거부된다 — 일부만 쓰고 넘어가지 않는다`() {
        val ledger = CallBudgetLedger(CollectionCallBudget(perDay = 5, total = 100), DAY)
        ledger.consume(DAY, 3)

        ledger.consume(DAY, 3).shouldBeInstanceOf<BudgetOutcome.Exhausted>()

        ledger.spentToday shouldBe 3
    }

    @Test
    fun `한도 값은 양수여야 한다 — 0 이나 음수는 구성 오류다`() {
        runCatching { CollectionCallBudget(perDay = 0, total = 10) }.isFailure shouldBe true
        runCatching { CollectionCallBudget(perDay = 10, total = 0) }.isFailure shouldBe true
        runCatching { CollectionCallBudget(perDay = 100, total = 10) }.isFailure shouldBe true
    }
}
