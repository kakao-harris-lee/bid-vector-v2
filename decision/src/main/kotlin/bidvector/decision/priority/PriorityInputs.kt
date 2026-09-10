package bidvector.decision.priority

import bidvector.decision.UnitScore
import java.math.BigDecimal

/**
 * [composePriority] 의 입력 여덟(scope.md ①) — 성분 다섯([Component] 과 이름이 같다)
 * + penalty 입력 셋(`loadRatio`·`workload`·`complexity`, 조사 §1.1
 * `allocation.py:505-532`). 성분 값의 **산출**(마감→urgency 등)은 이 slice 밖(4B-5)
 * — 이 타입은 이미 계산된 값만 받는다(설계 검토 (0) 경계).
 *
 * `loadRatio`는 나머지 일곱과 달리 [BigDecimal]이다 — legacy `load_ratio`(활성 입찰 /
 * 최대 입찰)는 용량 초과가 구조적으로 가능해 `[0,1]`로 닫히지 않는다
 * (`allocation.py:505-517`). 나머지 일곱은 [UnitScore]([0,1] 구조적 경계, `decision`
 * 모듈이 이미 소유한 타입 — 새로 만들지 않는다).
 */
data class PriorityInputs(
    val match: ScoreFact<UnitScore>,
    val urgency: ScoreFact<UnitScore>,
    val competitiveness: ScoreFact<UnitScore>,
    val budgetCapture: ScoreFact<UnitScore>,
    val expectedMargin: ScoreFact<UnitScore>,
    val loadRatio: ScoreFact<BigDecimal>,
    val workload: ScoreFact<UnitScore>,
    val complexity: ScoreFact<UnitScore>,
)

/**
 * [Component] → 그 성분의 [ScoreFact] 조회(scope.md ③ 「Component.entries 기반」 순회의
 * 유일한 분기점). 새 [Component] 값이 추가되면 이 `when`이 컴파일 실패로 잡는다(위협
 * 모델 (c)).
 */
internal fun PriorityInputs.factFor(component: Component): ScoreFact<UnitScore> =
    when (component) {
        Component.Match -> match
        Component.Urgency -> urgency
        Component.Competitiveness -> competitiveness
        Component.BudgetCapture -> budgetCapture
        Component.ExpectedMargin -> expectedMargin
    }
