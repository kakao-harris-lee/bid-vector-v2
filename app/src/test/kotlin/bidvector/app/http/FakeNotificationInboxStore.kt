package bidvector.app.http

import bidvector.procurement.NoticeId
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.MarkReadOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPage
import bidvector.workflow.notificationinbox.NotificationInboxStore
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationPageRequest
import java.time.Instant

/**
 * 저장소 없는 알림함(M7/7C③) — **빈 목록과 0 건 처리**를 낸다. 꽂는 자리는
 * [HttpTestApplication] 의 `@Bean` 하나다.
 *
 * **`@Configuration` 으로 두지 않는다**(실측으로 걷어낸 길). 이 패키지에 둘째 `@Configuration`
 * 을 두면 출하 조립을 띄우는 E2E 의 스캔에도 걸려 같은 이름의 빈이 두 번 정의되고
 * `BeanDefinitionOverrideException` 으로 **E2E 여럿이 기동 실패**한다. 그래서 이 파일은 fake
 * **타입만** 들고, 빈 등록은 조립 자신이 한다.
 *
 * 값을 지어내지 않는다: 이 fake 가 항목을 만들어 내면 HTTP 표면 test 가 「목록이 이렇게
 * 보인다」를 재는 것처럼 보이지만 그 모양의 근거는 어댑터의 실 DB test 에 있다. 여기서 재는
 * 것은 **표면**(상태 코드 · 헤더 · 키 집합 · 인증)이므로 비어 있는 답이 정직하다.
 */
class FakeNotificationInboxStore : NotificationInboxStore {
    override fun list(
        account: AccountId,
        request: NotificationPageRequest,
    ): NotificationInboxPage = NotificationInboxPage(items = emptyList(), totalCount = 0L, unreadCount = 0L)

    override fun markRead(
        account: AccountId,
        item: NotificationItemKey,
        at: Instant,
    ): MarkReadOutcome = MarkReadOutcome(markedCount = 0, unreadCount = 0L)

    override fun markNoticeRead(
        account: AccountId,
        notice: NoticeId,
        at: Instant,
    ): MarkReadOutcome = MarkReadOutcome(markedCount = 0, unreadCount = 0L)
}
