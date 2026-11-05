package bidvector.app.wiring

import bidvector.adapters.relay.NotificationRelayRun
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.relay.NotificationChannelProperties
import bidvector.app.relay.NotificationRelayRunner
import bidvector.app.relay.RelayBootDecision
import bidvector.app.relay.relayBootDecision
import bidvector.sharedkernel.Resolution
import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.NOTIFICATION_DELIVERY_POLICY
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RelayTarget
import bidvector.workflow.notification.RuntimeEnvironment
import bidvector.workflow.strategy.OperatorId
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.LocalDate
import javax.sql.DataSource

/**
 * relay 러너의 입력(D-6F10-20) — **기본값이 하나도 없다.** 미설정은 Spring relaxed binding 의
 * `BindException`(기동 실패)이다: 환경·소유자·채널·상한 가운데 어느 하나를 지어내면 그
 * 값이 곧 발송 대상·억제 판정을 바꾼다.
 *
 * [owner] 가 설정에서 오는 이유(D-6F10-18 ⑦): `OperatorProfilePort.current()` 가 돌려주는
 * `ProfileFacts` 에는 `OperatorId` 가 **없다**(실측). 그리고 owner 는 `RouteDirectory.
 * routesFor(owner)` 의 입력이라 route 설정과 같은 축에서 오는 것이 정직하다.
 */
@ConfigurationProperties(prefix = "bidvector.relay")
data class RelayProperties(
    val environment: RuntimeEnvironment,
    val owner: String,
    val channel: Channel,
    val claimLimit: Int,
)

/**
 * 일회성 relay 배선(D-6F10-18 ⑧) — **`bidvector.relay.mode=once` 일 때만** 이 설정 전체가
 * 올라온다. 속성이 없으면(기본) 러너도, 조립도, 임대 어댑터 생성도 없다 — 평가 endpoint 만
 * 쓰는 배포는 relay 설정 없이 그대로 뜬다(compose 는 `*.mode` 를 설정하지 않는다, D-6A2a-5).
 *
 * **발송 가능(`DeliveryMode.Live`) 모드 환경인데 알림 채널 설정이 비면 기동을 거부한다**
 * (M9/STR12 D-7 — 앞 판의 D-6F10-20 「Live 면 무조건 거부」를 승계해 좁혔다).
 * 그 모드로 뜨면 relay 가 claim 한 뒤 보낼 곳이 없어 그 행들이 어휘 밖 종단으로 타 버린다 —
 * 종단 전이는 단방향이라 되돌릴 간선이 없다. 그래서 「Live 인데 채널 설정 결손」을 **설정
 * 오류**로 만든다: 러너가 돌기 전에 실패해야 claim 이 0 이다.
 *
 * **Live 가 아니면 설정 없이 뜬다**(D-7) — 그 환경의 relay 는 claim 조차 하지 않는다(환경 억제).
 *
 * 검사가 보는 것은 **환경 이름이 아니라 정책표가 그 환경에 붙인 모드**다 — 그 판정은
 * [relayBootDecision] 이 값으로 내고(cr R-2), 이 자리는 그 값을 기동 실패로 옮긴다.
 *
 * 이 클래스가 조립을 직접 하지 않는 이유: outbox 쓰기 타입을 `app` 이 이름으로 볼 수 없다
 * (D-6A3-17(a)③). [NotificationRelayRun](어댑터 조립 경계)만 든다.
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.relay", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(RelayProperties::class, NotificationChannelProperties::class)
open class RelayWiring {
    @Bean
    open fun notificationRelayRun(
        dataSource: DataSource,
        properties: RelayProperties,
        channelProperties: NotificationChannelProperties,
    ): NotificationRelayRun {
        val policy = resolvedNotificationPolicy()
        // 판정은 [relayBootDecision] 이 **값으로** 낸다(cr R-2) — 여기 인라인되어 있던 동안은
        // 정책표를 바꿔 넣은 변이로 직접 칠 수 없었다. 이 자리의 몫은 그 값을 기동 실패로
        // 옮기는 것뿐이다. 판정이 받는 것은 설정의 **값이 아니라 존재 여부**라 이 메시지가
        // 자격·chat id 원문을 담을 경로가 없다.
        val decision = relayBootDecision(properties.environment, policy, channelProperties.presence())
        require(decision is RelayBootDecision.Allowed) {
            "발송 가능(Live) 모드 환경인데 알림 채널 설정이 비었다 — 채워야 하는 칸: $decision"
        }
        require(properties.owner.isNotBlank()) { "bidvector.relay.owner 는 빈 문자열일 수 없다" }
        require(properties.claimLimit > 0) { "bidvector.relay.claim-limit 는 1 이상이어야 한다" }
        return NotificationRelayRun(
            dataSource = dataSource,
            target = RelayTarget(OperatorId(properties.owner), properties.channel),
            environment = properties.environment,
            policy = policy,
            channels = channelProperties.channelSettings(),
            clock = Clock.systemUTC(),
        )
    }

    @Bean
    open fun notificationRelayRunner(
        run: NotificationRelayRun,
        properties: RelayProperties,
        termination: CollectionTermination,
    ): NotificationRelayRunner {
        val logger = LoggerFactory.getLogger(NotificationRelayRunner::class.java)
        return NotificationRelayRunner(
            run = run,
            limit = properties.claimLimit,
            log = CollectionLog { logger.info(it) },
            termination = termination,
        )
    }

    /**
     * 해소 실패는 기동 실패다 — 값을 지어내지 않는다(`EvaluationWiring.licenseGatePort` 와
     * 같은 규율). **클래스 멤버**로 둔다: 파일 top-level 로 두면 `RelayWiringKt` 파일 facade
     * 클래스가 생기고, 그 클래스는 어느 조립 층에도 속하지 않아 제한 층의 허용 목록 밖
     * 참조가 된다(등재를 하나 늘리는 대신 facade 자체를 만들지 않는다).
     */
    private fun resolvedNotificationPolicy(): NotificationDeliveryPolicyData {
        val resolution = NOTIFICATION_DELIVERY_POLICY.resolve(LocalDate.now())
        val resolved =
            resolution as? Resolution.Resolved<NotificationDeliveryPolicyData>
                ?: error("알림 배달 정책이 해소되지 않았다: $resolution")
        return resolved.value
    }
}
