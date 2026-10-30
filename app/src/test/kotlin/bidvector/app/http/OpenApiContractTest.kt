package bidvector.app.http

import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.strategy.StrategyViolation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.yaml.snakeyaml.constructor.DuplicateKeyException

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
 * **D-6A1-44 시정 — 순회 루트를 `components.schemas` 고정에서 문서 전체로 넓힌다.**
 * D-6A1-38 은 [isFlatPropertyDefinition] 술어 자체는 제대로 뒤집었지만, 그 술어를
 * **어디에 적용하는가**가 `components.schemas`만 훑는 것으로 고정돼 있었다. verifier
 * 실측(MUT-D6): `paths`의 **인라인 응답 스키마**에 중첩 object·object 배열을 심어도
 * `components.schemas` 밖이라는 이유만으로 전건 `check`가 초록이었다 — 게이트가 방금
 * 닫았다고 선언한 바로 그 형태였다. **처방은 위치를 하나씩 늘리는 것(`paths...schema`를
 * 손으로 추가하는 것)이 아니다** — 그것은 이 slice가 이미 세 번 겪은 「위치 술어에는
 * 종점이 없다」병의 재발이다. 대신 [collectPropertyDefiningSchemas]가 **문서 트리
 * 전체를 재귀로 훑어 `properties` 키를 가진 모든 Map**을 스키마 정의로 수집한다 —
 * OpenAPI/JSON Schema에서 "이 자리는 속성 정의를 가진 객체 스키마다"를 가르는 유일한
 * 구조적 표지는 `properties` 키의 존재뿐이고, 그 키가 `components.schemas` 아래 있든
 * `paths...content...schema` 아래 있든 배열 `items` 아래 있든 무관하다. 문서는
 * 유한하므로 전체 재귀에는 다음 칸이 없다(`paths 의 인라인 응답 스키마에 심은 위반도
 * 순회가 잡는다` test가 위치 자체의 폐쇄를 합성 spec으로 고정한다).
 *
 * 새 라이브러리(swagger-parser 등)를 들이지 않는다 — 계약이 얕아 SnakeYAML(이미 app의
 * test 의존)로 raw Map 순회만으로 충분하다(preflight 조사·재사용 우선).
 *
 * **D-6A3-20 — 평가 dry-run endpoint(`/api/evaluation-dry-runs`) 전용 대조는
 * `OpenApiDryRunContractTest`로 분리했다**(sizeGate, v2-지침서 §5 파일당 500줄 —
 * `PredictionContractTest`→`PredictionAdditiveContractTest` 선례와 같은 관심사 경계).
 * YAML 로딩·스키마 조회 헬퍼는 `OpenApiSpecSupport.kt`를 공유한다.
 */
@Suppress("UNCHECKED_CAST")
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND,
        PROP_NO_STATIC_RESOURCE_MAPPINGS,
        PROP_NO_MANAGEMENT_SERVER,
    ],
)
class OpenApiContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var strategyRepository: TestStrategyRepository

    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = freshStrategy()
        strategyRepository.loadFailure = null
    }

    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    /**
     * 문서 전체(D-6A1-44)에서 속성을 정의하는 스키마를 전부 찾아 [isFlatPropertyDefinition]이
     * **허용하지 않는** 속성(스키마 자신 말고)을 모은다.
     */
    private fun collectNonFlatProperties(): List<String> =
        collectPropertyDefiningSchemas(spec, "spec").flatMap { (location, schemaBody) ->
            val properties = schemaBody["properties"] as Map<String, Any?>
            properties
                .filterNot { (_, definition) -> isFlatPropertyDefinition(definition as Map<String, Any?>) }
                .map { (propertyName, _) -> "$location.properties.$propertyName" }
        }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

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

    /**
     * **양성 대조 — D-7C4C-6 의 깊이 1 object 확장이 열지 않는 모양.**
     *
     * 확장의 위험은 「한 겹을 열면 두 겹도 열리는가」다. 깊이를 **세는 인자**를 두지 않고 호출
     * 그래프로 닫았으므로(object 층 술어가 스칼라 층 술어만 부르고 자기를 다시 부르지 않는다)
     * 깊이 2 를 표현할 함수가 없다 — 이 test 는 그 구성이 모양마다 실제로 그렇게 판정하는지 잰다.
     *
     * `$ref`(속성 · `items`) · 배열의 배열 · `type: object` 단독은 **위 test 가 이미 고정**한다 —
     * 같은 단언을 두 벌 두지 않는다. 여기서는 확장이 새로 건드리는 축만 센다.
     */
    @Test
    fun `깊이 1 object 확장은 깊이 2 · 자유 맵 · 합성 · 빈 properties · nullable · 미지 키를 거부한다`() {
        val scalar = mapOf("type" to "string")
        val depthOne = mapOf("type" to "object", "properties" to mapOf("a" to scalar))

        // 깊이 2 — 세 자리(속성 안 object · 아이템 안 object · 아이템 안 object 배열).
        isFlatPropertyDefinition(objectWithProperty(depthOne)) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(objectWithProperty(depthOne))) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(objectWithProperty(arrayWithItems(depthOne)))) shouldBe false
        // 자유 맵 — 아이템이 자유 맵인 꼴(속성 자리는 위 test 가 고정한다).
        isFlatPropertyDefinition(arrayWithItems(depthOne + mapOf("additionalProperties" to scalar))) shouldBe false
        // 합성 — object 를 감싸 깊이를 숨기는 세 어휘(속성 · 아이템 둘 다).
        isFlatPropertyDefinition(mapOf("allOf" to listOf(depthOne))) shouldBe false
        isFlatPropertyDefinition(mapOf("oneOf" to listOf(scalar, depthOne))) shouldBe false
        isFlatPropertyDefinition(mapOf("anyOf" to listOf(scalar, depthOne))) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(mapOf("allOf" to listOf(depthOne)))) shouldBe false
        // 계약이 없는 object — 빈 `properties` 와 `properties` 자체가 없는 꼴.
        isFlatPropertyDefinition(mapOf("type" to "object", "properties" to emptyMap<String, Any?>())) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(mapOf("type" to "object"))) shouldBe false
        // 두 모양을 받는 속성 — nullable object. 키 허용 목록이 닫는다(스칼라의 nullable 은 그대로 열려 있다).
        isFlatPropertyDefinition(depthOne + mapOf("nullable" to true)) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(depthOne + mapOf("nullable" to true))) shouldBe false
        // 미지의 키 — 차단 목록을 늘리는 대신 키 허용 목록이 기본 거부로 잡는다.
        val pattern = mapOf("patternProperties" to mapOf("^x" to scalar))
        isFlatPropertyDefinition(depthOne + pattern) shouldBe false
        isFlatPropertyDefinition(arrayWithItems(depthOne + pattern)) shouldBe false
    }

    /**
     * **음성 대조 — D-7C4C-1 이 여는 유일한 모양(`awaiting` 짝 배열)이 통과한다.**
     *
     * 이 단언이 붉은 동안은 체크리스트 operation 을 계약에 적을 수 없다(`OPEN-7C4-AWAITING-CHECKLIST`).
     * 스칼라 · 스칼라 배열이 그대로 통과하는 것은 위 test 가 고정한다.
     */
    @Test
    fun `awaiting 짝 배열과 깊이 1 object 속성은 통과한다 — 음성 대조`() {
        val itemName = mapOf("type" to "string", "enum" to listOf("TRADE_NAME", "LICENSES"))
        val pair =
            mapOf(
                "type" to "object",
                "required" to listOf("item", "prerequisite"),
                "properties" to mapOf("item" to itemName, "prerequisite" to itemName),
            )

        isFlatPropertyDefinition(arrayWithItems(pair)) shouldBe true
        // 서술 키 하나는 계약 문서가 실제로 쓰는 자리라 허용 목록에 든다.
        isFlatPropertyDefinition(
            arrayWithItems(pair + mapOf("description" to "항목과 선행 항목의 짝")),
        ) shouldBe true
        // 자리로 가르지 않는다 — 같은 모양이 속성 자리에 와도 같은 판정이다(위치 술어는 종점이 없다).
        isFlatPropertyDefinition(pair) shouldBe true
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

    /**
     * D-6A1-44 닫힘 판정 — `paths`의 **인라인 응답 스키마**(`components.schemas` 밖)에
     * ① 중첩 object 속성 ② object 배열 속성 ③ `$ref` 속성을 심어도 [collectNonFlatProperties]가
     * 셋 다 잡는지를 **합성 spec**으로 잠근다. 실 `openapi.yaml`은 오늘 위반이 없어(파일을
     * 고쳐도 통과하므로) 위치 폐쇄 자체는 합성 spec 없이는 고정할 수 없다(verifier MUT-D6
     * 재현).
     */
    @Test
    fun `paths 의 인라인 응답 스키마에 심은 위반도 순회가 잡는다`() {
        // 아래에서 위로 조립한다 — 한 표현식으로 쓰면 들여쓰기가 깊어져 줄 길이 한도를 넘는다.
        val innerScalar = mapOf("type" to "string")
        val nestedObjectProperty = mapOf("type" to "object", "properties" to mapOf("inner" to innerScalar))
        val objectItems = mapOf("type" to "object", "properties" to mapOf("inner" to innerScalar))
        val arrayOfObjectProperty = mapOf("type" to "array", "items" to objectItems)
        val refProperty = mapOf("\$ref" to "#/components/schemas/Nested")
        val inlineResponseSchema =
            mapOf(
                "type" to "object",
                "properties" to
                    mapOf(
                        "nestedObject" to nestedObjectProperty,
                        "arrayOfObject" to arrayOfObjectProperty,
                        "refProperty" to refProperty,
                    ),
            )
        val content = mapOf("application/json" to mapOf("schema" to inlineResponseSchema))
        val responses = mapOf("200" to mapOf("content" to content))
        val getOperation = mapOf("responses" to responses)
        val syntheticSpec: Map<String, Any?> =
            mapOf(
                "paths" to mapOf("/probe" to mapOf("get" to getOperation)),
                "components" to mapOf("schemas" to emptyMap<String, Any?>()),
            )

        val violations =
            collectPropertyDefiningSchemas(syntheticSpec, "spec").flatMap { (location, schemaBody) ->
                val properties = schemaBody["properties"] as Map<String, Any?>
                properties
                    .filterNot { (_, definition) -> isFlatPropertyDefinition(definition as Map<String, Any?>) }
                    .map { (propertyName, _) -> "$location.properties.$propertyName" }
            }

        violations.any { it.contains("nestedObject") } shouldBe true
        violations.any { it.contains("arrayOfObject") } shouldBe true
        violations.any { it.contains("refProperty") } shouldBe true
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
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("StrategyReadResponse")
        // D-6A1-20 ⓑ — 문서(위 test)뿐 아니라 **실제 응답값**도 평탄한지 잰다(배열 축 포함).
        response.body?.values?.none(::containsNestedObject) shouldBe true
    }

    @Test
    fun `401 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response =
            restTemplate.getForEntity(url("/api/strategy"), Map::class.java) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 401
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
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
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
    }

    /**
     * D-6A1-32 — 계약(`responses.500`)에 적힌 상태를 실제로 대조한다. 500을
     * `RequestAuditFilterTest`가 code/message만 개별 확인하면 계약 대조 경로에는 없다
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
        (response.body?.keys ?: emptySet()) shouldBe spec.propertyKeys("ErrorBody")
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

    /**
     * code-review r3 Q3 — **로더가 중복 키를 거부한다**(계약 D-7D-6). 양성 대조다: 실제 계약
     * 문서가 아니라 **합성 문서**로 재서, 「거부한다」가 문서의 현재 상태와 무관하게 성립함을
     * 보인다. 이 가드가 없으면 뒤 값이 조용히 이기고, 선언을 **더하는** 변이가 초록이 된다
     * (수정 r2 의 M27 이 그랬다).
     */
    @Test
    fun `로더는 중복 키를 거부한다 — 뒤 값이 조용히 이기지 않는다`() {
        val duplicated =
            """
            paths:
              /a:
                get:
                  security: []
                  security:
                    - operatorCredential: []
            """.trimIndent()
        val single =
            """
            paths:
              /a:
                get:
                  security:
                    - operatorCredential: []
            """.trimIndent()

        shouldThrow<DuplicateKeyException> { parseOpenApiSpec(duplicated) }
        // 중복만 뺀 같은 모양은 그대로 읽힌다 — 거부가 구문이나 들여쓰기 탓이 아니다.
        parseOpenApiSpec(single) shouldNotBe null
    }

    /**
     * 실제 계약 문서에 중복 키가 **없다**(위 가드가 그것을 지킨다). 이 단언이 붉으면 문서를
     * 고치라는 뜻이고, 위 양성 대조와 함께 보면 「가드가 산다」와 「문서가 통과한다」가 갈린다.
     */
    @Test
    fun `계약 문서 자체가 중복 키 없이 읽힌다`() {
        spec.keys.isNotEmpty() shouldBe true
    }
}

/**
 * D-6A1-44 — 문서 트리 전체를 재귀로 훑어 **속성을 정의하는 스키마**(`properties` 키를
 * 가진 Map)를 위치 무관하게 전부 찾는다. `components.schemas` 아래든 `paths...schema`
 * 아래든 배열 `items` 아래든, OpenAPI/JSON Schema에서 그 구조적 표지는 `properties`
 * 키의 존재뿐이다 — 위치를 손으로 나열하지 않고(위치 술어는 종점이 없다) **재귀 하나로**
 * 문서가 유한하다는 사실을 그대로 이용한다. `location`은 디버깅용 경로 문자열이다.
 */
@Suppress("UNCHECKED_CAST")
private fun collectPropertyDefiningSchemas(
    node: Any?,
    location: String,
): List<Pair<String, Map<String, Any?>>> {
    val found = mutableListOf<Pair<String, Map<String, Any?>>>()
    when (node) {
        is Map<*, *> -> {
            val map = node as Map<String, Any?>
            if (map["properties"] is Map<*, *>) {
                found += location to map
            }
            map.forEach { (key, value) -> found += collectPropertyDefiningSchemas(value, "$location.$key") }
        }

        is List<*> -> {
            node.forEachIndexed { index, item ->
                found += collectPropertyDefiningSchemas(item, "$location[$index]")
            }
        }

        else -> {}
    }
    return found
}

/** 대조용 조립 — 한 겹 object 로 감싼다(한 표현식으로 쓰면 줄 길이 한도를 넘는다). */
private fun objectWithProperty(property: Map<String, Any?>): Map<String, Any?> =
    mapOf("type" to "object", "properties" to mapOf("a" to property))

/** 대조용 조립 — 배열로 감싼다. */
private fun arrayWithItems(items: Map<String, Any?>): Map<String, Any?> =
    mapOf("type" to "array", "items" to items)

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
    val itemsDefinition = items as? Map<String, Any?>
    return itemsDefinition != null &&
        NON_FLAT_KEYS.none(itemsDefinition::containsKey) &&
        itemsDefinition["type"] in SCALAR_PROPERTY_TYPES
}
