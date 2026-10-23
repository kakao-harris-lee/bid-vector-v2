package bidvector.app.wiring

import bidvector.adapters.notificationinbox.JdbcNotificationInbox
import bidvector.workflow.notificationinbox.NotificationInboxStore
import bidvector.workflow.strategy.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * 알림함 경로의 조립(M7/7C③) — `PersistenceWiring` 의 `DataSource` 빈을 그대로 받는다
 * (migration 은 그쪽이 기동 시 1회 돌린다). 이 class 는 도메인 값을 만들지 않고 어댑터를
 * 생성자에 꽂을 뿐이다(7C① 의 `NoticeSearchWiring` 과 같은 형태).
 *
 * **쓰기 포트의 빈을 만들지 않는다.** `JdbcNotificationInbox` 는 읽기와 쓰기 포트를 **둘 다**
 * 구현하지만, 여기서 노출하는 빈의 타입은 [NotificationInboxStore](목록·읽음) 하나다. 그래서
 * app 조립에 `NotificationInboxPort` 타입의 빈이 **존재하지 않고**, 어떤 controller 도 그것을
 * 주입받을 수 없다 — 「HTTP 로 알림을 위조한다」가 조립의 성질로 막힌다. 원천 slice 들이 그
 * 포트를 쓸 때는 **자기 조립 자리**에서 같은 어댑터를 그 타입으로 꽂는다(그 자리는 요청 경로가
 * 아니다).
 *
 * **비활성화 경로**: [notificationInboxQuery] 빈을 빼면 알림함 경로 전체가 사라지고(컨트롤러가
 * 조회기를 요구하므로 기동이 실패한다) 전략·검색·세션 경로는 그대로 남는다 — 다른 Wiring 과
 * 같은 형태의 끄는 자리다.
 */
@Configuration
open class NotificationInboxWiring {
    @Bean
    open fun notificationInboxStore(dataSource: DataSource): NotificationInboxStore =
        JdbcNotificationInbox(dataSource)

    @Bean
    open fun notificationInboxQuery(
        notificationInboxStore: NotificationInboxStore,
        clock: Clock,
    ): NotificationInboxQuery = NotificationInboxQuery(notificationInboxStore, clock)
}
