package bidvector.workflow.prediction

import java.time.Duration

/**
 * ML 호출에 남은 예산(scope.md ②, ADR 0010 D-2) — [bidvector.workflow.prediction.BidPredictionPort.predict]의
 * 필수 인자다. 어댑터는 **이 값에서만** gRPC deadline 을 만든다 — deadline 없는 호출은
 * 시그니처 자체가 없다(설계 검토 (1) 우회 (1) 차단). 상한(정책 ceiling)은 어댑터의
 * `MlCallPolicyData.deadlineCeiling`이 정한다 — 이 타입은 하한(양수)만 강제한다.
 */
data class CallBudget(
    val remaining: Duration,
) {
    init {
        require(!remaining.isNegative && !remaining.isZero) {
            "CallBudget.remaining은 0보다 커야 한다: $remaining"
        }
    }
}
