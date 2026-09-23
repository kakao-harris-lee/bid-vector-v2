package bidvector.adapters.evaluation

import bidvector.strategy.OperatorStrategy
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.StrategyRepository

/**
 * 요청 스코프 [StrategyRepository] 데코레이터(M6/6A-3+6F-3, D-6A3-5, 0단계 실측 추천 (a)) —
 * 전략은 요청당 정확히 한 번 읽는다. `EvaluationDryRunFactory`(app.wiring)가 실 저장소에서
 * 한 번 `load()`한 값을 [loaded]로 고정해 이 데코레이터로 감싼다 —
 * `EvaluateCandidatesUseCase.evaluate()`의 `strategies.load()` 호출은 실 저장소를 다시
 * 부르지 않고 이 캐시값을 돌려받는다. 그래서 같은 요청에서 여력 상한([RequestCapacityPort]가
 * 소비)과 사다리 판정이 같은 개정을 본다(4B-1 형태 ⑧ 「두 시점에 다르게 세어진다」의
 * 재발 방지).
 *
 * `save()`는 dry-run 경로에서 불리지 않지만 인터페이스 계약을 지키려 실 저장소로 위임한다
 * — 이 클래스 자신이 쓰기를 흡수하지 않는다.
 */
class PinnedStrategyRepository(
    private val loaded: OperatorStrategy,
    private val delegate: StrategyRepository,
) : StrategyRepository {
    override fun load(): OperatorStrategy = loaded

    override fun save(applied: AppliedStrategy) = delegate.save(applied)
}
