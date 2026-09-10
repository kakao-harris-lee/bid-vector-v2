package bidvector.decision.priority

/**
 * priority 가중합의 성분 다섯(scope.md ①, 조사 §1.1 — legacy 여섯 가중합
 * `allocation.py:38-43` 에서 확률 축을 뺀 나머지). 이 어휘를 넓히는 편집(성분 추가)은
 * 소진 `when`(`PriorityInputs.factFor`)의 컴파일 실패를 요구한다 — 위협 모델 (c)
 * 「확률 축의 몰래 유입」을 이 enum 이 닫는다.
 */
enum class Component {
    Match,
    Urgency,
    Competitiveness,
    BudgetCapture,
    ExpectedMargin,
}
