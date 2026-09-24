package bidvector.adapters.evaluation

import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test

/**
 * [RecordingNotificationRequestPort] — dry-run 전용 `NotificationRequestPort` 구현(D-6A3-3).
 * `NotificationRequest`의 생성자가 `internal`(`workflow` 모듈 한정)이라 이 모듈의 test는
 * 실제 값을 직접 지어 `request(...)`에 넣을 수 없다 — 「호출부가 넘긴 값을 그대로 모은다」는
 * 거동은 acceptance E2E(scope.md ⑤·⑦, `wouldNotifyNoticeIds == bidNowNoticeIds`)가 실
 * `EvaluateCandidatesUseCase` 경유로 잠근다. 이 test는 이 클래스 자신의 닫힌 계약(초기
 * 상태·읽기 전용 노출)만 잰다.
 */
class RecordingNotificationRequestPortTest {
    @Test
    fun `생성 직후 requested 는 비어 있다`() {
        val port = RecordingNotificationRequestPort()

        port.requested().shouldBeEmpty()
    }
}
