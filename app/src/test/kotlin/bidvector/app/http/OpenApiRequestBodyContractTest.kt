package bidvector.app.http

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
    }
}
