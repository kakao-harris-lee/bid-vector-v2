package bidvector.app.wiring

import bidvector.adapters.notificationinbox.JdbcNotificationSweepCandidates
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.notificationinbox.NotificationSweepRunner
import bidvector.workflow.bidding.NOTIFICATION_CALENDAR_ZONE
import bidvector.workflow.bidding.NOTIFICATION_WINDOW_POLICY
import bidvector.workflow.bidding.NotificationWindowPolicyData
import bidvector.workflow.bidding.SweepNotifications
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.strategy.Clock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 일회성 알림 훑기 배선(M7/7C③-b · D-7C3B-8) — **`bidvector.notification-sweep.mode=once` 일 때만**
 * 이 설정 전체가 올라온다. 속성이 없으면(기본) 러너도, 후보 질의도 서지 않는다.
 * **끄는 법이 그 한 줄이다**(rollback.md 가 이 경로를 적는다).
 *
 * compose 는 `*.mode` 를 설정하지 않는다(D-6A2a-5) — 그래서 **출하 조립의 기본값은 「훑기가 꺼진
 * 상태」**다. 운영 host 의 cron 등록은 이 slice 밖이고(D-7C3B-8) runbook 이 항목만 적는다.
 *
 * **새 상주 스케줄러가 아니다.** 이 저장소에 상주 스케줄러는 없고(`OPEN-6F10-SCHEDULER`) 주기는
 * 외부 cron 이 진다 — 수집·relay·평가 커밋·보존이 쓰는 것과 **같은 장치의 아홉째**다.
 *
 * 창 정책은 **조립에서 한 번 해소**하고, 해소되지 않으면 지어내지 않고 기동을 실패시킨다
 * (`resolvedEvidenceRetentionPolicy` 와 같은 규율) — 창을 모르는 채 알림을 쓰기 시작하면
 * 「며칠 전에 알렸는가」가 run 마다 달라진다.
 *
 * **KONEPS 를 부르지 않는다** — 서비스 키 설정을 요구하지 않는다(보존·승격 배선과 같은 성질).
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.notification-sweep", name = ["mode"], havingValue = "once")
open class NotificationSweepWiring {
    @Bean
    open fun resolvedNotificationWindowPolicy(clock: Clock): NotificationWindowPolicyData {
        // 정책 해소도 **주입된 시계**로 한다 — 러너가 시계를 받으면서 정책만 시스템 시계로
        // 고르면 그 둘이 갈리는 날이 장부 밖이다(보존 배선의 cr L-4 와 같은 자리).
        val referenceDate = notificationPolicyReferenceDate(clock.now())
        return resolvedOrFail(NOTIFICATION_WINDOW_POLICY.resolve(referenceDate), "알림 창 정책")
    }

    /**
     * 러너가 받는 **단 하나의 협력자**다 — 후보 질의·알림 포트를 돌려주는 공개 경로가 없다
     * (`BidCaseWiring` 의 「controller 는 use case 만 받는다」와 같은 규율).
     */
    @Bean
    open fun notificationSweepRunner(
        dataSource: DataSource,
        notificationInboxPort: NotificationInboxPort,
        clock: Clock,
        resolvedNotificationWindowPolicy: NotificationWindowPolicyData,
        termination: CollectionTermination,
    ): NotificationSweepRunner {
        val logger = LoggerFactory.getLogger(NotificationSweepRunner::class.java)
        val sweep =
            SweepNotifications(
                candidates = JdbcNotificationSweepCandidates(dataSource),
                notifications = notificationInboxPort,
                clock = clock,
                policy = resolvedNotificationWindowPolicy,
            )
        return NotificationSweepRunner(sweep, CollectionLog { logger.info(it) }, termination)
    }
}

/**
 * 창 정책을 고르는 **기준일** — 구역이 창을 세는 달력과 **같다**(v L-3).
 *
 * UTC 로 고르면 KST 00~09 시에 **어제 날짜**로 정책을 해소하고, 그러면 효력일이 바뀌는 날 판정
 * 달력과 정책 달력이 하루 어긋난다(체크리스트가 같은 함정을 자기 구역 상수로 막은 자리와 같다).
 *
 * 함수로 꺼낸 이유는 **경계를 잴 수 있게** 하기 위해서다 — 배선 안의 지역 변수로 두면 그 하루
 * 어긋남이 어떤 test 도 지나지 않는다(출하 정책 표의 항목이 하나라 값으로는 관측되지 않는다).
 */
internal fun notificationPolicyReferenceDate(now: Instant): LocalDate =
    LocalDate.ofInstant(now, NOTIFICATION_CALENDAR_ZONE)
