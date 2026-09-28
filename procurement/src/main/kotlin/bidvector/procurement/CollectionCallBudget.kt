package bidvector.procurement

import java.time.LocalDate

/**
 * 상한의 「하루」가 도는 구역(D-6G-47) — **KST 다.** UTC 로 두면 KST 00~09 시 실행에서 원장이
 * 세는 날과 소비가 세는 날이 갈리고, `spentToday` 가 0 으로 되돌아 seed 한 오늘치가 사라진다.
 * 그 회귀는 **시계가 정오 근처면 두 구역의 날짜가 같아 test 가 못 잡는다** — 그래서 이 값을 쓰는
 * test 는 시계를 KST 자정 근처에 둔다.
 */
val COLLECTION_BUDGET_ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")

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
 * **셈의 단위는 호출 하나**다(D-6G-47·56). 앞 문면은 「한 걸음은 통째로 허가되거나 통째로 거부된다」
 * 였는데, 그것은 참인 적이 없었다 — 상한이 어디서 끝나든 마지막 공고의 마지막 축은 반쪽으로 남는다.
 * 지금은 관문이 호출마다 이 원장에 묻고, 끝나지 않은 축은 다음 실행이 받으며, 총 상한으로 끝내 못 받은
 * 공고는 `incomplete_axis` 로 제외·계수된다(D-6G-29 ④ 개정).
 *
 * 날짜는 호출부가 준다 — 이 타입은 시계를 갖지 않는다(테스트가 날 경계를 값으로 넘긴다).
 */
class CallBudgetLedger(
    private val budget: CollectionCallBudget,
    startDay: LocalDate,
    /** 실행 **이전에** 이미 쓴 몫(D-6G-29 ① — 영속 원장에서 seed). 없으면 0 에서 시작한다. */
    alreadySpent: CallSpend = CallSpend(total = 0, today = 0),
) {
    private var day: LocalDate = startDay

    var spentToday: Int = alreadySpent.today
        private set

    var spentTotal: Int = alreadySpent.total
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
