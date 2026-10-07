package bidvector.adapters.evaluation

import bidvector.strategy.OperatorStrategy
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.StrategyRepository

/**
 * **읽기 한 번**으로 고정한 [StrategyRepository] 데코레이터(D-6A3-5, 0단계 실측 추천 (a)).
 *
 * **쓰는 자리가 둘이다(cr R-12 로 문면 정정 — 앞 판은 「dry-run 전용」이라고 적었다).**
 * ⓐ dry-run 요청 스코프(`EvaluationDryRunFactory`, app.wiring) ⓑ 커밋 run 배선
 * (`EvaluationCommitWiring`, M6/6F-10 D-6F10-27). 둘 다 「run 하나가 전략을 **정확히 한 번**
 * 읽는다」가 필요한 자리다 — 실 저장소에서 한 번 `load()` 한 값을 [loaded] 로 고정해 이
 * 데코레이터로 감싸면, `EvaluateCandidatesUseCase.evaluate()` 의 `strategies.load()` 는 실
 * 저장소를 다시 부르지 않는다. 그래서 같은 run 에서 여력 상한([RequestCapacityPort] 가 소비)
 * 과 사다리 판정이 같은 개정을 본다(4B-1 형태 ⑧ 「두 시점에 다르게 세어진다」의 재발 방지).
 *
 * **`save()`는 `error()`다(D-6A3-18).** 실 저장소로 위임하면 이 객체가 쓰기 경로를 계속 쥐고
 * 있는 셈이고, 두 사용자 가운데 어느 쪽도 `save` 를 부르지 않는다(`strategies.save` 를 부르는
 * main 코드는 `EditStrategyWorkflow` 하나이고 평가 경로에 그 자리가 없다). 위임할 실 저장소
 * 참조도 생성자에 없다 — `load()` 가 [loaded] 만으로 완결된다.
 */
class PinnedStrategyRepository(
    private val loaded: OperatorStrategy,
) : StrategyRepository {
    override fun load(): OperatorStrategy = loaded

    override fun save(applied: AppliedStrategy): Nothing =
        error("고정 저장소는 쓰기를 받지 않는다 — 한 번 읽은 값만 돌려준다(PinnedStrategyRepository)")
}
