package bidvector.workflow.evaluation

import java.math.BigDecimal

private val THRESHOLD_MIN: BigDecimal = BigDecimal.ZERO
private val THRESHOLD_MAX: BigDecimal = BigDecimal.ONE

/**
 * 사다리 정책 값 셋 중 운영자 전략(`ActionThresholds`)이 소유하지 않는 셋(4B-1
 * `OPEN-4B1-LADDER-THRESHOLDS`, 운영자 승인 대기) — `capacityHoldPriorityThreshold`·
 * `forceBidProbabilityThreshold`·`forceBidMatchedThreshold`. `bidNowThreshold`·
 * `reviewThreshold`는 이 슬롯에 없다 — 운영자 전략(`OperatorStrategy.actionThresholds`)
 * 이 그 둘을 이미 소유한다(`ActionThresholds` KDoc "소비하지 않는다(사다리는 M4 4B)").
 *
 * 값은 4B-1이 test 정책으로 이미 쓴 legacy-behavior 자리표시자와 **같다**(일관성) —
 * 운영 값이 아니다. 매직 넘버가 아니라 이 정책 슬롯 하나에 모은다(v2-지침서 §5).
 *
 * **형제 `VerdictLadderPolicyData`와 같은 범위 불변식을 생성 시점에 강제한다**(수정
 * 라운드 3 L-2) — 셋 다 `VerdictLadder.judge`에서 `BigDecimal` 점수와 직접 비교되는
 * 임계이므로 형제의 `[0,1]` 범위 축과 같다. `reviewThreshold <= bidNowThreshold` 같은
 * 순서 불변식은 이 슬롯에 대응 필드가 없어(그 둘은 `ActionThresholds` 소유) 옮기지
 * 않는다 — 이 세 필드는 `judge`에서 서로 다른 입력 축(priority·probability·matched)과
 * 비교돼 셋 사이에 순서 관계가 없다(조사 §2.1~§2.2, `VerdictLadder.kt` 분기 1·2(b)).
 * 강제하지 않으면 실패 지점이 조립부가 아니라 판정부로 밀린다.
 */
data class LadderPolicySlot(
    val capacityHoldPriorityThreshold: BigDecimal,
    val forceBidProbabilityThreshold: BigDecimal,
    val forceBidMatchedThreshold: BigDecimal,
) {
    init {
        listOf(
            "capacityHoldPriorityThreshold" to capacityHoldPriorityThreshold,
            "forceBidProbabilityThreshold" to forceBidProbabilityThreshold,
            "forceBidMatchedThreshold" to forceBidMatchedThreshold,
        ).forEach { (name, value) ->
            require(value >= THRESHOLD_MIN && value <= THRESHOLD_MAX) { "$name 은 [0,1] 범위여야 한다: $value" }
        }
    }
}

/** 4B-1 fixture(`verdict-005`~`012`)가 쓴 것과 같은 legacy-behavior 값(`policy-values.md` 관례). */
val EVALUATION_LADDER_POLICY_SLOT: LadderPolicySlot =
    LadderPolicySlot(
        capacityHoldPriorityThreshold = BigDecimal("0.8"),
        forceBidProbabilityThreshold = BigDecimal("0.8"),
        forceBidMatchedThreshold = BigDecimal("0.7"),
    )
