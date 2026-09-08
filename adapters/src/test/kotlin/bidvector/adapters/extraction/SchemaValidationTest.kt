package bidvector.adapters.extraction

import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/** ④ D-3C-2 — `additionalProperties:false`·required 목록이 실제로 강제됨을 단언한다(우회 (10)). */
class SchemaValidationTest {
    private val validator = RequirementSchemaValidator()

    @Test
    fun `정상 응답은 검증을 통과한다`() {
        validator.validate(validExtractionResponseJson()).shouldBeInstanceOf<SchemaValidationOutcome.Valid>()
    }

    @Test
    fun `요건 없음 주장 응답은 검증을 통과한다`() {
        validator.validate(ASSERTED_ABSENT_RESPONSE_JSON).shouldBeInstanceOf<SchemaValidationOutcome.Valid>()
    }

    @Test
    fun `additionalProperties 위반은 Invalid 다`() {
        validator.validate(SCHEMA_VIOLATING_RESPONSE_JSON).shouldBeInstanceOf<SchemaValidationOutcome.Invalid>()
    }

    /**
     * verifier r1 F-4(a) — 위 test 의 fixture 는 위반이 둘(미등록 키 + `items` 빈 배열)이라
     * `additionalProperties:false` 를 지워도 두 번째 위반 때문에 여전히 Invalid 다(변이
     * 실측). 이 test 의 fixture 는 미등록 키 **하나만** 위반이라 `additionalProperties`
     * 규칙이 실제로 죽으면(값을 `true` 로 바꾸면) 이 test 만 Valid 로 뒤집혀 죽는다.
     */
    @Test
    fun `additionalProperties 단독 위반(그 밖은 전부 유효)은 Invalid 다`() {
        validator
            .validate(
                ADDITIONAL_PROPERTY_ONLY_VIOLATION_JSON,
            ).shouldBeInstanceOf<SchemaValidationOutcome.Invalid>()
    }

    @Test
    fun `필수 필드 누락은 Invalid 다`() {
        validator.validate("""{"assertedAbsent": false}""").shouldBeInstanceOf<SchemaValidationOutcome.Invalid>()
    }

    @Test
    fun `licenseNames 가 비어 있는 항목은 Invalid 다`() {
        val body =
            """{"assertedAbsent": false, "items": [
              |{"serialNo": "001", "sourceField": "LICENSE_LIMIT_NAME", "licenseNames": [],
              | "evidence": {"charStart": 0, "charEnd": 1}}]}
            """.trimMargin()

        validator.validate(body).shouldBeInstanceOf<SchemaValidationOutcome.Invalid>()
    }

    @Test
    fun `assertedAbsent=true 인데 items 가 있으면 Invalid 다`() {
        val body =
            """{"assertedAbsent": true, "items": [
              |{"serialNo": "001", "sourceField": "LICENSE_LIMIT_NAME", "licenseNames": ["a"],
              | "evidence": {"charStart": 0, "charEnd": 1}}]}
            """.trimMargin()

        validator.validate(body).shouldBeInstanceOf<SchemaValidationOutcome.Invalid>()
    }

    @Test
    fun `깨진 JSON 은 MalformedJson 이다`() {
        validator.validate("{not json").shouldBeInstanceOf<SchemaValidationOutcome.MalformedJson>()
    }
}
