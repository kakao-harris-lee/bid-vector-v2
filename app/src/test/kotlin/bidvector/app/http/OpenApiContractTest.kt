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
     * code-review r1 M-3 · verifier r1 F4 — **문서가 선언하는 무인증 자리와 구현의 허용 목록이
     * 같은 집합인가.**
     *
     * 이 축을 operation 이름 목록으로 적지 않는다(그런 목록에는 종점이 없다) — 문서에서
     * `security: []` 인 operation 을 **모아서** 필터 상수와 등식으로 본다.
     *
     * 앞 판은 그 모음을 **상태 변경 operation 으로 한정**해서, 읽기 operation 에 `security: []` 를
     * 더해도 초록이었다(verifier 변이 M21b). 문서는 무인증이라 말하고 필터는 401 을 내는 drift 다 —
     * 지금은 **메서드와 무관하게 전수**를 본다.
     */
    @Test
    fun `문서가 무인증으로 여는 operation 집합은 기본 거부 필터의 허용 목록과 같다 — 메서드 무관 전수`() {
        val openInDocument = operationSecurity().filterValues { it?.isEmpty() == true }.keys

        openInDocument shouldBe RequestAuthenticationFilter.OPEN_ROUTES
    }

    /**
     * code-review r1 M-3 — **상태 변경 operation 은 전부 CSRF 스킴을 쿠키 경로와 묶어 선언한다.**
     * 문서의 기본 요구에는 `browserSession` 단독 대안이 있어서(읽기의 기본값), 그것을 물려받은
     * 쓰기 operation 은 「쿠키만 있으면 된다」고 말한다 — 구현은 그 반대다.
     *
     * 상태를 바꾸는 메서드의 정의를 **production 상수의 여집합**으로 받는다
     * ([BrowserSessionAuthenticator.CSRF_EXEMPT_METHODS]) — 목록을 두 벌 두면 그 둘이 갈린다.
     */
    @Test
    fun `상태 변경 operation 은 쿠키 경로에 CSRF 스킴을 묶어 선언한다 — 쿠키 단독 대안 0`() {
        val offenders =
            authenticatedOperations()
                .filterKeys { stateChanging(it) }
                .filterNot { (_, requirement) -> bindsCsrfToSession(requirement) }
                .keys

        offenders shouldBe emptySet()
    }

    /**
     * code-review r2 L-8 — **자격 헤더 대안의 소실도 잡는다.** 앞 판의 술어는 쿠키 쪽만 봐서,
     * `operatorCredential` 대안이 사라져도 초록이었다. 그러면 문서가 구현보다 **좁아지고**
     * (필터는 여전히 자격 헤더를 받는다) 기계 호출자가 문서만 보고 막힌 줄 안다 — M-3 이 닫은
     * 방향의 반대쪽이다.
     */
    @Test
    fun `인증을 요구하는 operation 은 전부 자격 헤더 대안을 남긴다 — 문서가 구현보다 좁아지지 않는다`() {
        val missing =
            authenticatedOperations()
                .filterNot { (_, requirement) -> alternativesOf(requirement).contains(setOf(CREDENTIAL_SCHEME)) }
                .keys

        missing shouldBe emptySet()
    }

    /**
     * verifier r1 F4 — **읽기 operation 도 쿠키 경로를 남긴다.** 상태 변경 쪽만 보면 읽기에서
     * `browserSession` 이 사라져도 초록이고, 그러면 화면이 쿠키로 읽을 수 없다고 문서가 말한다.
     */
    @Test
    fun `읽기 operation 은 쿠키 경로 대안을 남긴다 — CSRF 는 요구하지 않는다`() {
        val missing =
            authenticatedOperations()
                .filterKeys { !stateChanging(it) }
                .filterNot { (_, requirement) -> alternativesOf(requirement).contains(setOf(SESSION_SCHEME)) }
                .keys

        missing shouldBe emptySet()
    }

    /** 쿠키 경로를 쓰려면 CSRF 를 함께 요구해야 하고, 쿠키 **단독** 대안이 있으면 안 된다. */
    private fun bindsCsrfToSession(requirement: List<Map<String, Any?>>?): Boolean {
        val alternatives = alternativesOf(requirement)
        return alternatives.contains(setOf(SESSION_SCHEME, CSRF_SCHEME)) &&
            !alternatives.contains(setOf(SESSION_SCHEME))
    }

    private fun alternativesOf(requirement: List<Map<String, Any?>>?): List<Set<String>> =
        requirement.orEmpty().map { it.keys }

    /** 무인증으로 연 자리를 뺀 나머지 — 「인증을 요구한다」고 문서가 말하는 operation 전부. */
    private fun authenticatedOperations(): Map<String, List<Map<String, Any?>>?> =
        operationSecurity().filterKeys { it !in RequestAuthenticationFilter.OPEN_ROUTES }

    private fun stateChanging(route: String): Boolean =
        route.substringBefore(' ') !in BrowserSessionAuthenticator.CSRF_EXEMPT_METHODS

    /**
     * **모든 operation** 의 유효 보안 요구 — operation 자신의 선언이 없으면 문서 최상위 기본을
     * 물려받는다(OpenAPI 3 의 규칙 그대로). 키는 필터의 경로 키와 같은 모양이다.
     */
    private fun operationSecurity(): Map<String, List<Map<String, Any?>>?> {
        val paths = spec["paths"] as Map<String, Any?>
        val fallback = spec["security"] as? List<Map<String, Any?>>
        return paths.entries
            .flatMap { (path, item) ->
                val pathItem = item as Map<String, Any?>
                pathItem.keys
                    .filter { it in OPENAPI_METHODS }
                    .map { method ->
                        val operation = pathItem[method] as Map<String, Any?>
                        "${method.uppercase()} $path" to
                            ((operation["security"] as? List<Map<String, Any?>>) ?: fallback)
                    }
            }.toMap()
    }

    private companion object {
        /** OpenAPI 3 path item 의 메서드 고정 필드 — `parameters`·`summary` 같은 형제와 가른다. */
        val OPENAPI_METHODS =
            setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

        const val SESSION_SCHEME = "browserSession"
        const val CSRF_SCHEME = "operatorCsrf"
        const val CREDENTIAL_SCHEME = "operatorCredential"
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
