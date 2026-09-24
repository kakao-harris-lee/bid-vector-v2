package bidvector.adapters.evaluation

import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
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

/**
 * [PinnedStrategyRepository] — D-6A3-5 「전략은 요청당 정확히 한 번 읽는다」의 계수 test와
 * D-6A3-18(검토 라운드 1 verifier LOW)의 `save()` 시정. `load()`는 생성자가 받은 고정값을
 * 그대로 돌려준다(더 이상 위임할 실 저장소 참조가 없다 — 구조 자체가 재호출을 만들 수
 * 없다, 이전 판의 「delegate 를 다시 안 부른다」감시견 fake는 더 이상 필요 없다).
 *
 * **`save()`가 `error()`를 던지는 것 자체는 이 모듈에서 실행으로 잴 수 없다(gate 밖).**
 * `AppliedStrategy`의 생성자가 `workflow` 모듈 `internal`이라 `adapters`(그리고 `app`도
 * 마찬가지 — internal 은 클래스 계층이 아니라 모듈 경계다) 밖에서는 그 값을 지을 방법이
 * 없다(`RecordingNotificationRequestPortTest`와 같은 구조적 폐쇄 한계, `NoticeIdTest`의
 * `NoticeRound` 폐쇄와 같은 관례 — 그 증거는 컴파일 실패라 gate-tests.properties 목록
 * 밖이다). 반환 타입 `Nothing`(정상 반환 경로가 아예 없는 시그니처) 자체가 이미 컴파일
 * 층의 증거다 — `save`가 값을 조용히 삼키며 `Unit`을 반환하는 형태로 되돌아가면 그 자체가
 * 소스 리뷰에서 드러난다.
 */
class PinnedStrategyRepositoryTest {
    @Test
    fun `load 는 고정값을 그대로 돌려준다`() {
        val pinned = testStrategy(maxActiveBids = 7)
        val repository = PinnedStrategyRepository(pinned)

        repository.load() shouldBe pinned
        repository.load() shouldBe pinned // 두 번째 호출도 여전히 같은 고정값이다.
    }
}
