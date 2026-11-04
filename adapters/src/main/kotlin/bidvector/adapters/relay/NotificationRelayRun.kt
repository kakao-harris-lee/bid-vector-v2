package bidvector.adapters.relay

import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.event.JdbcInboxPort
import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.event.PostgresAdvisoryLockLease
import bidvector.adapters.notification.ConfiguredRouteDirectory
import bidvector.adapters.notification.JdbcNoticeNotificationFacts
import bidvector.adapters.notification.NoticeMessageRenderer
import bidvector.adapters.notification.NotificationChannelSettings
import bidvector.adapters.notification.TelegramNotificationSender
import bidvector.adapters.notification.telegramHttpClient
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.notification.DispatchNotification
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RelayOutboxNotifications
import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelayTarget
import bidvector.workflow.notification.RuntimeEnvironment
import java.time.Clock
import javax.sql.DataSource

/**
 * relay 의 **조립 경계**(선례 `JdbcStrategyEditTransaction`) — 호출부는 [relay] 하나만 받고,
 * 그 안에서 쓰는 outbox·inbox·lease·dispatcher 는 이 경계 밖으로 나가지 않는다.
 *
 * **왜 `app` 이 아니라 여기인가.** 이 조립은 `JdbcOutboxPort` 를 이름으로 부른다. `app`
 * production 은 outbox 쓰기 타입을 참조하지 못한다(D-6A3-17(a)③ 게이트, dry-run effect 0) —
 * 그 게이트를 넓히는 대신 조립을 어댑터 층에 둔다.
 *
 * **왜 `adapters.event` 가 아니라 새 패키지인가**(D-6F10-18 ①). 이 조립은
 * `workflow.notification`(dispatcher·정책·환경)을 이름으로 부르는데, `EventAdapterDependencyTest`
 * 는 그 좌표를 허용 루트 밖으로 두고 **양성 대조로 명시 단언**한다 — codec 이 notification
 * 타입을 보지 않는다는 것이 그 게이트의 뜻이다. 그 단언을 지우는 대신 간선을 이 패키지 안에
 * 가둔다(자기 의존 test `RelayAdapterDependencyTest` 가 경계를 진다).
 *
 * **발송 축 셋은 `bidvector.adapters.notification` 에 산다**(M9/STR12 — `OPEN-STR-12` 발송 축).
 * 6F-10 이 둔 자리지킴 셋(`Unavailable*`)을 실 구현이 대체했고 그 셋은 함께 사라졌다. 그 패키지를
 * 따로 둔 이유는 **이 파일의 허용 루트가 말하는 것이 「조립이 보는 것」**이기 때문이다 — 거기에
 * HTTP 전송과 공고 표 읽기를 더하면 그 등식이 무엇을 뜻하는지 흐려진다.
 *
 * **경계 인스턴스가 하나다.** [TransactionBoundary] 를 outbox·inbox·[ConsumerTransactions]
 * 셋에 **같은 객체로** 넘긴다 — 다른 객체를 넘기면 relay 가 연 경계 안에서 port 가 자기
 * 경계를 못 찾아 `withConnection` 이 실행 시점에 던진다. lease 는 **의도적으로 다른 연결**
 * (`DataSource` 직접)이다: advisory lock 은 세션 범위라 트랜잭션이 끝나도 쥐고 있어야 한다.
 * 렌더러의 연결도 **의도적으로 다르다** — 렌더링은 relay 의 T1·T2 **사이**(경계 밖)에서 일어난다.
 */
class NotificationRelayRun(
    dataSource: DataSource,
    target: RelayTarget,
    environment: RuntimeEnvironment,
    policy: NotificationDeliveryPolicyData,
    channels: NotificationChannelSettings,
    clock: Clock,
) {
    private val boundary = TransactionBoundary(dataSource)

    private val relay =
        RelayOutboxNotifications(
            outbox = JdbcOutboxPort(boundary),
            inbox = JdbcInboxPort(boundary),
            dispatcher = dispatcherFor(dataSource, target, environment, policy, channels, clock),
            leases = PostgresAdvisoryLockLease(dataSource),
            transactions = ConsumerTransactions(boundary),
            target = target,
            environment = environment,
            policy = policy,
        )

    fun relay(limit: Int): RelayReport = relay.relay(limit)
}

/**
 * 발송 축 셋의 배선 — route 는 설정의 owner·슬러그에서, 본문은 공고 표에서, 발송은 Telegram Bot
 * API 로. [RelayTarget.owner] 가 route 조회 키와 **같은 값**이어야 하므로 두 자리에 같은 값을
 * 넣지 않고 하나를 흘려 보낸다.
 */
private fun dispatcherFor(
    dataSource: DataSource,
    target: RelayTarget,
    environment: RuntimeEnvironment,
    policy: NotificationDeliveryPolicyData,
    channels: NotificationChannelSettings,
    clock: Clock,
): DispatchNotification =
    DispatchNotification(
        routes = ConfiguredRouteDirectory(target.owner, channels.telegram.routeKey),
        renderer = NoticeMessageRenderer(JdbcNoticeNotificationFacts(dataSource), channels.message),
        sender =
            TelegramNotificationSender(
                settings = channels.telegram,
                httpClient = telegramHttpClient(channels.telegram.connectTimeout),
                policy = policy,
                clock = clock,
            ),
        policyData = policy,
        environment = environment,
    )
