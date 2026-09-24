package bidvector.adapters.evaluation

import bidvector.workflow.evaluation.NotificationRequest
import bidvector.workflow.evaluation.NotificationRequestOutcome
import bidvector.workflow.evaluation.NotificationRequestPort

/**
 * [NotificationRequestPort]의 dry-run 전용 구현(M6/6A-3+6F-3, D-6A3-3, 운영자 결정 2) —
 * 알림 요청을 메모리에 모을 뿐 outbox 에 쓰지 않는다(「알림 요청을 낳는 자리까지」가 아니라
 * 「낳았을 요청을 세는 자리까지」). 요청 스코프로 지어진다 — 한 dry-run 호출의 결과만
 * 담는다(다음 요청과 공유하지 않는다).
 *
 * `requested()`는 읽기 전용 스냅숏을 낸다 — 응답 조립(`EvaluationDryRunResponse.
 * wouldNotifyNoticeIds`)이 이 값에서 공고 ID 를 뽑는다.
 */
class RecordingNotificationRequestPort : NotificationRequestPort {
    private val recorded = mutableListOf<NotificationRequest>()

    override fun request(notification: NotificationRequest): NotificationRequestOutcome {
        recorded += notification
        return NotificationRequestOutcome.Requested
    }

    fun requested(): List<NotificationRequest> = recorded.toList()
}
