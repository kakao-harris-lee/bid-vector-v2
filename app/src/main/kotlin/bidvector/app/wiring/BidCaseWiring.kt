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
    @Bean
    open fun bidCaseWorkflow(
        bidCaseStore: BidCaseStorePort,
        noticeDeadlineQuery: NoticeDeadlineQueryPort,
        noticeSnapshotQuery: NoticeSnapshotQueryPort,
        clock: Clock,
        resolvedEvidenceRetentionPolicy: Resolution.Resolved<EvidenceRetentionPolicyData>,
    ): BidCaseWorkflow =
        BidCaseWorkflow(
            cases = bidCaseStore,
            deadlines = noticeDeadlineQuery,
            snapshots = noticeSnapshotQuery,
            clock = clock,
            retentionPolicy = resolvedEvidenceRetentionPolicy,
        )
}
