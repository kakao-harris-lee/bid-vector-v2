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
 * (D-6A1-20 ⓑ — 지금 컨트롤러 DTO가 평탄하다는 사실을 이 test가 잠근다: 스키마 어디든
 * `type: object` 속성이 생기면 이 test가 즉시 실패한다).
 *
 * **D-6A1-30 시정 — 깊이 방어가 배열 안 object를 못 봤다**(contract-keeper·code-reviewer
 * 독립 수렴). 정적 검사(`collectNestedObjectProperties`)는 `type: object` 속성만 보고
 * `type: array, items: {type: object}`는 놓쳤다. 런타임 검사(`values.none { it is Map }`)도
 * 값이 `List<Map<*,*>>`이면 `it`이 List라 `false`를 내 놓쳤다. 둘 다 배열 축까지 보도록
 * 넓힌다.
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
     * 모든 스키마를 재귀로 훑어 `type: object`인 **속성**(스키마 자신 말고)이 없는지 잰다
     * — object 자체뿐 아니라 `type: array`의 `items`가 object인 경우(D-6A1-30)도 본다.
     */
    private fun collectNestedObjectProperties(): List<String> {
        val schemas = (spec["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>
        return schemas.flatMap { (schemaName, schemaBody) ->
            val properties = (schemaBody as Map<String, Any?>)["properties"] as Map<String, Any?>
            properties
                .filter { (_, definition) -> isNestedObjectDefinition(definition as Map<String, Any?>) }
                .map { (propertyName, _) -> "$schemaName.$propertyName" }
        }
    }

    private fun isNestedObjectDefinition(definition: Map<String, Any?>): Boolean =
        when (definition["type"]) {
            "object" -> true
            "array" -> (definition["items"] as? Map<String, Any?>)?.get("type") == "object"
            else -> false
        }

    /** D-6A1-30 — 값 자체가 object이거나(Map), object의 배열(List<Map>)이면 평탄하지 않다. */
    private fun containsNestedObject(value: Any?): Boolean =
        when (value) {
            is Map<*, *> -> true
            is List<*> -> value.any { it is Map<*, *> }
            else -> false
        }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    @Test
    fun `D-6A1-20 ⓑ — 계약의 어떤 스키마도 중첩 object 속성을 갖지 않는다(배열 축 포함)`() {
        collectNestedObjectProperties() shouldBe emptyList()
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
