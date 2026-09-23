package bidvector.adapters.evaluation

import bidvector.procurement.Notice
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.ReasonCode
import bidvector.strategy.WatchSubject
import bidvector.strategy.assembleFullScopeText
import bidvector.strategy.assembleKeywordScopeText
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort

/**
 * [WatchSubjectPort]의 첫 production 구현(D-6F4W-2~5). `Notice`(3A canonical fact)가 이미 나르는
 * 열(title·businessCategory·agency 둘·baseAmount)만 읽는다 — DB 재조회·마이그레이션 없음
 * (D-6F4W-1 은 이 slice 를 ⓐ 포트 구현으로만 가른다, 값의 수집 정확성은
 * `OPEN-6F4-TITLE-INGEST`).
 *
 * **D-6F4W-3 — `Unavailable` 은 이 어댑터에서 표현 불가능하다.** 순수 매퍼([noticeToWatchSubject])는
 * I/O 가 없어 실패할 수 없다 — 반환형이 `WatchSubjectOutcome`이 아니라 [WatchSubject]인 전(total)
 * 함수이고, 이 클래스가 그 결과를 [WatchSubjectOutcome.Found]로만 감싼다. 「부재 → `Unavailable`」로
 * 정하면(반대 선택) `notice_title` 실 데이터 0건인 지금 전건 탈락이 된다(D-6F4W-2) — 타입으로
 * 그 선택 자체를 없앤다.
 */
class NoticeWatchSubjectPort : WatchSubjectPort {
    override fun subjectFor(notice: Notice): WatchSubjectOutcome =
        WatchSubjectOutcome.Found(noticeToWatchSubject(notice))
}

/**
 * `Notice` → `WatchSubject` 순수 매퍼(D-6F4W-3, 전(total) 함수). 조립은 `strategy`의
 * [assembleKeywordScopeText]·[assembleFullScopeText] 커널이 진다 — 이 함수는 값을 원문
 * `String?`으로 바꿔 넘기기만 한다(`strategy`는 ADR 0006 D-4 로 `procurement` 타입을 못 받아
 * 원문 문자열 변환이 어댑터의 일이다). 이어붙이기·구분자 상수를 여기 두지 않는다(우회 3).
 *
 * **D-6F4W-4 — `baseAmount` 부재의 사유는 [ReasonCode.EMPTY_INPUT] 고정이다.** 공고가 기초금액을
 * 싣지 않은 것과 「값은 있는데 계산이 실패했다」류 사유(`POLICY_NOT_APPLICABLE` 등)는 다른
 * 축이다 — scope.md D-6F4W-4 전수 검토.
 */
internal fun noticeToWatchSubject(notice: Notice): WatchSubject {
    val noticeTitle = notice.title?.value
    val businessCategoryLabel = notice.businessCategory?.label?.value
    return WatchSubject(
        categories =
            notice.businessCategory
                ?.let { category -> setOf(bidvector.strategy.CategoryCode(category.code.value)) }
                .orEmpty(),
        keywordText = assembleKeywordScopeText(noticeTitle, businessCategoryLabel),
        fullText =
            assembleFullScopeText(
                noticeTitle,
                businessCategoryLabel,
                notice.demandAgency?.name?.value,
                notice.noticeAgency?.name?.value,
            ),
        baseAmount =
            notice.baseAmount
                ?.let { resolved -> Fact.Known(resolved.amount) }
                ?: Fact.Absent(ReasonCode.EMPTY_INPUT),
    )
}
