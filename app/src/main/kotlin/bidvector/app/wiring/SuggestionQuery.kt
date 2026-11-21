package bidvector.app.wiring

import bidvector.workflow.access.AccountId
import bidvector.workflow.suggestion.ListSuggestionsUseCase
import bidvector.workflow.suggestion.SuggestionList

/**
 * 제안 목록 조회기(M7/7C①b — `app.http` 는 use case 가 아니라 조회기를 받는다,
 * `NoticeSearchQuery` 와 같은 형태). 하는 일은 위임 하나다 — 게이트·비교·상한을 복제하지 않는다.
 */
class SuggestionQuery(
    private val suggestions: ListSuggestionsUseCase,
) {
    /** **M15/15A** — 그 계정의 전략·자격으로 계산한 목록이다. */
    fun list(account: AccountId): SuggestionList = suggestions.list(account)
}
