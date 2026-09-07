package bidvector.adapters.extraction

import bidvector.procurement.ExtractedGroupNo
import bidvector.procurement.ExtractedRequirementItem
import bidvector.procurement.ExtractedRequirements
import bidvector.procurement.ExtractedSerialNo
import bidvector.procurement.ExtractedSourceField
import bidvector.procurement.ExtractionEvidenceSpan
import bidvector.qualification.RequirementCollection
import bidvector.qualification.RequirementRow
import bidvector.qualification.RequirementSourceField
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

private val ITEM =
    ExtractedRequirementItem(
        ExtractedGroupNo("01"),
        ExtractedSerialNo("001"),
        ExtractedSourceField.PermittedIndustryList,
        listOf("전기공사업", "정보통신공사업"),
        ExtractionEvidenceSpan(0, 10),
    )

/** D-3C-3 — 3C 추출 결과 → 1C `RequirementCollection` 변환표(design review §(3)). */
class ExtractionToQualificationTest {
    @Test
    fun `Extracted 는 Collected 로 변환되고 필드가 보존된다`() {
        val attempt = ExtractionAttempt.Extracted(ExtractedRequirements(listOf(ITEM), false), emptyList())

        val collection = toRequirementCollection(attempt)

        collection.shouldBeInstanceOf<RequirementCollection.Collected>()
        val row = collection.rows.single() as RequirementRow.Parsed
        row.licenseNames.map { it.value } shouldBe listOf("전기공사업", "정보통신공사업")
        row.sourceField shouldBe RequirementSourceField.PermsnIndstrytyList
        row.groupNo?.value shouldBe "01"
    }

    @Test
    fun `Uncertain 은 사유와 무관하게 CollectionFailed 로 접힌다`() {
        val reasons =
            listOf(
                ExtractionFailure.FetchFailed,
                ExtractionFailure.Timeout,
                ExtractionFailure.BreakerOpen,
                ExtractionFailure.SchemaViolation("v1", emptyList()),
                ExtractionFailure.BudgetExceeded(BudgetExceededKind.CallCount),
                ExtractionFailure.EmptyResponse,
            )

        reasons.forEach { reason ->
            toRequirementCollection(ExtractionAttempt.Uncertain(reason)) shouldBe RequirementCollection.CollectionFailed
        }
    }

    @Test
    fun `요건 없음 주장도 CollectionFailed 로 접힌다 — DataAbsent 로 확정하지 않는다`() {
        val requirements = ExtractedRequirements(emptyList(), assertedAbsent = true)
        val attempt = ExtractionAttempt.Extracted(requirements, emptyList())

        toRequirementCollection(attempt) shouldBe RequirementCollection.CollectionFailed
    }
}
