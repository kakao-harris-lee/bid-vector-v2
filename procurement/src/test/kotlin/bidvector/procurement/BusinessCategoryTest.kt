package bidvector.procurement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * D-4B8-3 — [CategoryCode] 정규화 규칙표(strip+lower, Python `ml_engine.features.normalize.
 * normalize_feature_key` 미러). 생성은 [CategoryCode.of] 하나뿐이다 — `CategoryCode(...)`
 * 직접 호출은 `private` 생성자라 컴파일되지 않는다(이 gate 는 컴파일 거부라 test 로 상주하지
 * 않는다, `gate.tests.procurement` 관례와 같음 — `NoticeIdTest`의 `NoticeRound` 폐쇄 주석 참고).
 */
class BusinessCategoryTest {
    @Test
    fun `of 는 앞뒤 공백을 제거하고 소문자로 접는다`() {
        CategoryCode.of(" A01 ").value shouldBe "a01"
    }

    @Test
    fun `이미 정규화된 값은 그대로다`() {
        CategoryCode.of("a01").value shouldBe "a01"
    }

    @Test
    fun `빈 문자열은 거부된다`() {
        shouldThrow<IllegalArgumentException> { CategoryCode.of("") }
    }

    @Test
    fun `공백만 있는 문자열은 정규화 뒤 빈 키라 거부된다`() {
        shouldThrow<IllegalArgumentException> { CategoryCode.of("   ") }
    }

    /**
     * 유니코드 한 case — 전각 공백(U+3000). 설계 검토(`_workspace/m4-4b8/02_design-review.md`
     * 구현 지시 (1))는 「전각 공백은 `trim` 대상 아니다 — Python `strip()`과의 차이」를
     * 가정했으나, 실측(kotlinc 2.2.20 스크립트 — `"　A01　".trim()` == `"A01"`)은 그 반대다:
     * Kotlin `trim()`(`Char.isWhitespace() || Char.isSpaceChar()` 판정)도 U+3000을 제거하고,
     * Python `str.strip()`도 U+3000을 제거한다(`'　'.isspace()` == `True`) — 두 언어가
     * 이 축에서는 갈리지 않는다. 알려진 제한은 이 사실이 아니라 더 일반적인 것으로
     * 정정한다: JVM `String.lowercase()`(로케일 독립)와 Python `str.lower()`의 유니코드
     * 케이스 폴딩이 모든 문자에서 바이트 단위로 같다는 보장은 없다(둘 다 로케일 독립
     * 알고리즘이라 ASCII·한글·숫자 축에서는 같지만, 전수 대조는 이 slice 밖이다).
     */
    @Test
    fun `전각 공백도 trim 대상이다 — Python strip 과 갈리지 않는다(설계 검토 가정 정정, 실측)`() {
        CategoryCode.of("　A01　").value shouldBe "a01"
    }

    @Test
    fun `normalizeCategoryKey 는 CategoryCode 없이 문자열만 정규화한다`() {
        normalizeCategoryKey(" A01 ") shouldBe "a01"
    }

    @Test
    fun `normalizeCategoryKey 로 얻은 정규화 문자열만으로는 CategoryCode 를 만들 수 없다 — of 를 거쳐야 한다`() {
        val normalized = normalizeCategoryKey(" A01 ")

        CategoryCode.of(normalized).value shouldBe normalized
    }
}
