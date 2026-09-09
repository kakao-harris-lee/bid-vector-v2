package bidvector.workflow.evaluation

import java.math.BigDecimal

/**
 * 사다리 정책 값 셋 중 운영자 전략(`ActionThresholds`)이 소유하지 않는 셋(4B-1
 * `OPEN-4B1-LADDER-THRESHOLDS`, 운영자 승인 대기) — `capacityHoldPriorityThreshold`·
 * `forceBidProbabilityThreshold`·`forceBidMatchedThreshold`. `bidNowThreshold`·
 * `reviewThreshold`는 이 슬롯에 없다 — 운영자 전략(`OperatorStrategy.actionThresholds`)
 * 이 그 둘을 이미 소유한다(`ActionThresholds` KDoc "소비하지 않는다(사다리는 M4 4B)").
 *
 * 값은 4B-1이 test 정책으로 이미 쓴 legacy-behavior 자리표시자와 **같다**(일관성) —
 * 운영 값이 아니다. 매직 넘버가 아니라 이 정책 슬롯 하나에 모은다(v2-지침서 §5).
 */
data class LadderPolicySlot(
    val capacityHoldPriorityThreshold: BigDecimal,
    val forceBidProbabilityThreshold: BigDecimal,
    val forceBidMatchedThreshold: BigDecimal,
)

/** 4B-1 fixture(`verdict-005`~`012`)가 쓴 것과 같은 legacy-behavior 값(`policy-values.md` 관례). */
val EVALUATION_LADDER_POLICY_SLOT: LadderPolicySlot =
    LadderPolicySlot(
        capacityHoldPriorityThreshold = BigDecimal("0.8"),
        forceBidProbabilityThreshold = BigDecimal("0.8"),
        forceBidMatchedThreshold = BigDecimal("0.7"),
    )
