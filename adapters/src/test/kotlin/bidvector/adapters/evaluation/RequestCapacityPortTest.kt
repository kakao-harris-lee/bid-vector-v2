package bidvector.adapters.evaluation

import bidvector.workflow.evaluation.CapacitySnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * [RequestCapacityPort] — `CapacityPort`의 첫 production 구현(M6/6A-3+6F-3, D-6A3-5). 요청
 * 스코프로 지어지고 생성자 인자가 값의 전부다((2b) 「닫는다」) — `snapshot()`은 그 값을
 * 그대로 [CapacitySnapshot]으로 옮길 뿐 새 계산값을 만들지 않는다.
 */
class RequestCapacityPortTest {
    @Test
    fun `snapshot 은 생성자 값을 그대로 옮긴다`() {
        val port = RequestCapacityPort(currentActiveBids = 3, maxActiveBids = 10)

        port.snapshot() shouldBe CapacitySnapshot(currentActiveBids = 3, maxActiveBids = 10)
    }

    @Test
    fun `currentActiveBids 가 0 이면 허용된다`() {
        val port = RequestCapacityPort(currentActiveBids = 0, maxActiveBids = 1)

        port.snapshot() shouldBe CapacitySnapshot(currentActiveBids = 0, maxActiveBids = 1)
    }

    @Test
    fun `currentActiveBids 가 음수면 InvalidEvaluationRequestException 을 던진다 — ErrorMapping 이 이 타입만 400 으로 매핑한다`() {
        shouldThrow<InvalidEvaluationRequestException> {
            RequestCapacityPort(currentActiveBids = -1, maxActiveBids = 10)
        }
    }
}
