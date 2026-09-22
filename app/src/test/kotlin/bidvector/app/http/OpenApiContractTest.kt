package bidvector.app.http

import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.strategy.StrategyViolation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 우회 (6) 폐쇄 — D-6A1-8(수작성 단일 출처) · D-6A1-20(대조 깊이). 경로 이름만 보지 않고
 * **상태 코드 + 응답 JSON top-level 키 집합**을 `Set.equals`로 완전 일치 대조한다(부분
 * 포함이 아니다 — 여분 필드가 생겨도 실패). 추가로 **스키마 자신이 평탄함을 단언**한다
 * (D-6A1-20 ⓑ — 지금 컨트롤러 DTO가 평탄하다는 사실을 이 test가 잠근다).
 *
 * **D-6A1-38 시정 — 「이런 형태는 중첩이다」 차단 목록을 「이런 평탄한 형태만 허용한다」
 * 허용 목록으로 뒤집는다.** D-6A1-30 은 차단 목록에 배열 축 하나(`items.type == "object"`)만
 * 더했다. verifier 실측: 정적 다섯 형태 중 배열의 `$ref`·속성의 직접 `$ref`·배열의 배열·
 * `additionalProperties` **넷**이 여전히 GREEN 이었고, 런타임은 `values.none { it is Map }`이
 * 재귀가 아니라 **깊이 2 이상**(`List<List<Map>>`)이 GREEN 이었다 — 이 slice 안에서
 * F-3 → D-6A1-29 와 같은 축의 **세 번째 재발**(차단 목록에는 종점이 없다).
 *
 * [isFlatPropertyDefinition]은 이제 **스칼라 타입** 또는 **아이템이 스칼라인 배열**만
 * 허용하고 그 밖(`$ref`·`oneOf`·`allOf`·`anyOf`·`additionalProperties`·`type` 없음·
 * `type: object`·배열의 배열)은 전부 실패로 판정한다 — `type` 키 자체가 없으면 곧바로
 * 실패하므로 합성 어휘를 열거할 필요가 없다. [containsNestedObject]는 재귀로 바꿔 임의
 * 깊이의 `Map`을 잡는다.
 *
 * 새 라이브러리(swagger-parser 등)를 들이지 않는다 — 계약이 얕아 SnakeYAML(이미 app의
 * test 의존)로 raw Map 순회만으로 충분하다(preflight 조사·재사용 우선).
 */
@Suppress("UNCHECKED_CAST")
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND, PROP_NO_STATIC_RESOURCE_MAPPINGS],
)
class OpenApiContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = freshStrategy()
        strategyRepository.loadFailure = null
    }

    private val spec: Map<String, Any?> by lazy {
        val specProperty =
            requireNotNull(System.getProperty("bidvector.openapi.spec")) {
                "bidvector.openapi.spec 시스템 프로퍼티가 없다"
            }
        val specFile = File(specProperty)
        Yaml().load<Map<String, Any?>>(specFile.readText())
    }

    private fun schema(name: String): Map<String, Any?> =
        ((spec["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>)[name] as Map<String, Any?>

    private fun propertyKeys(schemaName: String): Set<String> =
        (schema(schemaName)["properties"] as Map<String, Any?>).keys

    /**
     * 모든 스키마를 훑어 [isFlatPropertyDefinition]이 **허용하지 않는** 속성(스키마 자신
     * 말고)을 모은다.
     */
    private fun collectNonFlatProperties(): List<String> {
        val schemas = (spec["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>
        return schemas.flatMap { (schemaName, schemaBody) ->
            val properties = (schemaBody as Map<String, Any?>)["properties"] as Map<String, Any?>
            properties
                .filterNot { (_, definition) -> isFlatPropertyDefinition(definition as Map<String, Any?>) }
                .map { (propertyName, _) -> "$schemaName.$propertyName" }
        }
    }

    /** D-6A1-30 — 값 자체가 object이거나(Map), 배열 어느 깊이에서든 object를 담으면 평탄하지 않다. */
    private fun containsNestedObject(value: Any?): Boolean =
        when (value) {
            is Map<*, *> -> true
            is List<*> -> value.any(::containsNestedObject)
            else -> false
        }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    @Test
    fun `D-6A1-20 ⓑ — 계약의 모든 속성 정의가 평탄한 형태(허용 목록)만 쓴다`() {
        collectNonFlatProperties() shouldBe emptyList()
    }

    /**
     * 양성 대조 — [isFlatPropertyDefinition]을 실제 YAML 파싱을 거치지 않고 직접 재서,
     * verifier가 고안한 다섯 우회 형태(MUT-D2~D5) 전부와 양성 대조(스칼라·스칼라 배열·
     * `type: object` 직접)를 고정한다. 실 스펙 파일을 건드리지 않고 술어 자체를 잠근다.
     */
    @Test
    fun `평탄 속성 허용 목록 술어는 다섯 우회 형태를 전부 거부하고 평탄 형태만 통과시킨다`() {
        isFlatPropertyDefinition(mapOf("type" to "string")) shouldBe true
        isFlatPropertyDefinition(mapOf("type" to "integer", "format" to "int64")) shouldBe true
        isFlatPropertyDefinition(mapOf("type" to "array", "items" to mapOf("type" to "string"))) shouldBe true

        // MUT-D-P0(양성) — 직접 object.
        isFlatPropertyDefinition(mapOf("type" to "object")) shouldBe false
        // MUT-D3 — 속성이 직접 `$ref`(object 스키마를 가리킨다).
        isFlatPropertyDefinition(mapOf("\$ref" to "#/components/schemas/Nested")) shouldBe false
        // MUT-D2 — `type: array` + `items: {$ref → object}`.
        isFlatPropertyDefinition(
            mapOf("type" to "array", "items" to mapOf("\$ref" to "#/components/schemas/Nested")),
        ) shouldBe false
        // D-6A1-30 이 이미 닫은 형태 — `type: array` + `items: {type: object}`.
        isFlatPropertyDefinition(
            mapOf("type" to "array", "items" to mapOf("type" to "object")),
        ) shouldBe false
        // MUT-D4 — 배열의 배열의 object.
        isFlatPropertyDefinition(
            mapOf(
                "type" to "array",
                "items" to mapOf("type" to "array", "items" to mapOf("type" to "object")),
            ),
        ) shouldBe false
        // MUT-D5 — `additionalProperties`(자유형 map, `type` 키 없이).
        isFlatPropertyDefinition(mapOf("additionalProperties" to mapOf("type" to "string"))) shouldBe false
        // additionalProperties가 `type: object`와 함께 오는 경우도 잡는다.
        isFlatPropertyDefinition(
            mapOf("type" to "object", "additionalProperties" to mapOf("type" to "string")),
        ) shouldBe false
    }

    /** 양성 대조 — [containsNestedObject]가 재귀로 임의 깊이의 object를 잡는지(MUT-R2). */
    @Test
    fun `평탄 응답 값 술어는 임의 깊이의 object 를 재귀로 잡는다`() {
        containsNestedObject("문자열") shouldBe false
        containsNestedObject(listOf("문자열", "다른 문자열")) shouldBe false
        containsNestedObject(mapOf("k" to "v")) shouldBe true
        containsNestedObject(listOf(mapOf("k" to "v"))) shouldBe true
        // MUT-R2 — 깊이 2: List<List<Map>>.
        containsNestedObject(listOf(listOf(mapOf("k" to "v")))) shouldBe true
        // 깊이 3도 잡는다 — 종점을 열거하지 않는 재귀임을 확인.
        containsNestedObject(listOf(listOf(listOf(mapOf("k" to "v"))))) shouldBe true
    }

    @Test
    fun `200 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 200
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("StrategyReadResponse")
        // D-6A1-20 ⓑ — 문서(위 test)뿐 아니라 **실제 응답값**도 평탄한지 잰다(배열 축 포함).
        response.body?.values?.none(::containsNestedObject) shouldBe true
    }

    @Test
    fun `401 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response =
            restTemplate.getForEntity(url("/api/strategy"), Map::class.java) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 401
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("ErrorBody")
    }

    @Test
    fun `미매핑 경로의 404 응답도 ErrorBody 계약과 일치한다`() {
        val response =
            restTemplate.exchange(
                url("/does-not-exist"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 404
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("ErrorBody")
    }

    /**
     * D-6A1-32 — 계약(`responses.500`)에 적힌 상태를 실제로 대조한다. 이전 판은 500을
     * `RequestAuditFilterTest`가 code/message만 개별 확인하고 계약 대조 경로에는 없었다
     * (D-6A1-8 단일 출처의 구멍). `InvalidStoredStrategyException` 전용 분기를 때려
     * `ErrorMapping`의 매핑표 두 번째 분기(기본 분기가 아니다)를 실제로 지난다.
     */
    @Test
    fun `500 응답(저장된 전략 무효)도 ErrorBody 계약과 일치한다`() {
        strategyRepository.loadFailure = {
            InvalidStoredStrategyException(listOf(StrategyViolation.CandidateLimitNotPositive))
        }

        val response =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 500
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.INVALID_STORED_STRATEGY
    }

    @Test
    fun `nullable 필드는 값이 없을 때도 키 자체는 남아 null로 응답한다`() {
        // freshStrategy()는 예산 한계를 설정하지 않는다(StrategyDraft 기본값) — nullable
        // 필드가 실제로 null을 낸다.
        val response =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.GET,
                HttpEntity<Void>(authorizedHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.body?.containsKey("minBudgetWon") shouldBe true
        response.body?.get("minBudgetWon") shouldBe null
    }
}

/** D-6A1-38 이 허용하는 스칼라 타입 — 그 밖은 전부 [isFlatPropertyDefinition]이 거부한다. */
private val SCALAR_PROPERTY_TYPES = setOf("string", "integer", "number", "boolean")

/** OpenAPI/JSON Schema 합성·참조 어휘 — 하나라도 있으면 그 정의는 평탄하지 않다. */
private val NON_FLAT_KEYS = setOf("\$ref", "oneOf", "allOf", "anyOf", "additionalProperties")

/**
 * D-6A1-38 — 허용 목록으로 뒤집은 평탄 속성 판정. 속성 정의가 **스칼라 타입**이거나
 * **아이템이 스칼라인 배열**일 때만 `true`다. `$ref`·`oneOf`·`allOf`·`anyOf`·
 * `additionalProperties`가 있거나 `type` 자체가 없거나 `type: object`이거나 배열의
 * 아이템이 다시 배열·object·참조이면 전부 `false` — 새로 생기는 합성 어휘를 열거할
 * 필요가 없다(차단 목록에는 종점이 없지만 허용 목록은 종점이 있다).
 */
private fun isFlatPropertyDefinition(definition: Map<String, Any?>): Boolean {
    if (NON_FLAT_KEYS.any(definition::containsKey)) return false
    return when (definition["type"]) {
        in SCALAR_PROPERTY_TYPES -> true
        "array" -> isFlatArrayItems(definition["items"])
        else -> false
    }
}

@Suppress("UNCHECKED_CAST")
private fun isFlatArrayItems(items: Any?): Boolean {
    val itemsDefinition = items as? Map<String, Any?> ?: return false
    if (NON_FLAT_KEYS.any(itemsDefinition::containsKey)) return false
    return itemsDefinition["type"] in SCALAR_PROPERTY_TYPES
}
