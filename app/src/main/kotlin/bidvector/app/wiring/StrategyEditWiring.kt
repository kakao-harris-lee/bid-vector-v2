package bidvector.app.wiring

import bidvector.adapters.persistence.TransactionBoundary
import bidvector.adapters.strategy.JdbcStrategyEditTransaction
import bidvector.adapters.strategy.StrategyEditTransaction
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyPolicyData
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.EDIT_SESSION_POLICY
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.StrategyRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 편집 쓰기 경로 조립(M6/6A-2b D-6A2b-3) — 이 조립이 없으면 편집 endpoint 자체가 뜨지
 * 않는다(scope.md 「비활성화 경로」: [strategyEditExecutor] 빈을 빼면 쓰기 경로 전체가
 * 사라지고 읽기·dry-run 은 남는다).
 *
 * 이 클래스는 도메인 값을 만들지 않는다 — 정책을 해소하고 어댑터를 생성자에 꽂을 뿐이고,
 * 해소되지 않으면 지어내지 않고 기동을 실패시킨다(`PersistenceWiring` 과 같은 규율).
 * 포트를 이름으로 부르지도 않는다: 트랜잭션 경계 안의 use case 조립은 어댑터 층
 * ([JdbcStrategyEditTransaction])이 진다.
 */
@Configuration
open class StrategyEditWiring {
    /**
     * 요청마다 여는 트랜잭션 경계 하나(생애주기 내내 재사용 — 경계 자신은 커넥션을 갖지
     * 않고 `inTransaction` 호출마다 연다). `PersistenceWiring` 의 `DataSource` 빈을 그대로
     * 받는다 — migration 은 그쪽이 이미 기동 시 1회 돌린다.
     */
    @Bean
    open fun transactionBoundary(dataSource: DataSource): TransactionBoundary = TransactionBoundary(dataSource)

    @Bean
    open fun strategyEditTransaction(
        transactionBoundary: TransactionBoundary,
        clock: Clock,
    ): StrategyEditTransaction =
        JdbcStrategyEditTransaction(
            transactions = transactionBoundary,
            strategyPolicy = resolvedStrategyPolicy(),
            sessionPolicy = resolvedSessionPolicy(),
            clock = clock,
        )

    @Bean
    open fun strategyEditExecutor(strategyEditTransaction: StrategyEditTransaction): StrategyEditExecutor =
        StrategyEditExecutor(strategyEditTransaction, resolvedStrategyPolicy())

    /** D-6A2b-8 — `app.http` 가 전략 포트를 직접 받지 않게 하는 읽기 전용 조회기. */
    @Bean
    open fun strategyQuery(strategyRepository: StrategyRepository): StrategyQuery = StrategyQuery(strategyRepository)
}

/**
 * 정책 해소는 `PersistenceWiring.strategyRepository` 와 **같은 형태**다(그 자리의 KDoc
 * D-6F1-6) — 어댑터가 정책 파일의 독자가 되지 않게 조립이 해소해 넘긴다. 해소 실패는
 * 기동 실패다(전략을 지어내지 않는다).
 */
private fun resolvedStrategyPolicy(): Resolution.Resolved<StrategyPolicyData> {
    val resolution = STRATEGY_POLICY.resolve(LocalDate.now())
    return resolution as? Resolution.Resolved<StrategyPolicyData> ?: error("전략 정책이 해소되지 않았다: $resolution")
}

private fun resolvedSessionPolicy(): EditSessionPolicyData {
    val resolution = EDIT_SESSION_POLICY.resolve(LocalDate.now())
    return (resolution as? Resolution.Resolved<EditSessionPolicyData>)?.value
        ?: error("편집 세션 정책이 해소되지 않았다: $resolution")
}
