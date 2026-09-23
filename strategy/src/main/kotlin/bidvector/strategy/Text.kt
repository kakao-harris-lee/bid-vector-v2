package bidvector.strategy

/** 카테고리 코드 — 완전일치 비교 축(STR-01). 값 크기·형태로 의미를 추측하지 않는다. */
data class CategoryCode(
    val value: String,
)

/**
 * 키워드 규칙이 보는 텍스트 범위(STR-02, D-6F4-3·3b 개정) — **공고명 + 공종 둘만**. 요건
 * (`qualification_text`)·발주기관명 어느 쪽도 포함하지 않는다. 요건은 V2에서 면허제한
 * 오퍼레이션 응답(면허명·허용업종·업종분야)이라 업무 키워드가 아니고, 기관명은 legacy가
 * 겪은 오탐(발주기관명이 필수 키워드를 거짓 만족)의 차단 대상이다(D-6F4-3b·D-6F4-3).
 * [FullScopeText]와 서로 다른 타입이라 규칙의 시그니처가 어느 텍스트를 보는지 강제한다.
 *
 * **생성 경계(M6/6F-4-w, D-6F4W-7)** — 유일한 생성 경로는 [assembleKeywordScopeText]다.
 * `procurement.NoticeTitle`과 같은 기전(`@ConsistentCopyVisibility` + `private constructor`
 * + companion factory)으로 닫혀 `copy()`도 이 경계 밖으로 나가지 않는다(verifier r2
 * MEDIUM-3 — 요건 텍스트로 이 타입을 직접 만들어 필수 키워드를 만족시키는 test 가
 * 초록이었던 우회를 닫는다). 그래서 요건·기관명이 이 값에 실릴 길이 타입으로 없다.
 */
@ConsistentCopyVisibility
data class KeywordScopeText private constructor(
    val value: String,
) {
    companion object {
        internal fun of(
            noticeTitle: String?,
            businessCategoryLabel: String?,
        ): KeywordScopeText = KeywordScopeText(joinNonBlankParts(keywordParts(noticeTitle, businessCategoryLabel)))
    }
}

/**
 * 지역 규칙이 보는 텍스트 범위(STR-02, D-6F4-3 개정) — 키워드 조각(공고명+공종) + 발주기관명
 * 둘(V7 `demand_agency_name`·`notice_agency_name`). 지역명은 발주기관명에 정당하게
 * 등장하므로 키워드 규칙보다 넓게 본다. **본문(공고 상세)은 V2에 존재하지 않는다** —
 * legacy의 그 필드는 공고 본문이 아니라 수집 메타데이터 덤프였다(D-6F4-2, 옮기지 않는다).
 *
 * **생성 경계(M6/6F-4-w, D-6F4W-7)** — [KeywordScopeText]와 같은 기전으로 닫힌다. 유일한
 * 생성 경로는 [assembleFullScopeText]다.
 */
@ConsistentCopyVisibility
data class FullScopeText private constructor(
    val value: String,
) {
    companion object {
        internal fun of(
            noticeTitle: String?,
            businessCategoryLabel: String?,
            demandAgencyName: String?,
            noticeAgencyName: String?,
        ): FullScopeText =
            FullScopeText(
                joinNonBlankParts(
                    keywordParts(noticeTitle, businessCategoryLabel) + listOf(demandAgencyName, noticeAgencyName),
                ),
            )
    }
}

/** 감시 텍스트 조립 부분 구분자 — 규약 상수, 정책 값이 아니다(`TextSynthesis.kt` SEPARATOR 관례). */
private const val WATCH_TEXT_SEPARATOR = " "

private fun joinNonBlankParts(parts: List<String?>): String =
    parts.filterNotNull().filter(String::isNotBlank).joinToString(WATCH_TEXT_SEPARATOR)

/** [assembleKeywordScopeText]·[assembleFullScopeText]가 공유하는 키워드 조각 — 공고명 + 공종. */
private fun keywordParts(
    noticeTitle: String?,
    businessCategoryLabel: String?,
): List<String?> = listOf(noticeTitle, businessCategoryLabel)

/**
 * 키워드 매칭 대상 조립(D-6F4-3·3b) — 공고명 + 공종만 잇는다. `strategy`는 shared-kernel만
 * 참조하므로(ADR 0006 D-4) `procurement` 타입을 직접 받지 않는다 — 호출부(워크플로/어댑터)
 * 가 도메인 값(`Notice.title`·`Notice.businessCategory`)을 원문 문자열로 바꿔 넘긴다.
 * 부재 조각은 결합에서 빠진다(빈 문자열을 끼워 넣지 않는다) — 둘 다 없으면 `""`(D-6F4-4b,
 * 「값이 없으면 없는 것이다」, 예외로 흐르지 않는다).
 *
 * **요건·기관명은 이 함수의 인자 자체가 없다** — 이 시그니처를 통해서는 부를 방법이 없다
 * (D-6F4-3b: 요건은 면허제한 오퍼레이션 응답이라 업무 키워드가 아니다. D-6F4-3: 기관명은
 * legacy 오탐의 차단 대상, [assembleFullScopeText]에만 더한다). **M6/6F-4-w(D-6F4W-7)부터
 * 그 배제는 이 함수 하나로 닫힌다** — [KeywordScopeText]의 생성자가 `private`이라 이
 * 시그니처를 우회할 생성 경로가 없다(2026-09-19 D-6F4-3c 의 한계를 해소).
 */
fun assembleKeywordScopeText(
    noticeTitle: String?,
    businessCategoryLabel: String?,
): KeywordScopeText = KeywordScopeText.of(noticeTitle, businessCategoryLabel)

/**
 * 지역 매칭 대상 조립(D-6F4-3) — 키워드 조각(공고명+공종) + 발주기관명 둘(V7). legacy가
 * 메타데이터 덤프에서 긁던 지역 단서를 타입이 있는 열에서 얻는다 — 덤프가 없으므로 「기관명이
 * 필수 키워드를 만족시키는」 오탐 경로가 이 함수가 아니라 [assembleKeywordScopeText]의
 * 시그니처(기관명 인자 없음)로 막힌다. [FullScopeText]도 [KeywordScopeText]와 같은 생성
 * 경계(D-6F4W-7)로 닫혀 있다.
 */
fun assembleFullScopeText(
    noticeTitle: String?,
    businessCategoryLabel: String?,
    demandAgencyName: String?,
    noticeAgencyName: String?,
): FullScopeText = FullScopeText.of(noticeTitle, businessCategoryLabel, demandAgencyName, noticeAgencyName)
