package bidvector.app.wiring

import bidvector.adapters.notice.JdbcNoticeSearch
import bidvector.workflow.notice.NoticeSearchPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * 공고 검색 경로의 조립 — `PersistenceWiring` 의 `DataSource` 빈을 그대로 받는다(migration
 * 은 그쪽이 기동 시 1회 돌린다). 이 클래스는 도메인 값을 만들지 않고 어댑터를 생성자에
 * 꽂을 뿐이다.
 *
 * **비활성화 경로**: [noticeSearchQuery] 빈을 빼면 검색 경로 전체가 사라지고(컨트롤러가
 * 조회기를 요구하므로 기동이 실패한다) 전략 조회·편집·dry-run 은 그대로 남는다 —
 * `StrategyEditWiring` 과 같은 형태의 끄는 자리다.
 */
@Configuration
open class NoticeSearchWiring {
    @Bean
    open fun noticeSearchPort(dataSource: DataSource): NoticeSearchPort = JdbcNoticeSearch(dataSource)

    @Bean
    open fun noticeSearchQuery(noticeSearchPort: NoticeSearchPort): NoticeSearchQuery =
        NoticeSearchQuery(noticeSearchPort)
}
