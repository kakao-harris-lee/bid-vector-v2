package bidvector.app.http

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * 우회 (1) 폐쇄 — 「등록된 모든 endpoint가 필터를 지난다」를 **기계 전수**로 잰다(손으로 쓴
 * 경로 목록이 아니라 [RequestMappingHandlerMapping]에서 직접 얻는다). 새 controller
 * 메서드가 생겨도 이 test는 수정 없이 그 경로를 포함한다 — 변이 검증: `HttpTestSupport`의
 * `HttpTestApplication`에 임시 `@RestController`(새 경로)를 추가해 재실행하면, 그 새
 * 경로도 무자격 401 대조에 자동으로 들어간다(수동 재현, evidence checklist에 기록).
 *
 * 우회 (4) — [OperatorCredentialFilter]의 비교는 `MessageDigest.isEqual`만 쓴다
 * (`ConstantTimeComparisonStructureTest`가 구조로 잠근다, 이 test는 기능만 본다).
 */
@SpringBootTest(
    classes = [HttpTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND, PROP_NO_STATIC_RESOURCE_MAPPINGS],
)
class OperatorAuthenticationTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    @Test
    fun `등록된 모든 endpoint가 자격증명 없이는 401이다 — 기계 전수`() {
        val paths =
            handlerMapping.handlerMethods.keys
                .mapNotNull { it.pathPatternsCondition }
                .flatMap { it.patterns }
                .map { it.patternString }
                .toSet()

        // 술어가 공허하게 참인 회귀를 막는다 — 최소 우리 endpoint 하나는 있어야 한다.
        paths.shouldNotBeEmpty()
        // code-reviewer MEDIUM 시정 — shouldNotBeEmpty()만으로는 D-6A1-21 ①(Boot 자동 구성
        // /error가 이 기계 전수에 들어오는지)을 확인하지 못한다. Boot 버전이 바뀌어 /error가
        // handlerMethods 밖으로 나가면(예: 별도 dispatcher로) 이 test가 조용히 그 사실을
        // 놓치지 않도록 직접 단언한다.
        paths shouldContain "/error"

        paths.forEach { path ->
            val response = restTemplate.getForEntity(url(path), String::class.java)
            response.statusCode.value() shouldBe 401
        }
    }

    @Test
    fun `올바른 자격증명이면 통과한다`() {
        val headers = HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL) }
        val response =
            restTemplate.exchange(url("/api/strategy"), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        response.statusCode.value() shouldBe 200
    }

    @Test
    fun `자격증명이 없든 틀리든 사유를 나누지 않는다`() {
        val wrongHeaders = HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, "wrong-value") }

        val missing = restTemplate.getForEntity(url("/api/strategy"), Map::class.java)
        val wrong =
            restTemplate.exchange(url("/api/strategy"), HttpMethod.GET, HttpEntity<Void>(wrongHeaders), Map::class.java)

        missing.statusCode.value() shouldBe 401
        wrong.statusCode.value() shouldBe 401
        missing.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
        wrong.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
        missing.body?.get("message") shouldBe wrong.body?.get("message")
    }

    @Test
    fun `401 응답에 자격증명 값이 실리지 않는다`() {
        val headers = HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, "wrong-value") }
        val response =
            restTemplate.exchange(url("/api/strategy"), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        (response.body ?: "").contains("wrong-value") shouldBe false
    }
}
