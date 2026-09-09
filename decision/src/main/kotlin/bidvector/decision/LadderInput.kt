package bidvector.decision

import java.math.BigDecimal

private val UNIT_MIN: BigDecimal = BigDecimal.ZERO
private val UNIT_MAX: BigDecimal = BigDecimal.ONE

/**
 * 사다리가 읽는 무차원 점수(조사 §7 — `priorityScore`·`probabilityScore`·`matchedScore`).
 * `[0,1]` 구조적 경계를 갖는다 — legacy 의 `probability_score`는 상한이 없었다(조사 §8
 * R-3, `matched_score`만 `le=1`이었다). `decision`은 domain 층이라 `strategy` 모듈의
 * 같은 개념(`Score`)을 참조할 수 없다(ADR 0006 D-4, 도메인 모듈은 서로를 직접 참조하지
 * 못한다 — `decision/build.gradle.kts`가 `shared-kernel` 하나만 의존으로 선언한다) —
 * 이 타입은 그 경계 안에서 이 모듈이 소유하는 최소 재구현이다. 생성자는 공개다(입력값이라
 * 통로 보호 대상이 아니다 — [Verdict]와 다른 축).
 */
data class UnitScore(
    val value: BigDecimal,
) {
    init {
        require(value >= UNIT_MIN && value <= UNIT_MAX) { "UnitScore는 [0,1] 범위여야 한다: $value" }
    }
}

/**
 * 사다리가 실제로 읽는 다섯 입력(scope.md ⑦, 조사 §7 — `DecisionSignals` 16 필드 중
 * 다섯). 렌더링 입력 열하나는 이 타입에 없다(N-7 폐쇄 — 커널 서명이 계약이라는 규율).
 *
 * 점수 셋은 **nullable**로 부재를 나른다(1E `ActionThresholds` 관례 — 「미설정」과
 * 「값 0」은 다른 상태다, §1.3). ML 이 점수를 못 낸 경우가 `null`이다 — `0`으로 접지
 * 않는다(조사 §7.2 N-9 폐쇄).
 */
data class LadderInput(
    val priorityScore: UnitScore?,
    val probabilityScore: UnitScore?,
    val matchedScore: UnitScore?,
    val currentActiveBids: Int,
    val maxActiveBids: Int,
) {
    init {
        require(currentActiveBids >= 0) { "currentActiveBids는 음수일 수 없다: $currentActiveBids" }
        require(maxActiveBids >= 0) { "maxActiveBids는 음수일 수 없다: $maxActiveBids" }
    }
}
