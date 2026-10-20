package bidvector.app.wiring

import bidvector.workflow.notice.NoticeSearchCriteria
import bidvector.workflow.notice.NoticeSearchPage
import bidvector.workflow.notice.NoticeSearchPort

/**
 * 공고 검색 조회기(D-6A2b-8 — `app.http` 는 포트가 아니라 조회기를 받는다). 포트에 읽기
 * 하나밖에 없어도 조회기를 끼우는 이유는 같다: HTTP 로 닿는 층이 포트 타입을 **쥐지 않게**
 * 한다(새 메서드가 포트에 생기면 그것이 곧 HTTP 지름길이 된다).
 *
 * 하는 일은 위임 하나다 — 판정·조립을 복제하지 않는다. 조건을 만드는 것은 순수 파싱
 * (`parseNoticeSearchParameters`)이고 이 클래스는 그것을 다시 검사하지 않는다.
 */
class NoticeSearchQuery(
    private val notices: NoticeSearchPort,
) {
    fun search(criteria: NoticeSearchCriteria): NoticeSearchPage = notices.search(criteria)
}
