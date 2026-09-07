package bidvector.adapters.extraction

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

/**
 * 요건 추출 결과 스키마 version — 파일명이 곧 version 이다(D-3C-7, `schema/
 * requirement-extraction.v1.json`과 `prompts/requirement-extraction.v1.txt`가 짝).
 * 정책 데이터가 아니다 — 값을 바꾸려면 새 파일을 만든다(운영 튜닝 대상이 아니라 리소스
 * 좌표 자체이기 때문, 매직넘버 금지가 겨냥하는 "튜닝 가능한 운영 값"과는 다른 축).
 */
const val REQUIREMENT_EXTRACTION_SCHEMA_VERSION = "requirement-extraction.v1"
const val REQUIREMENT_EXTRACTION_PROMPT_VERSION = "requirement-extraction.v1"

private const val SCHEMA_RESOURCE_PATH = "/schema/$REQUIREMENT_EXTRACTION_SCHEMA_VERSION.json"

sealed interface SchemaValidationOutcome {
    data class Valid(
        val node: JsonNode,
    ) : SchemaValidationOutcome

    data class Invalid(
        val messages: List<String>,
    ) : SchemaValidationOutcome

    data class MalformedJson(
        val detail: String,
    ) : SchemaValidationOutcome
}

/**
 * JSON Schema(Draft 2020-12, networknt) 검증기(④, D-3C-2) — 스키마는 리소스 파일 하나가
 * 유일한 출처다(우회 (10) 방어 — 검증 로직에 조건을 완화하는 자리를 두지 않는다).
 */
class RequirementSchemaValidator {
    private val mapper = ObjectMapper()
    private val schema: JsonSchema = loadSchema()

    fun validate(rawJson: String): SchemaValidationOutcome {
        val node =
            try {
                mapper.readTree(rawJson)
            } catch (malformed: com.fasterxml.jackson.core.JsonProcessingException) {
                return SchemaValidationOutcome.MalformedJson(malformed.javaClass.simpleName)
            }
        val messages = schema.validate(node)
        return if (messages.isEmpty()) {
            SchemaValidationOutcome.Valid(node)
        } else {
            SchemaValidationOutcome.Invalid(messages.map { it.message })
        }
    }

    private fun loadSchema(): JsonSchema {
        val stream =
            javaClass.getResourceAsStream(SCHEMA_RESOURCE_PATH)
                ?: error("스키마 리소스를 찾지 못했다: $SCHEMA_RESOURCE_PATH")
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(stream)
    }
}
