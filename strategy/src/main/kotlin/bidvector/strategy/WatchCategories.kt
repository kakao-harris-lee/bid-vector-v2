package bidvector.strategy

/**
 * 감시 「관심 업종」 집합 조립(D-6F9-4, M6/6F-9) — 공고가 나르는 업무구분 **네 값**(대분류·용역구분·공공조달분류 코드·
 * 주공종)에서 **비어 있지 않은 것만** 모아 [WatchSubject.categories] 를 만든다. 운영자가 「관심 업종」에 `공사`·`기술용역`·
 * `전기공사업`·분류번호 무엇을 적어도 이 집합의 원소 하나와 맞는다(비교는 규칙이 trim·대소문자 접기로 한다 — 이 커널은 비교
 * 규칙을 다시 쓰지 않는다).
 *
 * [assembleKeywordScopeText]와 같은 이유로 `procurement` 타입을 받지 않는다(ADR 0006 D-4) — 호출부(어댑터)가 도메인 값을 원문
 * 문자열로 바꿔 넘긴다. **어댑터가 이 집합을 스스로 짓지 않는 것이 요점이다**: 빈 값·공백뿐인 코드가 집합에 들어가면 그 원소가
 * 공백 규칙과 맞거나(아무 공고나 통과) 아무것과도 안 맞아(감시가 조용히 죽는다) 「값이 없으면 없다」(D-6F4-8)가 깨진다 —
 * 그 제외 규칙과 trim 이 이 한 자리에 있다.
 */
fun assembleWatchCategories(
    businessDivision: String?,
    serviceDivision: String?,
    classificationCode: String?,
    mainConstructionType: String?,
): Set<CategoryCode> =
    listOf(businessDivision, serviceDivision, classificationCode, mainConstructionType)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .mapTo(linkedSetOf(), ::CategoryCode)
