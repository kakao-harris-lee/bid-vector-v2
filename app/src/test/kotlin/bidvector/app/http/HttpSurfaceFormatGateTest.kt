package bidvector.app.http

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/** 경로 변수 자리에 넣는 고정 값 — 어느 세션도 가리키지 않는다(형식 판정만 본다). */
private const val PATH_VARIABLE_PROBE = "format-gate-probe"

private val PROBED_METHODS =
    listOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)

/**
 * `OPEN-API-WRONG-METHOD-500` 폐쇄 판정(M6/6A-2b D-6A2b-7) — **경로를 손으로 적지
 * 않는다.** 등록된 핸들러 매핑 전부를 [RequestMappingHandlerMapping] 에서 기계로 거두고,
 * 각 매핑이 **선언하지 않은** 메서드를 전수로 두드려 500 이 하나도 없음을 단언한다.
 * 새 컨트롤러가 생기면 이 test 는 수정 없이 그 경로를 포함한다.
 *
 * 「수집 집합 == 매핑 집합」을 함께 단언한다(집합 등식) — 경로 하나가 조용히 모집단에서
 * 빠지면(예: 패턴 형태가 달라 치환에 실패) 그 부재 자체가 RED 다. 부분집합 단언이면
 * 모집단이 줄어드는 회귀가 보이지 않는다.
 *
 * 인증을 통과한 뒤를 잰다 — 401 로 먼저 끊기면 이 표면은 아무것도 재지 못한다.
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
class HttpSurfaceFormatGateTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    /** 매핑 → (구체 경로, 선언된 메서드 집합). 메서드 조건이 비면 「전 메서드 허용」이다. */
    private fun mappings(): Map<String, Set<String>> {
        val collected = mutableMapOf<String, MutableSet<String>>()
        handlerMapping.handlerMethods.keys.forEach { info ->
            val methods = info.methodsCondition.methods.map(RequestMethod::name)
            info.pathPatternsCondition?.patterns.orEmpty().forEach { pattern ->
                val path = pattern.patternString.replace(Regex("\\{[^/}]+}"), PATH_VARIABLE_PROBE)
                collected.getOrPut(path) { mutableSetOf() } += methods
            }
        }
        return collected
    }

    private fun headers(contentType: MediaType?): HttpHeaders =
        HttpHeaders().apply {
            set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL)
            contentType?.let { this.contentType = it }
        }

    private fun exchange(
        path: String,
        method: HttpMethod,
        body: String?,
        contentType: MediaType?,
    ): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            url(path),
            method,
            HttpEntity(body, headers(contentType)),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    @Test
    fun `등록된 매핑이 선언하지 않은 메서드는 500 이 아니라 405 다 — 기계 전수`() {
        val mappings = mappings()
        mappings.keys.shouldNotBeEmpty()
        // 모집단이 우리 표면을 실제로 담고 있는지 — 공허한 전수를 막는 앵커.
        mappings.keys shouldContainAll setOf("/api/strategy", "/api/strategy/edit-sessions")

        val probed = mutableSetOf<String>()
        val statuses = mutableMapOf<String, Int>()
        mappings.forEach { (path, declared) ->
            probed += path
            if (declared.isEmpty()) return@forEach
            PROBED_METHODS
                .filterNot { it.name() in declared }
                .forEach { method ->
                    val response = exchange(path, method, null, null)
                    statuses["$method $path"] = response.statusCode.value()
                }
        }

        probed shouldBe mappings.keys
        statuses.filterValues { it == 500 } shouldBe emptyMap()
        statuses.filterValues { it != 405 } shouldBe emptyMap()
    }

    @Test
    fun `405 응답 본문은 ErrorBody 이고 Allow 헤더가 함께 온다`() {
        val response =
            restTemplate.exchange(
                url("/api/strategy"),
                HttpMethod.DELETE,
                HttpEntity<Void>(headers(null)),
                Map::class.java,
            ) as ResponseEntity<Map<String, Any?>>

        response.statusCode.value() shouldBe 405
        response.body?.keys shouldBe setOf("code", "message", "correlationId")
        response.body?.get("code") shouldBe ErrorCode.METHOD_NOT_ALLOWED
        response.headers.getFirst("Allow") shouldBe "GET"
    }

    @Test
    fun `본문을 받는 매핑은 미지원 미디어 타입과 깨진 JSON 에 500 을 내지 않는다 — 기계 전수`() {
        val postPaths = mappings().filterValues { "POST" in it }.keys
        postPaths.shouldNotBeEmpty()

        val outcomes = mutableMapOf<String, Pair<Int, Any?>>()
        postPaths.forEach { path ->
            val plainText = exchange(path, HttpMethod.POST, "not json", MediaType.TEXT_PLAIN)
            val brokenJson = exchange(path, HttpMethod.POST, "{", MediaType.APPLICATION_JSON)
            val emptyObject = exchange(path, HttpMethod.POST, "{}", MediaType.APPLICATION_JSON)
            outcomes["415 $path"] = plainText.statusCode.value() to plainText.body?.get("code")
            outcomes["400 $path"] = brokenJson.statusCode.value() to brokenJson.body?.get("code")
            outcomes["누락 $path"] = emptyObject.statusCode.value() to emptyObject.body?.get("code")
        }

        outcomes.filterKeys { it.startsWith("415 ") }.values.toSet() shouldBe
            setOf(415 to ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        outcomes.filterKeys { it.startsWith("400 ") }.values.toSet() shouldBe
            setOf(400 to ErrorCode.INVALID_REQUEST)
        outcomes.filterKeys { it.startsWith("누락 ") }.values.toSet() shouldBe
            setOf(400 to ErrorCode.INVALID_REQUEST)
    }
}
