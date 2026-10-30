package bidvector.app.wiring

import bidvector.adapters.result.JdbcAwardWinnerNameView
import bidvector.adapters.result.JdbcBatchOpeningFactsView
import bidvector.adapters.result.JdbcRecordedBidView
import bidvector.settlement.AWARD_MATCHING_POLICY
import bidvector.settlement.AwardMatchingPolicyData
import bidvector.sharedkernel.Resolution
import bidvector.workflow.opening.OPENING_PROGRESS_POLICY
import bidvector.workflow.profile.BusinessProfileStorePort
import bidvector.workflow.result.AwardWinnerNamePort
import bidvector.workflow.result.BatchOpeningFactsPort
import bidvector.workflow.result.ReadMyBidResults
import bidvector.workflow.result.RecordedBidReadPort
import bidvector.workflow.strategy.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 내 입찰 결과 조회 경로의 조립(M7/7C⑤-b) — `PersistenceWiring` 의 `DataSource` 와 전역 `Clock`
 * 빈을 그대로 받는다. 도메인 값을 만들지 않고 어댑터·정책을 생성자에 꽂을 뿐이다.
 *
 * **정책 둘이 여기서 주입된다** — 판정 함수가 정책을 전역에서 읽지 않으므로 test 가 다른 라벨
 * 집합·다른 법인 토큰 목록으로 같은 판정을 잴 수 있다.
 *
 * **비활성화 경로**: [readMyBidResults] 빈을 빼면 이 경로 전체가 사라지고(컨트롤러가 use case 를
 * 요구하므로 기동이 실패한다) 다른 조회·편집 경로는 그대로 남는다.
 */
@Configuration
open class MyBidResultWiring {
    @Bean
    open fun recordedBidReadPort(dataSource: DataSource): RecordedBidReadPort = JdbcRecordedBidView(dataSource)

    @Bean
    open fun batchOpeningFactsPort(dataSource: DataSource): BatchOpeningFactsPort =
        JdbcBatchOpeningFactsView(dataSource)

    @Bean
    open fun awardWinnerNamePort(dataSource: DataSource): AwardWinnerNamePort = JdbcAwardWinnerNameView(dataSource)

    /**
     * 상호 매칭 정책 — 기준일 축이 있는 정책이라 해소를 거친다(`resolvedStrategyPolicy` 관례).
     * 해소되지 않으면 **기동이 실패한다**: 토큰 목록 없이 정규화하면 법인 표기만 다른 상호가
     * 서로 다른 회사로 읽혀 조용한 오판이 된다.
     *
     * 기준일이 주입 `Clock` 이 아니라 `LocalDate.now()` 인 것은 `resolvedStrategyPolicy` 와 같은
     * 자리·같은 모양이다 — 기동 시점에 한 번 고르는 값이라 요청 시계와 축이 다르다.
     */
    @Bean
    open fun resolvedAwardMatchingPolicy(): Resolution.Resolved<AwardMatchingPolicyData> {
        val resolution = AWARD_MATCHING_POLICY.resolve(LocalDate.now())
        return resolution as? Resolution.Resolved<AwardMatchingPolicyData>
            ?: error("상호 매칭 정책이 해소되지 않았다: $resolution")
    }

    @Bean
    open fun readMyBidResults(
        recordedBidReadPort: RecordedBidReadPort,
        batchOpeningFactsPort: BatchOpeningFactsPort,
        awardWinnerNamePort: AwardWinnerNamePort,
        businessProfileStorePort: BusinessProfileStorePort,
        clock: Clock,
        resolvedAwardMatchingPolicy: Resolution.Resolved<AwardMatchingPolicyData>,
    ): ReadMyBidResults =
        ReadMyBidResults(
            bids = recordedBidReadPort,
            openingFacts = batchOpeningFactsPort,
            winnerNames = awardWinnerNamePort,
            profiles = businessProfileStorePort,
            clock = clock,
            openingPolicy = OPENING_PROGRESS_POLICY,
            matchingPolicy = resolvedAwardMatchingPolicy.value,
        )
}
