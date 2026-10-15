package bidvector.workflow.notice

import bidvector.procurement.Notice

/**
 * 공고 조회 포트(STR-16 검색 절반) — **읽기 하나뿐이다.** 쓰기·상태 전이 메서드가 없으므로
 * 이 포트를 쥔 코드가 공고를 바꿀 길이 없다(검색은 read-only, capability map STR-16
 * acceptance 「검색이 운영자 전략을 변경하지 않는다」와 같은 축).
 *
 * `bidvector.workflow.evaluation.CandidateSourcePort` 와 같은 자리의 타입이다(둘 다 공고
 * 다건 읽기) — 다른 점은 **조건이 운영자 질의**라는 것뿐이라 그 포트를 넓히지 않고 따로
 * 둔다(감시 후보 스캔은 전략이 조건을 정하고 상한 초과가 실패다; 검색은 운영자가 조건을
 * 정하고 페이지가 답이다).
 */
interface NoticeSearchPort {
    fun search(criteria: NoticeSearchCriteria): NoticeSearchPage
}

/**
 * 한 페이지의 답 — 페이지 길이와 **전체 건수**가 다른 값이다(`X-Total-Count`). 빈 결과의
 * 두 뜻은 [state] 가 가른다(추적표 STR-16 「"조건에 맞는 것이 없다"와 "아직 수집되지
 * 않았다"가 서로 다른 답으로 구분된다」) — 호출부가 건수 0 을 보고 사유를 추측하지 않는다.
 *
 * `init` 의 두 단언이 불가능한 조합을 만들지 못하게 한다: 「맞은 것이 있다」와 「전체 건수
 * 0」은 함께 설 수 없고, 맞은 것이 없는데 페이지에 행이 있을 수도 없다.
 */
data class NoticeSearchPage(
    val notices: List<Notice>,
    val totalCount: Long,
    val state: NoticeSearchState,
) {
    init {
        require((state == NoticeSearchState.MATCHED) == (totalCount > 0L)) {
            "state 와 totalCount 가 어긋난다: $state, $totalCount"
        }
        require(state == NoticeSearchState.MATCHED || notices.isEmpty()) {
            "맞은 공고가 없는데 페이지에 행이 있다: $state"
        }
    }
}

/**
 * 빈 결과의 두 뜻 + 비어 있지 않은 경우. [MATCHED] 는 **전체 건수가 1 이상**이라는 뜻이고
 * 이 페이지가 비어 있을 수 있다(마지막 페이지 뒤를 물었을 때) — 그 경우도 「조건이 좁다」가
 * 아니므로 같은 값이다.
 */
enum class NoticeSearchState {
    /** 조건에 맞는 공고가 있다(이 페이지가 비어 있어도 전체 건수가 1 이상이다). */
    MATCHED,

    /** 조건에 맞는 공고가 없다 — 수집된 공고는 있다(조건이 좁다). */
    NO_MATCH,

    /** 수집된 공고가 아직 하나도 없다(조건과 무관하다). */
    NOTHING_COLLECTED,
}
