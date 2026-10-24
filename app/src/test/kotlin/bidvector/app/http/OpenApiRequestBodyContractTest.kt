package bidvector.app.http

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * **문서의 `requestBody` 유무 ↔ 매핑의 `@RequestBody` 유무 — 집합 등식**(M7/7B 계약 D-7B-3 결정 ④).
 *
 * 왜 필요한가: `resumeBidCase` 는 문서에 본문이 없는데 구현은 `{}` 를 요구했다. 문서대로 부르면 400 이고,
 * 생성 타입(`operator-api.ts`)도 본문 없는 op 로 나가므로 그 문서로 만든 클라이언트는 그 op 에서 늘
 * 실패한다. 보안 선언 test 도, 본문 거부 전수 test 도 문서의 본문 선언과 매핑의 서명을 맞대지 않아서
 * 어느 게이트도 그 어긋남을 보지 못했다.
 *
 * 그래서 **양쪽을 같은 키 모양(`METHOD /path`)으로 모아 등식**으로 본다 — 한쪽에만 본문이 있으면
 * 어느 방향이든 붉다(문서가 본문을 적고 구현이 안 받거나, 구현이 받는데 문서가 안 적거나).
 * 판정은 핸들러 메서드의 **서명**이다(경로 목록을 손으로 적지 않는다).
 */
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND,
        PROP_NO_STATIC_RESOURCE_MAPPINGS,
        PROP_NO_MANAGEMENT_SERVER,
    ],
)
@Suppress("UNCHECKED_CAST")
class OpenApiRequestBodyContractTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    @Autowired
    private lateinit var access: InMemoryBrowserAccessTransaction

    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

    @Test
    fun `본문을 선언한 operation 집합과 본문을 받는 매핑 집합이 같다`() {
        val documented = documentedOperations()
        documented.keys.shouldNotBeEmpty()

        val withBodyInDocument = documented.filterValues { it }.keys
        val withBodyInMapping = mappedOperations().filterValues { it }.keys

        withBodyInMapping shouldBe withBodyInDocument
    }

    /**
     * 양성 대조 — 문서의 operation 이 매핑에 있다(키 모양이 어긋나 등식이 빈 집합끼리 맞는 일이 없게).
     * 예외는 미매핑 경로 템플릿 하나다 — 404 응답 모양을 문서화하려는 자리이고 매핑이 아니다(D-6A1-21).
     */
    @Test
    fun `문서의 operation 은 미매핑 템플릿 하나를 빼고 전부 매핑에 있다 — 키 모양 양성 대조`() {
        val mapped = mappedOperations().keys

        documentedOperations().keys.filterNot { it in mapped }.toSet() shouldBe setOf("GET /{unmatched}")
    }

    /**
     * 검토 r2 V2-N2 · r4 V4-N2 — **주석이 아니라 행동으로**: 문서가 본문을 적지 않은 상태 변경 operation 전부를 **본문 없이**,
     * 그리고 **그 operation 이 받아들여지는 상태에서** 불러, 응답 상태가 **문서의 2xx 와 정확히 같다**고 본다. 위 등식은
     * `@RequestBody` 주석에 걸려 있어 주석 없이 본문을 요구하는 핸들러(r1 H3 의 결함 모양)가 지나간다 — 이 test 는 모집단을
     * 문서에서 **도출**하고 실제로 부른다. r3 판은 「401 · 404 아님 + 400 · 415 아님」을 열거해, 전이 거부(409)에서 멈춘 응답을
     * 본체 도달로 셌고(N2a — 받아들여질 때만 본문을 요구) 403 · 422 · 5xx 가 지나갔다. 성공 응답과의 등식은 그 열거를 없앤다.
     */
    @Test
    fun `문서가 본문을 적지 않은 operation 은 받아들여지는 상태에서 본문 없이 불러 문서의 2xx 를 낸다`() {
        val bodyless =
            documentedOperations()
                .filterValues { !it }
                .keys
                .filterNot { it.substringBefore(' ') in READ_METHODS }
        bodyless.toSet() shouldBe setOf("DELETE /api/session", "POST /api/bid-cases/{caseId}/resume")

        val statuses =
            bodyless.associateWith { operation ->
                val (method, path) = operation.split(' ', limit = 2)
                access.reset()
                val session = SessionTestClient(restTemplate, ::url).signIn()
                // 경로 변수는 **이 세션이 담고 그 operation 이 받아들여지는 상태로 옮긴 건**의 id 로 채운다(지금 경로 변수를 가진
                // 본문 없는 op 는 resume 하나이고, 그 출발 상태는 「나중에」다 — 전이표). 모집단이 바뀌면 위 등식이 먼저 붉다.
                val target = if (PATH_VARIABLE in path) path.replace(PATH_VARIABLE, deferredCaseId(session)) else path
                session
                    .send(HttpMethod.valueOf(method), target, null)
                    .statusCode
                    .value()
            }

        statuses shouldBe bodyless.associateWith(::documentedSuccess)
    }

    /** 담고 「나중에」로 옮긴 건 — `resume` 이 받아들여지는 상태다. */
    private fun deferredCaseId(session: SessionTestClient): String {
        val opened = session.post(AccountScopedRoutes.BID_CASES, OPEN_BODY)
        check(opened.statusCode.is2xxSuccessful) { "담기가 실패했다: ${opened.statusCode}" }
        val id = requireNotNull(CASE_ID.find(opened.body.orEmpty())) { "응답에 caseId 가 없다" }.groupValues[1]
        val deferred = session.post("${AccountScopedRoutes.BID_CASES}/$id/defer", DEFER_BODY)
        check(deferred.statusCode.is2xxSuccessful) { "나중에가 실패했다: ${deferred.statusCode}" }
        return id
    }

    /** 문서가 그 operation 에 적은 성공 응답 — 2xx 가 정확히 하나여야 한다(둘이면 어느 쪽을 기대할지 문서가 정하지 않았다). */
    private fun documentedSuccess(operation: String): Int {
        val (method, path) = operation.split(' ', limit = 2)
        val pathItem = (spec["paths"] as Map<String, Any?>)[path] as Map<String, Any?>
        val responses = (pathItem[method.lowercase()] as Map<String, Any?>)["responses"] as Map<String, Any?>
        return responses.keys
            .filter { it.startsWith("2") }
            .single()
            .toInt()
    }

    /**
     * 검토 r2 C2-N4 — **경로 변수를 읽는 매핑은 전부 문서에 400 을 적는다**(형식이 틀린 식별자는 400 이다). `resumeBidCase`
     * 가 그 400 을 빠뜨렸다. 문서 쪽에서 400 을 적은 operation 은 본문 형식 오류 때문에도 400 을 적으므로 두 집합은 같지
     * 않다 — 그래서 「경로 변수 매핑 중 400 을 적지 않은 것」의 집합이 **비어 있음**을 등식으로 본다.
     */
    @Test
    fun `경로 변수를 읽는 매핑은 전부 문서에 400 을 적는다`() {
        val pathVariableMappings =
            handlerMapping.handlerMethods.entries
                .filter { (_, handler) ->
                    handler.methodParameters.any { it.hasParameterAnnotation(PathVariable::class.java) }
                }.flatMap { (info, _) ->
                    val patterns =
                        info.pathPatternsCondition
                            ?.patterns
                            .orEmpty()
                            .map { it.patternString }
                    info.methodsCondition.methods.flatMap { method -> patterns.map { "${method.name} $it" } }
                }.toSet()
        pathVariableMappings.shouldNotBeEmpty()

        pathVariableMappings.filterNot { it in operationsDeclaring400() }.toSet() shouldBe emptySet()
    }

    private fun operationsDeclaring400(): Set<String> {
        val paths = spec["paths"] as Map<String, Any?>
        return paths.entries
            .flatMap { (path, item) ->
                val pathItem = item as Map<String, Any?>
                pathItem.keys
                    .filter { it in OPENAPI_METHODS }
                    .filter { method ->
                        val responses = (pathItem[method] as Map<String, Any?>)["responses"] as Map<String, Any?>
                        responses.containsKey("400")
                    }.map { "${it.uppercase()} $path" }
            }.toSet()
    }

    /** 문서 쪽 — `METHOD /path` → `requestBody` 가 있는가. */
    private fun documentedOperations(): Map<String, Boolean> {
        val paths = spec["paths"] as Map<String, Any?>
        return paths.entries
            .flatMap { (path, item) ->
                val pathItem = item as Map<String, Any?>
                pathItem.keys
                    .filter { it in OPENAPI_METHODS }
                    .map { method ->
                        val operation = pathItem[method] as Map<String, Any?>
                        "${method.uppercase()} $path" to operation.containsKey("requestBody")
                    }
            }.toMap()
    }

    /** 매핑 쪽 — `METHOD /path` → 핸들러가 `@RequestBody` 매개변수를 갖는가. */
    private fun mappedOperations(): Map<String, Boolean> =
        handlerMapping.handlerMethods.entries
            .flatMap { (info, handler) ->
                val readsBody = handler.methodParameters.any { it.hasParameterAnnotation(RequestBody::class.java) }
                val patterns =
                    info.pathPatternsCondition
                        ?.patterns
                        .orEmpty()
                        .map { it.patternString }
                info.methodsCondition.methods.flatMap { method ->
                    patterns.map { pattern -> "${method.name} $pattern" to readsBody }
                }
            }.toMap()

    private companion object {
        /** OpenAPI 3 path item 의 메서드 고정 필드 — `parameters`·`summary` 같은 형제와 가른다. */
        val OPENAPI_METHODS =
            setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

        /** 본문을 싣지 않는 것이 정상인 읽기 메서드 — 행동 등식의 모집단에서 뺀다. */
        val READ_METHODS = setOf("GET", "HEAD", "OPTIONS")
        val PATH_VARIABLE = Regex("""\{[^}]+}""")
        val CASE_ID = Regex(""""caseId":(\d+)""")
        const val OPEN_BODY = """{"noticeNumber":"R7B-BODYLESS","noticeRound":"000","bidKind":"REAL"}"""
        const val DEFER_BODY = """{"remindAt":"2099-01-01T00:00:00Z"}"""
    }
}
