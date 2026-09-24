package bidvector.procurement

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * D-6F9-1·2 — 업무구분 축의 새 칸 셋(대분류·용역구분·주공종)의 타입 규율. 대분류는 P-7 문서 열거 어휘
 * 넷(순서 = 문서 표기 순서)이고, 용역구분·주공종은 원문 trim 이름이다(코드를 짓지 않는다).
 */
class BusinessDivisionTest {
    @Test
    fun `대분류 라벨은 문서 열거 어휘 넷을 문서 표기 순서로 낸다`() {
        BusinessDivision.entries.map { it.label } shouldContainExactly listOf("물품", "용역", "공사", "외자")
    }

    @Test
    fun `정책의 문서 열거 어휘는 대분류 enum 에서 파생된다 — 두 번째 출처가 없다`() {
        val policy = (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved).value

        policy.businessCategoryDocumentedLabels.values shouldBe BusinessDivision.entries.map { it.label }
    }

    @Test
    fun `라벨로 대분류를 복원한다 — 왕복이 항등이다`() {
        BusinessDivision.entries.forEach { division ->
            withClue(division.name) { BusinessDivision.fromLabel(division.label) shouldBe division }
        }
    }

    @Test
    fun `어휘 밖 라벨은 null 이다 — 예외도 추측도 없다`() {
        listOf("", " ", "용역 ", "SERVICE", "기술용역", "공사업").forEach { label ->
            withClue("'$label'") { BusinessDivision.fromLabel(label) shouldBe null }
        }
    }

    @Test
    fun `용역구분은 원문 trim 만 하고 정규화하지 않는다`() {
        ServiceDivision.of("  일반용역 ")?.value shouldBe "일반용역"
        ServiceDivision.of("일반용역(리스)")?.value shouldBe "일반용역(리스)"
    }

    @Test
    fun `주공종은 원문 trim 만 하고 정규화하지 않는다`() {
        MainConstructionType.of(" 전기공사업\t")?.value shouldBe "전기공사업"
        MainConstructionType.of("정보통신 공사업")?.value shouldBe "정보통신 공사업"
    }

    @Test
    fun `빈 값과 공백류만 있는 용역구분·주공종은 예외 없이 null 이다`() {
        listOf("", " ", "\t", "\n", "\u00A0", "\u3000").forEach { blank ->
            withClue("'${blank.replace("\n", "\\n")}'") {
                ServiceDivision.of(blank) shouldBe null
                MainConstructionType.of(blank) shouldBe null
            }
        }
    }
}
