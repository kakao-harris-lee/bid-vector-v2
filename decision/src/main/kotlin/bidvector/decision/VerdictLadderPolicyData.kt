package bidvector.decision

import java.math.BigDecimal

private val THRESHOLD_MIN: BigDecimal = BigDecimal.ZERO
private val THRESHOLD_MAX: BigDecimal = BigDecimal.ONE

/**
 * 사다리 임계 다섯(scope.md ⑥, 조사 §2.4 — legacy 는 두 출처(영속 전략 2 + settings 3)에
 * 흩어져 있었다, DEC-12 A10). 정책 데이터 **한 자리**로 모은다. 값 자체는 이 타입이
 * 지어내지 않는다 — 운영자 승인 대상(`OPEN-4B1-LADDER-THRESHOLDS`), legacy 값은
 * `legacy-behavior`로 test 정책에만 쓴다(1D `ProvenancePolicyData` 관례).
 *
 * **`reviewThreshold <= bidNowThreshold`를 생성 불변식으로 강제한다**(조사 §2.4 N-6
 * 폐쇄) — legacy 는 이 조합이 깨지면 **판정 시점에 조용히 수리**했다
 * (`review_threshold = bid_now_threshold`, 저장값과 판정값이 갈리고 기록이 없다).
 * 이 정책 타입은 그 조합 자체를 구성 불가로 만들어 침묵 수리 자리를 없앤다 — 수리하지
 * 않는다.
 */
data class VerdictLadderPolicyData(
    val capacityHoldPriorityThreshold: BigDecimal,
    val bidNowThreshold: BigDecimal,
    val reviewThreshold: BigDecimal,
    val forceBidProbabilityThreshold: BigDecimal,
    val forceBidMatchedThreshold: BigDecimal,
) {
    init {
        listOf(
            "capacityHoldPriorityThreshold" to capacityHoldPriorityThreshold,
            "bidNowThreshold" to bidNowThreshold,
            "reviewThreshold" to reviewThreshold,
            "forceBidProbabilityThreshold" to forceBidProbabilityThreshold,
            "forceBidMatchedThreshold" to forceBidMatchedThreshold,
        ).forEach { (name, value) ->
            require(value >= THRESHOLD_MIN && value <= THRESHOLD_MAX) { "$name 은 [0,1] 범위여야 한다: $value" }
        }
        require(reviewThreshold <= bidNowThreshold) {
            "reviewThreshold는 bidNowThreshold 이하여야 한다(legacy 침묵 수리 금지): " +
                "review=$reviewThreshold bidNow=$bidNowThreshold"
        }
    }
}
