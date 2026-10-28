package bidvector.app.http

import bidvector.procurement.NoticeId
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OpeningResultFactsPort

/**
 * 7C⑤-a — 개찰 결과 조회 포트의 이중체. HTTP 층이 **사실을 어떤 응답으로 옮기는가**만 재고,
 * 실 질의 거동(열 집합·읽기 전용·원문 존재)은 `bidvector.adapters.opening
 * .JdbcOpeningResultViewTest`(실 PostgreSQL)가 잰다.
 */
class FakeOpeningResultFactsPort : OpeningResultFactsPort {
    @Volatile var facts: NoticeOpeningFacts = NoticeOpeningFacts.NoticeAbsent

    @Volatile var lastLookup: NoticeId? = null

    override fun lookup(id: NoticeId): NoticeOpeningFacts {
        lastLookup = id
        return facts
    }
}
