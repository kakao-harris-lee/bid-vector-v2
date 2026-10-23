package bidvector.app.wiring

import bidvector.procurement.NoticeId
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.MarkReadOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPage
import bidvector.workflow.notificationinbox.NotificationInboxStore
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationPageRequest
import bidvector.workflow.strategy.Clock

/**
 * 알림함 조회·읽음 처리의 **제한 층**(M7/7C③) — controller 가 받는 유일한 협력자다.
 *
 * 7C① 의 `NoticeSearchQuery` 와 같은 자리·같은 사유: controller 가 저장 포트를 **직접 받지
 * 못하게** 한다(`app.http.denied-types` 가 포트 타입을 그 층에서 막고, 아키텍처 게이트의 포트
 * 호출 쌍 허용 목록이 「누가 그 포트의 메서드를 부를 수 있는가」를 이 class 로 좁힌다).
 *
 * **읽음 처리의 시각을 controller 가 정하지 않는다** — [clock] 이 낸다. 시각을 HTTP 층에서
 * 받으면 요청자가 「언제 읽었는지」를 말할 수 있고, 그 값은 저장 층의 `read_at >= created_at`
 * CHECK 를 밀어 보려는 입력이 된다. 요청 본문에 시각 칸이 **없는** 것과 짝이다.
 *
 * **쓰기 포트를 받지 않는다**: 이 class 가 아는 것은 [NotificationInboxStore](목록·읽음)뿐이고
 * 알림을 **기록**하는 포트는 app 조립의 어느 자리에도 꽂히지 않는다.
 */
class NotificationInboxQuery(
    private val store: NotificationInboxStore,
    private val clock: Clock,
) {
    fun list(
        account: AccountId,
        request: NotificationPageRequest,
    ): NotificationInboxPage = store.list(account, request)

    fun markRead(
        account: AccountId,
        item: NotificationItemKey,
    ): MarkReadOutcome = store.markRead(account, item, clock.now())

    fun markNoticeRead(
        account: AccountId,
        notice: NoticeId,
    ): MarkReadOutcome = store.markNoticeRead(account, notice, clock.now())
}
