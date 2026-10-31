package bidvector.app.wiring

import bidvector.adapters.bidding.JdbcBidCaseStore
import bidvector.adapters.bidding.JdbcNoticeDeadlineQuery
import bidvector.adapters.bidding.JdbcNoticeSnapshotQuery
import bidvector.sharedkernel.Resolution
import bidvector.workflow.bidding.BidCaseStorePort
import bidvector.workflow.bidding.BidCaseWorkflow
import bidvector.workflow.bidding.EVIDENCE_RETENTION_POLICY
import bidvector.workflow.bidding.EvidenceRetentionPolicyData
import bidvector.workflow.bidding.NoticeDeadlineQueryPort
import bidvector.workflow.bidding.NoticeSnapshotQueryPort
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.strategy.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 입찰 건 경로 조립(M7/7B) — 이 조립이 없으면 입찰함 endpoint 가 뜨지 않는다.
 *
 * `PersistenceWiring` 의 `DataSource` 빈을 그대로 받는다(migration 은 그쪽이 기동 시 1회
 * 돌린다). 정책은 **조립에서 한 번 해소**하고, 해소되지 않으면 지어내지 않고 기동을
 * 실패시킨다(`resolvedStrategyPolicy` 와 같은 규율) — 보관 기한을 모르는 채 증빙 참조를
 * 받기 시작하면 R-5 를 지킬 수 없다.
 *
 * **알림 쓰기 포트를 받는다**(M7/7C③-b) — 결과 전이를 알림함에 되비추는 자리가 use case 안이고,
 * 그 포트의 빈은 `NotificationInboxWiring` 하나다. controller 는 그 타입을 받을 수 없다
 * (`app.http.denied-types`) — 이 조립이 use case 하나만 내주는 기존 규율이 그대로 선다.
 */
@Configuration
open class BidCaseWiring {
    @Bean
    open fun resolvedEvidenceRetentionPolicy(): Resolution.Resolved<EvidenceRetentionPolicyData> {
        val resolution = EVIDENCE_RETENTION_POLICY.resolve(LocalDate.now())
        return resolution as? Resolution.Resolved<EvidenceRetentionPolicyData>
            ?: error("증빙 보관 정책이 해소되지 않았다: $resolution")
    }

    @Bean
    open fun bidCaseStore(dataSource: DataSource): BidCaseStorePort = JdbcBidCaseStore(dataSource)

    @Bean
    open fun noticeDeadlineQuery(dataSource: DataSource): NoticeDeadlineQueryPort = JdbcNoticeDeadlineQuery(dataSource)

    @Bean
    open fun noticeSnapshotQuery(dataSource: DataSource): NoticeSnapshotQueryPort = JdbcNoticeSnapshotQuery(dataSource)

    /**
     * controller 가 받는 **단 하나의 협력자**다 — 저장소 · `ConnectionSource` 를 돌려주는
     * 공개 경로가 없다(`StrategyEditExecutor` 와 같은 규율). 그래서 `app.http` 는 전이
     * use case 밖에서 건 상태를 바꿀 방법이 없다((2b) 값 획득 축).
     */
    @Suppress("LongParameterList")
    @Bean
    open fun bidCaseWorkflow(
        bidCaseStore: BidCaseStorePort,
        noticeDeadlineQuery: NoticeDeadlineQueryPort,
        noticeSnapshotQuery: NoticeSnapshotQueryPort,
        clock: Clock,
        resolvedEvidenceRetentionPolicy: Resolution.Resolved<EvidenceRetentionPolicyData>,
        notificationInboxPort: NotificationInboxPort,
    ): BidCaseWorkflow =
        BidCaseWorkflow(
            cases = bidCaseStore,
            deadlines = noticeDeadlineQuery,
            snapshots = noticeSnapshotQuery,
            clock = clock,
            retentionPolicy = resolvedEvidenceRetentionPolicy,
            // 쓰기 빈은 `NotificationInboxWiring` 한 자리다 — 같은 어댑터를 여기서 다시 짓지 않는다.
            notifications = notificationInboxPort,
        )
}
