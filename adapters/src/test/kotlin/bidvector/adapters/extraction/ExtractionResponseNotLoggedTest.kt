package bidvector.adapters.extraction

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

private const val SECRET_MARKER = "SECRET-LICENSE-HOLDER-DETAIL-9f21"

/**
 * M3/3C ⑥ — LLM 응답 원문은 `toString()`·예외 메시지 어디에도 노출되지 않는다(위협
 * 모델 방어 (e), 우회 (5)).
 */
class ExtractionResponseNotLoggedTest {
    @Test
    fun `LlmResponse toString 은 원문을 담지 않는다`() {
        val response = LlmResponse(SECRET_MARKER)

        response.toString() shouldNotContain SECRET_MARKER
    }

    @Test
    fun `스키마 위반 실패의 사유 메시지는 값에 담긴 원문을 옮기지 않는다`() {
        val validator = RequirementSchemaValidator()
        val rawResponse =
            """{"assertedAbsent": false, "items": [{"serialNo": "$SECRET_MARKER",
              |"sourceField": "NOT_A_VALID_FIELD", "licenseNames": [], "evidence": {"charStart": 0, "charEnd": 1}}]}
            """.trimMargin()

        val outcome = validator.validate(rawResponse)

        val messages = (outcome as SchemaValidationOutcome.Invalid).messages
        messages.none { it.contains(SECRET_MARKER) } shouldBe true
    }

    @Test
    fun `LlmCredential toString 은 원문을 노출하지 않는다`() {
        val credential = LlmCredential.of(SECRET_MARKER)

        credential.toString() shouldNotContain SECRET_MARKER
    }
}
