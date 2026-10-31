package bidvector.app.wiring

import bidvector.adapters.notificationinbox.JdbcNotificationInbox
import bidvector.adapters.notificationinbox.JdbcNotificationInboxStore
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import bidvector.workflow.notificationinbox.NotificationInboxStore
import bidvector.workflow.strategy.Clock
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * 알림함 경로의 조립(M7/7C③) — `PersistenceWiring` 의 `DataSource` 빈을 그대로 받는다
 * (migration 은 그쪽이 기동 시 1회 돌린다). 이 class 는 도메인 값을 만들지 않고 어댑터를
 * 생성자에 꽂을 뿐이다(7C① 의 `NoticeSearchWiring` 과 같은 형태).
 *
 * **쓰기 빈은 여기 한 자리다**(M7/7C③-b · D-7C3B-2). 7C③ 은 이 빈을 **만들지 않는 것**으로
 * 「HTTP 로 알림을 위조한다」를 막았다 — 트리거가 하나도 없던 동안에는 그것이 가장 좁은 선택이었다.
 * 트리거가 생기면서 빈이 필요해졌고, 그 자리를 **원천 slice 마다 따로 꽂게 하면** 같은 어댑터가
 * 여러 조립에서 생긴다. 그래서 정의는 하나로 두고, 막는 일은 **게이트가 진다**:
 * `app.http.denied-types` 에 이 포트 타입이 있어 어떤 controller 도 그것을 생성자로 받을 수 없고,
 * `app.port-call.allowed-pairs` 가 이 포트를 **부르는 자리**를 (호출자, 메서드) 쌍으로 전수 고정한다.
 * 어댑터는 class 로도 갈려 있다(`JdbcNotificationInbox` = 쓰기 · `JdbcNotificationInboxStore` = 읽기).
 *
 * **잃은 알림을 이 자리가 적는다**(D-7C3B-9). 원천 쓰기는 알림 기록 실패로 되돌아가지 않으므로
 * (`BidCaseWorkflow.reflect`) 그 실패가 값으로만 남으면 아무 데도 보이지 않는다 — workflow 층은
 * 로거를 들지 않으니 **조립이 그 표지를 진다**. 줄에는 종류와 공고 번호만 싣는다: 계정 주체를
 * 적으면 감사 주체 라벨을 만드는 자리가 둘이 되어 구조 등식이 붉어지고, 계수만으로 「무엇이
 * 몇 건」이 보인다.
 *
 * **비활성화 경로**: [notificationInboxQuery] 빈을 빼면 알림함 경로 전체가 사라지고(컨트롤러가
 * 조회기를 요구하므로 기동이 실패한다) 전략·검색·세션 경로는 그대로 남는다 — 다른 Wiring 과
 * 같은 형태의 끄는 자리다.
 */
@Configuration
open class NotificationInboxWiring {
    @Bean
    open fun notificationInboxStore(dataSource: DataSource): NotificationInboxStore =
        JdbcNotificationInboxStore(dataSource)

    /**
     * 쓰기 포트 — 원천 slice 들의 조립이 이 빈을 받는다(건 전이 · 투찰가 요청 · 훑기 러너).
     *
     * **비활성화 경로**: 이 빈을 빼면 트리거 배선 전체가 사라진다(세 자리가 모두 이 타입을
     * 요구하므로 기동이 실패한다) — 알림함 읽기 경로와 다른 축이고 따로 끌 수 있다.
     */
    @Bean
    open fun notificationInboxPort(dataSource: DataSource): NotificationInboxPort {
        val adapter = JdbcNotificationInbox(dataSource)
        val logger = LoggerFactory.getLogger(NotificationInboxWiring::class.java)
        return NotificationInboxPort { record ->
            adapter.record(record).also { outcome ->
                if (outcome == NotificationInboxOutcome.Failed) logger.warn(inboxWriteFailedLine(record))
            }
        }
    }

    @Bean
    open fun notificationInboxQuery(
        notificationInboxStore: NotificationInboxStore,
        clock: Clock,
    ): NotificationInboxQuery = NotificationInboxQuery(notificationInboxStore, clock)
}

/**
 * 잃은 알림 한 건의 표지 — **종류와 공고 번호만**이다. 계정 주체도, 저장소 예외 메시지도 싣지
 * 않는다(후자에는 행 값이 실릴 수 있다 — 러너들의 `causeCodeOf` 와 같은 판단).
 */
internal fun inboxWriteFailedLine(record: NotificationInboxRecord): String =
    "notification-inbox write failed kind=${record.fact.kind.name} " +
        "notice=${record.notice.number.value}/${record.notice.round.value}"
