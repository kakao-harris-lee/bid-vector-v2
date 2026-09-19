package bidvector.procurement

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * review MEDIUM — [NoticeTitle]의 핵심 불변식(trim 후 빈/공백 문자열은 `null`, 그 밖의 원문은
 * trim만 하고 정규화하지 않는다)을 순수 함수 수준에서 직접 잠근다. 지금까지는
 * `NoticeFindRoundTripTest`의 DB 왕복 경유로만 간접 확인됐다 — 같은 계열의 선례
 * [AgencyTest]가 [AgencyName]에 두는 test와 같은 형태다. 공백류 표본은 verifier r2
 * MEDIUM-1이 DB CHECK 과 교차 실측한 것과 같은 집합이다.
 */
class NoticeTitleTest {
    @Test
    fun `빈 문자열은 예외 없이 null 이다`() {
        NoticeTitle.of("") shouldBe null
    }

    @Test
    fun `공백류만 있는 문자열은 trim 뒤 빈 값이라 null 이다`() {
        listOf(
            " " to "ASCII 공백",
            "\t" to "탭",
            "\n" to "개행",
            " " to "NBSP",
            "　" to "전각 공백",
        ).forEach { (value, label) ->
            withClue(label) { NoticeTitle.of(value) shouldBe null }
        }
    }

    @Test
    fun `NoticeTitle of 는 원문을 trim 만 하고 정규화하지 않는다`() {
        NoticeTitle.of("  2026년 정보시스템 유지보수 용역  ")?.value shouldBe "2026년 정보시스템 유지보수 용역"
        NoticeTitle.of("공 고 명")?.value shouldBe "공 고 명"
    }

    @Test
    fun `NoticeTitle of 는 라틴 대소문자를 그대로 보존한다`() {
        NoticeTitle.of("  ABC Corp 지사 청사 신축  ")?.value shouldBe "ABC Corp 지사 청사 신축"
    }
}
