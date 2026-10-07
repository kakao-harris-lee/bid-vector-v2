package bidvector.workflow.evaluation

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
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

/**
 * 사다리 정책의 **버전 식별자** — `EvaluateCandidatesUseCase.reach` 가 `Resolution.Resolved`
 * 에 싣는 값이고, **같은 인스턴스**가 알림 payload 의 `ladderPolicyVersion` 으로 간다
 * (D-6F10-19).
 *
 * 왜 한 자리인가: 앞 판은 이 리터럴이 `reach()` 본문에 인라인돼 있었다. payload 가 같은
 * 사실을 실어야 하는데 값을 두 자리에 적으면 한쪽만 바뀌어 **「판정에 쓰인 버전」과
 * 「행에 적힌 버전」이 갈린다** — 그 둘이 갈리면 6D-2 의 재현 등식이 거짓을 말한다. 같은
 * `val` 을 두 곳이 참조하면 갈릴 자리가 없다.
 *
 * 값 자체는 운영 값이 아니라 [EVALUATION_LADDER_POLICY_SLOT] 과 같은 legacy-behavior
 * 자리표시자다(`OPEN-4B1-LADDER-THRESHOLDS` 가 운영 값을 정하면 함께 바뀐다).
 */
val EVALUATION_LADDER_POLICY_VERSION: PolicyVersion =
    PolicyVersion(EffectiveFrom.Initial, "m4-4b2-legacy-behavior-2026-09-09")
