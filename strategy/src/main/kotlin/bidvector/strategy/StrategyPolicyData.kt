package bidvector.strategy

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.math.BigDecimal

/**
 * 전략 판정이 소비하는 정책(⑧) — 점수 축 셋의 허용 범위와 예산 한계 경계 포함성. legacy
 * 기본값(0.6/0.55/0.7/0.45/1.0/10)은 이 타입이 값을 지어내지 않는다 — `legacy-behavior`로
 * test 정책 인스턴스에만 쓴다(스카우트 §1.1 좌표는 그쪽 주석에 남긴다, A-01).
 */
data class StrategyPolicyData(
    val matchScoreRange: ScoreRange,
    val probabilityScoreRange: ScoreRange,
    val priorityScoreRange: ScoreRange,
    val budgetBoundInclusivity: BudgetBoundInclusivity,
)

/**
 * 형태·version 배관만(`OPEN-1E-SCORE`류) — 점수 범위 **내용**은 구조적으로 가능한 전체
 * 범위(`[0,1]`)를 자리표시자로 둔다(1C `LICENSE_QUALIFICATION_POLICY`의 빈 내용 관례와
 * 같은 자리 — 그 타입은 빈 리스트로, 이 타입은 숫자라 "내용 없음"을 전체 구조적 범위로
 * 표현한다). `budgetBoundInclusivity`만 D-15가 실제로 정한 값(`Inclusive`)이다.
 */
val STRATEGY_POLICY: EffectiveDatedPolicy<StrategyPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m1/1e/scope.md D-13·D-15 — 점수 범위 내용 미확정, 형태만(2026-09-06)",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    StrategyPolicyData(
                        matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                        probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                        priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                        budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
                    ),
            ),
    )
