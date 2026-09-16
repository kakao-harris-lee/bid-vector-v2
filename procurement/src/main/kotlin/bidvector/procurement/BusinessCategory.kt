package bidvector.procurement

/**
 * 업무구분 코드 정규화(D-4B8-3) — Python `ml_engine.features.normalize.normalize_feature_key`
 * 미러(`strip().lower()`, 별칭 없음). 조회 축(SQL 정확 일치)·요청 축·표본 축이 같은 규칙으로
 * 이 함수 하나를 거친다 — 두 번째 정규화 규칙(예: SQL `LOWER(TRIM())`)을 두지 않는다.
 *
 * `Char.lowercaseChar()`(`Character.toLowerCase(Char)`, 문자 단위) 를 쓴다 — Kotlin의
 * `String.lowercase()`(무인자)는 내부적으로 `String.toLowerCase(Locale.ROOT)`로 컴파일되고,
 * `ArchitectureGateTest`가 domain 모듈의 `java.util.Locale` 참조를 환경 축 누출로 거부한다
 * (`config/quality/architecture-policy.properties` `effect.surface.classes`, 실측 —
 * `member-effects.properties`가 `String#toLowerCase`를 `forbidden`으로 이미 분류해 뒀다).
 * `Character.toLowerCase`는 로케일 인자가 없는 별도 JDK 메서드라 이 표면에 닿지 않는다
 * (알려진 제한 — 문자 단위 접힘이라 한 글자가 여러 글자로 바뀌는 유니코드 대소문자
 * 매핑(예: 터키어 `İ`)은 `String.toLowerCase(Locale.ROOT)`와 바이트 단위로 갈릴 수 있다.
 * KONEPS 업무구분 코드는 ASCII 영숫자라 이 축은 실무에서 관측되지 않는다).
 */
fun normalizeCategoryKey(raw: String): String = raw.trim().map(Char::lowercaseChar).joinToString(separator = "")

/**
 * 업무구분 코드(COL-08) — KONEPS 셀 형태(`"0411 기술용역"`)의 코드 절반. [value]는 항상
 * [normalizeCategoryKey]를 거친 정규화된 형태다(D-4B8-3 불변식) — 생성은 [of] 하나뿐이고
 * `@ConsistentCopyVisibility`(`Rate`·`BidAmount`와 같은 관례, `shared-kernel/Money.kt`)로
 * `copy()`도 그 경계 밖으로 나가지 않는다.
 */
@ConsistentCopyVisibility
data class CategoryCode private constructor(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CategoryCode는 빈 문자열일 수 없다" }
    }

    companion object {
        fun of(raw: String): CategoryCode = CategoryCode(normalizeCategoryKey(raw))
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
