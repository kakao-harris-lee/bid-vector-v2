package bidvector.decision.priority

import bidvector.decision.UnitScore

/**
 * [composePriority] 의 입력 여덟(scope.md ①) — 성분 다섯([Component] 과 이름이 같다)
 * + penalty 입력 셋(`loadRatio`·`workload`·`complexity`, 조사 §1.1
 * `allocation.py:505-532`). 성분 값의 **산출**(마감→urgency 등)은 이 slice 밖(4B-5)
 * — 이 타입은 이미 계산된 값만 받는다(설계 검토 (0) 경계).
 *
 * 여덟 전부 [UnitScore]([0,1] 구조적 경계, `decision` 모듈이 이미 소유한 타입 — 새로
 * 만들지 않는다). **`loadRatio`는 verifier r1 F-2로 [BigDecimal]에서 이 타입으로
 * 좁혔다** — legacy `load_ratio`(활성 입찰 / 최대 입찰)는 용량 초과가 구조적으로
 * 가능해 원래 `[0,1]`로 안 닫힌다고 판단했으나, 그 열린 하한이 음수 입력을 막지 못해
 * penalty 항이 조용히 가산점으로 뒤집히는 경로를 열었다(재현: `loadRatio=-1.0` →
 * `appliedPenalties[LoadRatio]` 가 음수, priority 가 오히려 오른다). 호출부(4B-5)가
 * `[0,1]`로 정규화(용량 초과분은 1로 clamp)한 값을 넘긴다 — 그 정규화 자체는 이
 * 타입의 책임이 아니라 호출부 계약이다.
 */
data class PriorityInputs(
    val match: ScoreFact<UnitScore>,
    val urgency: ScoreFact<UnitScore>,
    val competitiveness: ScoreFact<UnitScore>,
    val budgetCapture: ScoreFact<UnitScore>,
    val expectedMargin: ScoreFact<UnitScore>,
    val loadRatio: ScoreFact<UnitScore>,
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
