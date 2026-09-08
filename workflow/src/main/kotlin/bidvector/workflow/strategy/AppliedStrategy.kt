package bidvector.workflow.strategy

import bidvector.strategy.OperatorStrategy

/**
 * write 경로 우회 폐쇄용 통로(capability) 타입(우회 (3), verifier H-1/H-2 수정) —
 * M3/3B-2 `MaskedKonepsItem`(P-10 (a) allow-list 반전 + 통로 타입)과 같은 갈래다.
 *
 * `bidvector.strategy.validate()`가 public 이고 `StrategyValidation.Valid.strategy`도
 * public 이라(1E D-10) [OperatorStrategy] 자체는 어느 모듈에서든 손에 넣을 수 있다 —
 * `internal constructor`만으로는 「값을 가진 자 = 저장을 실행할 권한을 가진 자」를
 * 가르지 못한다(verifier 실측: `app` test 가 `validate()` 결과를 그대로 `save()`에 넘겨
 * `EditStrategyWorkflow`를 지나지 않고 컴파일됐다).
 *
 * 이 타입이 그 둘을 가른다 — 생성자가 `internal`이라 **`workflow` 모듈 밖에서는 만들 수
 * 없다.** 유일한 생성 경로는 [bidvector.workflow.strategy.apply]의 `onConfirm`(`Applied`
 * 갈래)뿐이고, [StrategyRepository.save]가 [OperatorStrategy]가 아니라 이 타입을 요구해
 * 「전략 값을 안다」와 「이 port 로 저장을 실행할 수 있다」를 구조적으로 분리한다.
 */
@ConsistentCopyVisibility
data class AppliedStrategy internal constructor(
    val strategy: OperatorStrategy,
)
