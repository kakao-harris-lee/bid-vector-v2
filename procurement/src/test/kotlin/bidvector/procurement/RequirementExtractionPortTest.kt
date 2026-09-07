package bidvector.procurement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private val ITEM =
    ExtractedRequirementItem(
        groupNo = ExtractedGroupNo("01"),
        serialNo = ExtractedSerialNo("001"),
        sourceField = ExtractedSourceField.LicenseLimitName,
        licenseNames = listOf("전기공사업"),
        evidence = ExtractionEvidenceSpan(0, 5),
    )

/**
 * M3/3C ⑦ — [ExtractedRequirements]는 「값을 못 읽었다」와 「없다고 확인했다」를
 * 동시에 주장할 수 없다(위협 모델 방어 (a), fail-open 금지). `Eligible`류 값이 없어
 * 이 타입 자체로는 자격 통과를 표현할 수 없다.
 */
class RequirementExtractionPortTest {
    @Test
    fun `assertedAbsent=false 이면 items 가 비어 있을 수 없다`() {
        shouldThrow<IllegalArgumentException> { ExtractedRequirements(emptyList(), assertedAbsent = false) }
    }

    @Test
    fun `assertedAbsent=true 이면 items 를 가질 수 없다`() {
        shouldThrow<IllegalArgumentException> { ExtractedRequirements(listOf(ITEM), assertedAbsent = true) }
    }

    @Test
    fun `요건 없음 주장은 빈 목록으로 구성할 수 있다`() {
        val requirements = ExtractedRequirements(emptyList(), assertedAbsent = true)

        requirements.items shouldBe emptyList()
    }

    @Test
    fun `licenseNames 가 비어 있으면 항목을 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            ExtractedRequirementItem(
                null,
                ExtractedSerialNo("001"),
                ExtractedSourceField.LicenseLimitName,
                emptyList(),
                ExtractionEvidenceSpan(0, 1),
            )
        }
    }

    @Test
    fun `ExtractionOutcome 은 소진 when 으로만 소비된다`() {
        val outcomes: List<ExtractionOutcome> =
            listOf(ExtractionOutcome.Extracted(ExtractedRequirements(listOf(ITEM), false)), ExtractionOutcome.Uncertain)

        val extractedCount =
            outcomes.count {
                when (it) {
                    is ExtractionOutcome.Extracted -> true
                    ExtractionOutcome.Uncertain -> false
                }
            }

        extractedCount shouldBe 1
    }
}
