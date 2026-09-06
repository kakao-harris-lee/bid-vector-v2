package bidvector.strategy

/** 카테고리 코드 — 완전일치 비교 축(STR-01). 값 크기·형태로 의미를 추측하지 않는다. */
data class CategoryCode(
    val value: String,
)

/**
 * 키워드 규칙이 보는 텍스트 범위(STR-02, D-5) — 제목+요건+카테고리. `description`을
 * 포함하지 않는다(발주기관명 오탐 차단, 스카우트 §1.3). [FullScopeText]와 서로 다른
 * 타입이라 규칙의 시그니처가 어느 텍스트를 보는지 강제한다 — 어댑터(M3)가 조립 규율을
 * 지킨다는 전제 자체는 타입이 막지 못한다(scope.md 위협 모델 「방어하지 않는다」).
 */
data class KeywordScopeText(
    val value: String,
)

/**
 * 지역 규칙이 보는 텍스트 범위(STR-02, D-5) — `description`을 포함한 전체 텍스트.
 * 지역명은 발주기관명에 정당하게 등장하므로 키워드 규칙보다 넓게 본다.
 */
data class FullScopeText(
    val value: String,
)
