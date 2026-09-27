package bidvector.workflow.collection

import java.time.LocalDate

/** 어느 한도가 물었는가 — 「멈췄다」만으로는 다음 날 다시 돌려도 되는지를 알 수 없다. */
enum class BudgetLimit { DAILY, TOTAL }

/** 한 걸음의 예산 판정 — 값이다(예외가 아니다). */
sealed interface BudgetOutcome {
    data object Allowed : BudgetOutcome

    data class Exhausted(
        val limit: BudgetLimit,
    ) : BudgetOutcome
}

/**
 * A-1 승인 호출 상한(D-6G-11 — 일 20,000 · 총 80,000). **기본값이 없다**: 두 값 모두 설정이
 * 주어야 하고, 코드가 「안전한 기본값」을 지어내면 승인 범위 밖의 실행이 조용히 가능해진다.
 */
data class CollectionCallBudget(
    val perDay: Int,
    val total: Int,
) {
    init {
        require(perDay > 0) { "일 호출 상한은 양수여야 한다: $perDay" }
        require(total > 0) { "총 호출 상한은 양수여야 한다: $total" }
        require(total >= perDay) { "총 상한($total)이 일 상한($perDay)보다 작을 수 없다" }
    }
}

/**
 * 호출 예산 원장(D-6G-11) — 수집 use case 가 한 걸음을 내딛기 **전에** 묻는다. 초과하면
 * 멈춘다(429 처럼 기다렸다 다시 부르지 않는다 — 승인 범위를 넘는 호출은 기다려도 승인되지
 * 않는다).
 *
 * **한 걸음은 통째로 허가되거나 통째로 거부된다** — 남은 몫만큼 부분 실행하면 「공고 하나를
 * 반만 수집한」 표본 행이 생기고, 그 행은 결측이 랜덤이 아니라 예산 경계에 걸린다(표본이
 * 비뚤어진다).
 *
 * 날짜는 호출부가 준다 — 이 타입은 시계를 갖지 않는다(테스트가 날 경계를 값으로 넘긴다).
 */
class CallBudgetLedger(
    private val budget: CollectionCallBudget,
    startDay: LocalDate,
) {
    private var day: LocalDate = startDay

    var spentToday: Int = 0
        private set

    var spentTotal: Int = 0
        private set

    fun consume(
        onDay: LocalDate,
        calls: Int,
    ): BudgetOutcome {
        require(calls >= 0) { "호출 수는 음수일 수 없다: $calls" }
        if (onDay != day) {
            day = onDay
            spentToday = 0
        }
        return when {
            spentTotal + calls > budget.total -> {
                BudgetOutcome.Exhausted(BudgetLimit.TOTAL)
            }

            spentToday + calls > budget.perDay -> {
                BudgetOutcome.Exhausted(BudgetLimit.DAILY)
            }

            else -> {
                spentToday += calls
                spentTotal += calls
                BudgetOutcome.Allowed
            }
        }
    }
}
