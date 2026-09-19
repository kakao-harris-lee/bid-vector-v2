package bidvector.app.http

import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.yaml.snakeyaml.Yaml

/**
 * 우회 (6) 폐쇄 — D-6A1-8(수작성 단일 출처) · D-6A1-20(대조 깊이). 경로 이름만 보지 않고
 * **상태 코드 + 응답 JSON top-level 키 집합**을 `Set.equals`로 완전 일치 대조한다(부분
 * 포함이 아니다 — 여분 필드가 생겨도 실패). 추가로 **스키마 자신이 평탄함을 단언**한다
 * (D-6A1-20 ⓑ — 지금 컨트롤러 DTO가 평탄하다는 사실을 이 test가 잠근다: 스키마 어디든
 * `type: object` 속성이 생기면 이 test가 즉시 실패한다).
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
    }

    private val spec: Map<String, Any?> by lazy {
        val specFile = File(requireNotNull(System.getProperty("bidvector.openapi.spec")) { "bidvector.openapi.spec 시스템 프로퍼티가 없다" })
        Yaml().load<Map<String, Any?>>(specFile.readText())
    }

    private fun schema(name: String): Map<String, Any?> =
        ((spec["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>)[name] as Map<String, Any?>

    private fun propertyKeys(schemaName: String): Set<String> = (schema(schemaName)["properties"] as Map<String, Any?>).keys

    /** 모든 스키마를 재귀로 훑어 `type: object`인 **속성**(스키마 자신 말고)이 없는지 잰다. */
    private fun collectNestedObjectProperties(): List<String> {
        val schemas = (spec["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>
        return schemas.flatMap { (schemaName, schemaBody) ->
            val properties = (schemaBody as Map<String, Any?>)["properties"] as Map<String, Any?>
            properties.filter { (_, definition) -> (definition as Map<String, Any?>)["type"] == "object" }
                .map { (propertyName, _) -> "$schemaName.$propertyName" }
        }
    }

    private fun authorizedHeaders(): HttpHeaders = HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

    @Test
    fun `D-6A1-20 ⓑ — 계약의 어떤 스키마도 중첩 object 속성을 갖지 않는다`() {
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
        // D-6A1-20 ⓑ — 문서(위 test)뿐 아니라 **실제 응답값**도 평탄한지 잰다. 문서만
        // 맞고 실제 DTO가 갈라지는 사각을 막는다 — 값 자체가 Map(중첩 object)이면 실패.
        response.body?.values?.none { it is Map<*, *> } shouldBe true
    }

    @Test
    fun `401 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response = restTemplate.getForEntity(url("/api/strategy"), Map::class.java) as ResponseEntity<Map<String, Any?>>

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
