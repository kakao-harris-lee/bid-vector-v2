package bidvector.app.http

import bidvector.adapters.evaluation.CandidateCapExceededException
import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.StrategyViolation
import bidvector.strategy.validate
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
import java.time.LocalDate

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

    @Autowired
    private lateinit var candidateSource: EmptyCandidateSource

    @BeforeEach
    fun resetFixture() {
        strategyRepository.strategy = freshStrategy()
        strategyRepository.loadFailure = null
        candidateSource.failure = null
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

    private fun requiredKeys(schemaName: String): Set<String> =
        ((schema(schemaName)["required"] as? List<String>) ?: emptyList()).toSet()

    /**
     * D-6A3-20(검토 라운드 1 contract-keeper R1) — `paths.<path>.<method>.responses` 의
     * 상태 코드 **키 집합**. verifier M7a/M7b/M7d(409·401 선언 삭제)가 이 test 를 RED로
     * 만든다 — HTTP 호출 없이 문서 자신만 본다(response 개별 키 대조 test 들과 분담).
     */
    @Suppress("UNCHECKED_CAST")
    private fun responseStatusCodes(
        path: String,
        method: String,
    ): Set<String> {
        val pathItem = (spec["paths"] as Map<String, Any?>)[path] as Map<String, Any?>
        val operation = pathItem[method] as Map<String, Any?>
        return (operation["responses"] as Map<String, Any?>).keys
    }

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

    // D-6A3-11 — 평가 dry-run endpoint(계약 갱신 없음, 이 slice가 처음 연다)도 같은 대조를 받는다.

    private fun postDryRun(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return restTemplate.exchange(
            url("/api/evaluation-dry-runs"),
            HttpMethod.POST,
            HttpEntity(body, authorizedHeaders()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    @Test
    fun `평가 dry-run 200 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        // 후보가 없어도(candidateCount 0) 응답 스키마는 항상 아홉 키를 모두 낸다.
        strategyRepository.strategy = strategyWithMaxActiveBids(10)

        val response = postDryRun(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 200
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("EvaluationDryRunResponse")
        response.body?.values?.none(::containsNestedObject) shouldBe true
    }

    private fun strategyWithMaxActiveBids(maxActiveBids: Int): bidvector.strategy.OperatorStrategy {
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val draft = StrategyDraft(focusCategories = listOf("CAT-1"), maxActiveBids = maxActiveBids)
        return when (val result = validate(draft, StrategyRevision(1), policy)) {
            is StrategyValidation.Valid -> result.strategy
            is StrategyValidation.Invalid -> error("test fixture가 유효하지 않다: ${result.violations}")
        }
    }

    @Test
    fun `평가 dry-run 409 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        // freshStrategy()는 maxActiveBids 를 설정하지 않는다 — fail-closed 409.
        val response = postDryRun(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 409
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.MAX_ACTIVE_BIDS_NOT_CONFIGURED
    }

    @Test
    fun `평가 dry-run 400 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        val response = postDryRun(mapOf("currentActiveBids" to -1))

        response.statusCode.value() shouldBe 400
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    /**
     * D-6A3-20(검토 라운드 1 verifier R5) — 이 path 의 401·500 도 `ErrorBody` 키 등식으로
     * 대조한다. `/api/strategy` 는 이미 대조되지만(위 test), 이 path 는 여태 200·400·409
     * 만 잰다 — D-6A1-27 전수 test(GET 으로만 때린다) 뒤 실제 메서드(POST)에 대한 증거이기도
     * 하다.
     */
    @Test
    fun `평가 dry-run 401 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        @Suppress("UNCHECKED_CAST")
        val response =
            restTemplate.exchange(
                url("/api/evaluation-dry-runs"),
                HttpMethod.POST,
                HttpEntity(mapOf("currentActiveBids" to 0), HttpHeaders()),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 401
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("ErrorBody")
    }

    @Test
    fun `평가 dry-run 500 응답도 ErrorBody 계약과 일치한다`() {
        strategyRepository.loadFailure = {
            InvalidStoredStrategyException(listOf(StrategyViolation.CandidateLimitNotPositive))
        }

        val response = postDryRun(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 500
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.INVALID_STORED_STRATEGY
    }

    /**
     * D-6A3-20 — D-6A3-7 의 HTTP 쪽 절반(verifier M8: `ErrorMapping` 의 `CandidateCapExceededException`
     * 행을 지우면 `@ExceptionHandler(Throwable)` 폴백으로 떨어져 500이 되는데도 이전까지는
     * 어떤 HTTP test 도 이 경로를 때리지 않아 전건 초록이었다). `EmptyCandidateSource.failure`
     * 로 그 예외를 직접 낸다 — production 배선(`JdbcCandidateSource`)의 SQL 실 상한 도달은
     * adapter 층(`JdbcCandidateSourceTest`)의 몫이다.
     */
    @Test
    fun `평가 dry-run 409 CANDIDATE_CAP_EXCEEDED 응답의 최상위 키 집합이 계약과 완전히 일치한다`() {
        strategyRepository.strategy = strategyWithMaxActiveBids(10)
        candidateSource.failure = { CandidateCapExceededException(100) }

        val response = postDryRun(mapOf("currentActiveBids" to 0))

        response.statusCode.value() shouldBe 409
        (response.body?.keys ?: emptySet()) shouldBe propertyKeys("ErrorBody")
        response.body?.get("code") shouldBe ErrorCode.CANDIDATE_CAP_EXCEEDED
    }

    /**
     * D-6A3-20(검토 라운드 1 contract-keeper V2) — `ErrorCode` 의 상수 집합과 문서
     * `ErrorBody.code` 의 `enum` 이 **양방향** 등식이다. 한쪽에만 코드를 더하면(리플렉션이
     * `ErrorCode`를 직접 읽으므로 이 목록을 손으로 복제하지 않는다) RED가 된다.
     */
    @Test
    fun `ErrorCode 상수 집합과 문서 enum 이 양방향으로 같다`() {
        val declaredCodes =
            ErrorCode::class.java.fields
                .filter { it.type == String::class.java }
                .map { it.get(null) as String }
                .toSet()

        @Suppress("UNCHECKED_CAST")
        val documentedCodes =
            (schema("ErrorBody")["properties"] as Map<String, Any?>)
                .let { properties -> properties["code"] as Map<String, Any?> }
                .let { codeDefinition -> codeDefinition["enum"] as List<String> }
                .toSet()

        documentedCodes shouldBe declaredCodes
    }

    /**
     * D-6A3-20(검토 라운드 1 contract-keeper R1) — `paths` 의 상태 코드 **선언 집합**이
     * 문서 자신 안에서 기대값과 같다(verifier M7a·M7b·M7d — 409·401 을 지워도 이전에는
     * 어떤 test 도 「지워졌다」를 몰랐다). HTTP 호출 없이 YAML 만 본다.
     */
    @Test
    fun `각 path 의 상태 코드 선언 집합이 기대값과 같다`() {
        responseStatusCodes("/api/strategy", "get") shouldBe setOf("200", "401", "500")
        responseStatusCodes("/api/evaluation-dry-runs", "post") shouldBe setOf("200", "400", "401", "409", "500")
        responseStatusCodes("/{unmatched}", "get") shouldBe setOf("404")
    }

    /**
     * D-6A3-20(검토 라운드 1 contract-keeper R2) — 요청 스키마의 **속성 키 집합**·**필수
     * 집합**이 DTO 필드와 같다(verifier M7c — 요청 스키마에 필드를 더해도 이전에는 어떤
     * test 도 몰랐다). 기존 `/api/strategy`는 요청 본문이 없어 이 축이 없다.
     *
     * **`kotlin-reflect` 없이 순수 Java reflection만 쓴다**(app 모듈에 그 의존이 없다 —
     * 새로 들이지 않는다, 재사용 우선). Kotlin `data class`의 프로퍼티는 생성자 파라미터와
     * 같은 이름의 인스턴스 필드로 컴파일된다 — non-null `Int`는 primitive `int` 필드가
     * 되므로 `isPrimitive`가 곧 「필수」다(이 DTO가 nullable 필드를 가지면 이 휴리스틱을
     * 다시 봐야 한다는 사실도 이 test가 실패로 알린다).
     */
    @Test
    fun `평가 dry-run 요청 스키마는 DTO 필드와 속성·필수 집합이 같다`() {
        val fields = EvaluationDryRunRequest::class.java.declaredFields.filterNot { it.isSynthetic }
        val parameterNames = fields.map { it.name }.toSet()
        val requiredParameterNames = fields.filter { it.type.isPrimitive }.map { it.name }.toSet()

        propertyKeys("EvaluationDryRunRequest") shouldBe parameterNames
        requiredKeys("EvaluationDryRunRequest") shouldBe requiredParameterNames
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
