package bidvector.procurement

/** 업무구분 코드(COL-08) — KONEPS 셀 형태(`"0411 기술용역"`)의 코드 절반. */
data class CategoryCode(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CategoryCode는 빈 문자열일 수 없다" }
    }
}

/** 업무구분 표시 라벨. */
data class CategoryLabel(
    val value: String,
)

/**
 * 업무구분(D-3A-5, COL-08) — 코드와 라벨 **두 값**. 매핑에 없는 코드는 `label = null`
 * (미지)이고, 임의 라벨을 붙이지 않는다(COL-08 acceptance). 코드+라벨 한 셀 형태는 파싱
 * 경계 밖으로 흐르지 않는다 — 분리는 어댑터(3B) 소관이고 이 타입은 분리된 결과만 받는다.
 */
data class BusinessCategory(
    val code: CategoryCode,
    val label: CategoryLabel?,
)

/**
 * 업무구분명(`bsnsDivNm`)의 **문서 열거 어휘** — v2-defect 016 수정(3A 잔여 일괄 verifier
 * r3 전). 문서(`policy-values.md` §1.5, 조달청 OpenAPI 참고자료 — *"물품 · 용역 · 공사 ·
 * 외자"*, 필수·항목크기 30)가 정확히 이 넷을 든다. **순서가 문서 표기 순서**다(왕복
 * 비교에 쓰인다) — 임의로 재정렬하지 않는다.
 */
data class DocumentedVocabulary(
    val values: List<String>,
) {
    fun covers(value: String): Boolean = value in values
}
