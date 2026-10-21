package bidvector.app.wiring

import bidvector.adapters.access.BcryptPassphraseHasher
import bidvector.adapters.access.BrowserAccessTransaction
import bidvector.adapters.access.JdbcBrowserAccessTransaction
import bidvector.adapters.access.SecureBrowserSessionKeySource
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.sharedkernel.Resolution
import bidvector.workflow.access.BROWSER_ACCESS_POLICY
import bidvector.workflow.access.BrowserAccessPolicyData
import bidvector.workflow.strategy.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 브라우저 접근 경로 조립(M7/7D) — 이 조립이 없으면 세션 endpoint 와 쿠키 인증 경로가 함께
 * 사라진다(rollback 의 「비활성화 경로」: [browserAccessExecutor] 빈을 빼면 세션 쪽 전부가
 * 뜨지 않고 기존 운영자 자격 헤더 경로는 그대로 남는다 — 무회귀).
 *
 * 이 클래스는 도메인 값을 만들지 않는다 — 정책을 해소하고 어댑터를 생성자에 꽂을 뿐이고,
 * 해소되지 않으면 지어내지 않고 기동을 실패시킨다(`PersistenceWiring`·`StrategyEditWiring` 과
 * 같은 규율). 포트를 이름으로 부르지도 않는다: 트랜잭션 경계 안의 use case 조립은 어댑터 층
 * ([JdbcBrowserAccessTransaction])이 진다.
 *
 * **쿠키 쓰기 빈이 이 배선에 없는 이유**: 그 타입은 `app.http` 에 살고, 이 패키지가 그것을
 * 이름 부르면 `app.http` ↔ `app.wiring` 패키지 순환이 생긴다(`ArchitectureGateTest` 가 실측으로
 * 그것을 잡았다). 조립 근(`BidVectorApplication`, 패키지 `bidvector.app`)이 이미 `app.http` 를
 * 한 방향으로만 참조하므로 그 자리에 둔다.
 */
@Configuration
open class BrowserAccessWiring {
    @Bean
    open fun resolvedBrowserAccessPolicy(): BrowserAccessPolicyData {
        val resolution = BROWSER_ACCESS_POLICY.resolve(LocalDate.now())
        return (resolution as? Resolution.Resolved<BrowserAccessPolicyData>)?.value
            ?: error("브라우저 접근 정책이 해소되지 않았다: $resolution")
    }

    /**
     * 요청 경로의 경계 — `StrategyEditWiring` 이 만든 [TransactionBoundary](런타임 풀,
     * 최소 권한 역할)를 그대로 받는다. 운영자 명령의 소유자 연결과 **다른 경계**다.
     *
     * `DataSource` 를 함께 받는 이유는 로그인 경로다(code-review r1 M-2) — 그 경로는 느린
     * KDF 검증이 커넥션을 쥐지 않도록 문장마다 제 트랜잭션을 열고, 그 공급원은 경계가 아니라
     * 풀 자신이어야 한다. **같은 풀**이다(`PersistenceWiring` 의 빈 하나) — 역할·한도가 갈리지
     * 않는다.
     */
    @Bean
    open fun browserAccessTransaction(
        transactionBoundary: TransactionBoundary,
        dataSource: DataSource,
        clock: Clock,
        resolvedBrowserAccessPolicy: BrowserAccessPolicyData,
    ): BrowserAccessTransaction =
        JdbcBrowserAccessTransaction(
            transactions = transactionBoundary,
            dataSource = dataSource,
            hasher = BcryptPassphraseHasher(resolvedBrowserAccessPolicy.hashCost),
            keys = SecureBrowserSessionKeySource(),
            clock = clock,
            policy = resolvedBrowserAccessPolicy,
        )

    @Bean
    open fun browserAccessExecutor(browserAccessTransaction: BrowserAccessTransaction): BrowserAccessExecutor =
        BrowserAccessExecutor(browserAccessTransaction)
}
