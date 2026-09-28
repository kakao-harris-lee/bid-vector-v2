package bidvector.procurement

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
    fun `이미 쓴 몫으로 seed 되면 그 값에서 이어 센다 — 실행 사이에 상한이 이어진다`() {
        val ledger =
            CallBudgetLedger(
                CollectionCallBudget(perDay = 10, total = 100),
                DAY,
                CallSpend(total = 40, today = 9),
            )

        ledger.spentTotal shouldBe 40
        ledger.consume(DAY, 1).shouldBeInstanceOf<BudgetOutcome.Allowed>()
        ledger.consume(DAY, 1).shouldBeInstanceOf<BudgetOutcome.Exhausted>()
    }

    @Test
    fun `한도 값은 양수여야 한다 — 0 이나 음수는 구성 오류다`() {
        runCatching { CollectionCallBudget(perDay = 0, total = 10) }.isFailure shouldBe true
        runCatching { CollectionCallBudget(perDay = 10, total = 0) }.isFailure shouldBe true
        runCatching { CollectionCallBudget(perDay = 100, total = 10) }.isFailure shouldBe true
    }
}
