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
 * **`save()`는 `error()`다(D-6A3-18, 검토 라운드 1 verifier LOW 시정).** dry-run use case는
 * 오늘 `save`를 부르지 않는다(위협 모델 ① effect 0) — 실 저장소로 위임하면 이 객체가
 * 쓰기 경로를 계속 쥐고 있는 셈이라 dry-run 의 「effect 0」 취지와 어긋난다. 위임할 실
 * 저장소 참조([delegate])도 더 이상 필요 없어 생성자에서 뺐다 — `load()`가 이미 [loaded]
 * 만으로 완결된다.
 */
class PinnedStrategyRepository(
    private val loaded: OperatorStrategy,
) : StrategyRepository {
    override fun load(): OperatorStrategy = loaded

    override fun save(applied: AppliedStrategy): Nothing =
        error("PinnedStrategyRepository는 dry-run 전용이다 — save()를 부르지 않는다(effect 0)")
}
