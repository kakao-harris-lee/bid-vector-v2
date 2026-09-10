package bidvector.decision.priority

import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import java.math.BigDecimal

/**
 * 재정규화 가중치(scope.md ② 초기값, `PRIORITY_POLICY`와 같은 값) — main 의
 * `PRIORITY_POLICY`를 그대로 읽지 않고 test 전용 사본을 쓴다(3B `testKonepsHttpPolicy`
 * 관례 — 출하 인스턴스는 [PriorityPolicyDataTest] 가 직접 읽어 불변식만 확인한다).
 */
internal val TEST_PRIORITY_POLICY =
    PriorityPolicyData(
        weights =
            mapOf(
                Component.Match to BigDecimal("0.3834"),
                Component.Urgency to BigDecimal("0.2333"),
                Component.Competitiveness to BigDecimal("0.1333"),
                Component.BudgetCapture to BigDecimal("0.1000"),
                Component.ExpectedMargin to BigDecimal("0.1500"),
            ),
        loadPenalty = LoadPenaltyPolicy(ratioWeight = BigDecimal("0.18"), workloadWeight = BigDecimal("0.12")),
        complexityPenalty =
            ComplexityPenaltyPolicy(
                threshold = BigDecimal("0.55"),
                slope = BigDecimal("0.18"),
                cap = BigDecimal("0.12"),
            ),
        categoryOffsetMin = BigDecimal("-0.20"),
        categoryOffsetMax = BigDecimal("0.20"),
        normEpsilon = BigDecimal("0.0001"),
    )

internal fun presentUnit(value: String): ScoreFact<UnitScore> = ScoreFact.Present(UnitScore(BigDecimal(value)))

internal fun absentUnit(reason: MlUnavailableReason = MlUnavailableReason.ScoreNotProvided): ScoreFact<UnitScore> =
    ScoreFact.Absent(reason)

internal fun presentRatio(value: String): ScoreFact<BigDecimal> = ScoreFact.Present(BigDecimal(value))

internal fun absentRatio(reason: MlUnavailableReason = MlUnavailableReason.ScoreNotProvided): ScoreFact<BigDecimal> =
    ScoreFact.Absent(reason)

/**
 * 조사 §1.1 손계산 표본의 공통 뼈대 — 기본값은 「표본1」(다섯 성분 Present, penalty
 * 입력 셋은 Absent). 각 test 는 필요한 축만 override 한다.
 */
internal fun fullInputs(
    match: String = "0.80",
    urgency: String? = "0.60",
    competitiveness: String? = "0.50",
    budgetCapture: String? = "0.40",
    expectedMargin: String? = "0.70",
    loadRatio: String? = null,
    workload: String? = null,
    complexity: String? = null,
): PriorityInputs =
    PriorityInputs(
        match = presentUnit(match),
        urgency = urgency?.let(::presentUnit) ?: absentUnit(),
        competitiveness = competitiveness?.let(::presentUnit) ?: absentUnit(),
        budgetCapture = budgetCapture?.let(::presentUnit) ?: absentUnit(),
        expectedMargin = expectedMargin?.let(::presentUnit) ?: absentUnit(),
        loadRatio = loadRatio?.let(::presentRatio) ?: absentRatio(),
        workload = workload?.let(::presentUnit) ?: absentUnit(),
        complexity = complexity?.let(::presentUnit) ?: absentUnit(),
    )

/** epsilon 허용 비교(재정규화 나눗셈 손계산 대조 — 정밀도 경합 대신 근사, `MoneyArithmetic` 류와 다른 축). */
internal fun BigDecimal.closeTo(
    expected: BigDecimal,
    epsilon: BigDecimal = BigDecimal("0.000001"),
): Boolean = (this - expected).abs() <= epsilon
