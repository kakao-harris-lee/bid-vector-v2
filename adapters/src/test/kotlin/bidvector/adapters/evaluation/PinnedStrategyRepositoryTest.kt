package bidvector.adapters.evaluation

import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.StrategyRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

private fun testStrategy(maxActiveBids: Int?) =
    when (
        val result =
            validate(
                StrategyDraft(maxActiveBids = maxActiveBids),
                StrategyRevision(1),
                STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>,
            )
    ) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test fixture 는 항상 Valid 여야 한다: ${result.violations}")
    }

/** `load()`가 불리면 실패하는 감시견 — [PinnedStrategyRepository]가 실 저장소를 다시 부르지 않는지 잰다. */
private class LoadForbiddenStrategyRepository : StrategyRepository {
    override fun load(): Nothing = error("PinnedStrategyRepository는 delegate.load()를 다시 부르면 안 된다")

    override fun save(applied: AppliedStrategy) = Unit
}

/**
 * [PinnedStrategyRepository] — D-6A3-5 「전략은 요청당 정확히 한 번 읽는다」의 계수 test.
 * `AppliedStrategy`의 생성자가 `workflow` 모듈 internal 이라 이 모듈에서 `save()` 위임 자체는
 * 직접 못 잰다(구조적 폐쇄, `RecordingNotificationRequestPortTest`와 같은 한계) — `load()`가
 * 캐시값을 돌려주고 delegate 를 다시 부르지 않는지만 잰다.
 */
class PinnedStrategyRepositoryTest {
    @Test
    fun `load 는 고정값을 그대로 돌려주고 delegate 를 다시 부르지 않는다`() {
        val pinned = testStrategy(maxActiveBids = 7)
        val repository = PinnedStrategyRepository(pinned, LoadForbiddenStrategyRepository())

        repository.load() shouldBe pinned
        repository.load() shouldBe pinned // 두 번째 호출도 여전히 delegate 를 안 부른다.
    }
}
