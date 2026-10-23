package bidvector.app.wiring

import bidvector.adapters.opening.JdbcOpeningResultView
import bidvector.workflow.opening.OPENING_PROGRESS_POLICY
import bidvector.workflow.opening.OpeningResultFactsPort
import bidvector.workflow.opening.ReadOpeningResult
import bidvector.workflow.strategy.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * 개찰 결과 조회 경로의 조립(M7/7C⑤-a) — `PersistenceWiring` 의 `DataSource` 와 전역 `Clock`
 * 빈을 그대로 받는다. 이 클래스는 도메인 값을 만들지 않고 어댑터·정책을 생성자에 꽂을 뿐이다.
 *
 * **정책 값이 여기서 주입된다**([OPENING_PROGRESS_POLICY]) — 판정 함수가 정책을 전역에서 읽지
 * 않으므로 test 가 다른 라벨 집합으로 같은 판정을 잴 수 있다.
 *
 * **비활성화 경로**: [openingResultQuery] 빈을 빼면 이 경로 전체가 사라지고(컨트롤러가 조회기를
 * 요구하므로 기동이 실패한다) 다른 조회·편집 경로는 그대로 남는다 — `NoticeSearchWiring` 과
 * 같은 형태의 끄는 자리다.
 */
@Configuration
open class OpeningResultWiring {
    @Bean
    open fun openingResultFactsPort(dataSource: DataSource): OpeningResultFactsPort = JdbcOpeningResultView(dataSource)

    @Bean
    open fun readOpeningResult(
        openingResultFactsPort: OpeningResultFactsPort,
        clock: Clock,
    ): ReadOpeningResult = ReadOpeningResult(openingResultFactsPort, clock, OPENING_PROGRESS_POLICY)

    @Bean
    open fun openingResultQuery(readOpeningResult: ReadOpeningResult): OpeningResultQuery =
        OpeningResultQuery(readOpeningResult)
}
