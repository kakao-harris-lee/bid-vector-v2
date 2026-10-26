package bidvector.app.http

import bidvector.app.wiring.NotificationInboxQuery
import bidvector.procurement.NoticeId
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.MarkReadOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPage
import bidvector.workflow.notificationinbox.NotificationInboxStore
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationPageRequest
import bidvector.workflow.strategy.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant

/**
 * 알림함 HTTP 표면의 test 조립(M7/7C③).
 *
 * **왜 `HttpTestSupport.kt` 가 아니라 자기 파일인가**: 그 파일은 **정확히 500줄**이라 한 줄만
 * 더해도 `sizeGate` 가 문다(팀장 2026-10-26). 조립을 더할 때는 자기 파일로 옮기라는 지시를
 * 그대로 따른다. `@Configuration` 이 `bidvector.app.http` 에 있으므로 `HttpTestApplication` 의
 * 기본 컴포넌트 스캔(그 패키지와 하위)이 집어 간다.
 *
 * **무엇을 고치는 조립인가**: `NotificationInboxController` 는 main 소스의
 * `bidvector.app.http` 에 있어 **스캔으로 자동 등록**되는데, 그 협력자
 * [NotificationInboxQuery] 는 `bidvector.app.wiring` 이라 **스캔 범위 밖**이다. 그래서 이 빈이
 * 없으면 컨텍스트가 **기동 자체에 실패**하고, 같은 컨텍스트를 쓰는 **남의 test 까지**
 * 줄줄이 무너진다(7B 의 `AuthenticatedAccountTest`·`AccountBindingGuardScanTest` 여덟이 실제로
 * 그렇게 떨어졌다 — 그들의 결함이 아니라 이 조립의 부재가 원인이었다).
 *
 * 저장소에 닿지 않는 **fake** 를 꽂는다. 알림함의 거동 자체는 실 DB test 가 재고(어댑터 쪽),
 * 여기서 필요한 것은 「컨트롤러가 받을 협력자가 있다」뿐이다.
 */
@Configuration
open class NotificationInboxTestWiring {
    @Bean
    open fun fakeNotificationInboxStore(): FakeNotificationInboxStore = FakeNotificationInboxStore()

    @Bean
    open fun notificationInboxQuery(
        fakeNotificationInboxStore: FakeNotificationInboxStore,
        clock: Clock,
    ): NotificationInboxQuery = NotificationInboxQuery(fakeNotificationInboxStore, clock)
}

/**
 * 저장소 없는 알림함 — **빈 목록과 0 건 처리**를 낸다.
 *
 * 값을 지어내지 않는다: 이 fake 가 항목을 만들어 내면 HTTP 표면 test 가 「목록이 이렇게
 * 보인다」를 재는 것처럼 보이지만 그 모양의 근거는 어댑터에 있다. 여기서 재는 것은 **표면**
 * (상태 코드 · 헤더 · 키 집합 · 인증)이므로 비어 있는 답이 정직하다.
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
