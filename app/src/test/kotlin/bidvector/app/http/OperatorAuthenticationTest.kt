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
 * 우회 (4) — [OperatorCredentialAuthenticator]의 비교는 `MessageDigest.isEqual`만 쓴다
 * (`ConstantTimeComparisonStructureTest`가 구조로 잠근다, 이 test는 기능만 본다).
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
class OperatorAuthenticationTest : HttpIntegrationTestBase() {
    @Autowired
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    @Test
    fun `등록된 모든 endpoint가 자격증명 없이는 401이다 — 기계 전수`() {
        val paths =
            handlerMapping.handlerMethods.keys
                .mapNotNull { it.pathPatternsCondition }
                .flatMap { it.patterns }
                // 경로 변수를 가진 매핑이 생겼다(`/{sessionId}`). 패턴 문자열을 그대로
                // URL 로 쓰면 RestTemplate 이 그것을 **템플릿**으로 보고 확장에 실패한다 — 고정
                // 값으로 치환해 구체 경로를 만든다(어느 세션도 가리키지 않는다: 인증 경계만 잰다).
                .map { it.patternString.replace(PATH_VARIABLE_PATTERN, "auth-probe") }
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

    /**
     * **M15/15A — 탐침 경로가 바뀌었다.** 전략은 이제 계정 전용 뿌리라 자격 헤더 경로(계정을
     * 내지 않는다)로는 닿지 못한다. 이 test 가 재는 것은 「자격 헤더가 필터를 통과시키는가」라서
     * 계정을 요구하지 않는 경로 하나로 옮긴다 — 공고 검색이다.
     */
    @Test
    fun `올바른 자격증명이면 통과한다`() {
        val headers = HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }
        val response =
            restTemplate.exchange(url("/api/notices"), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        response.statusCode.value() shouldBe 200
    }

    /**
     * **M15/15A — 계정 전용 뿌리는 자격 헤더로 닿지 않는다.** 사유를 나누지 않으므로 401 이고,
     * 그 값이 「자격증명이 틀렸다」와 같다(D-6A1-6). 위 test 의 음성 대조다 — 같은 자격증명이
     * 한 경로에서는 200 이고 다른 경로에서는 401 이어야 이 slice 의 경계가 실재한다.
     *
     * **전수는 여기가 아니다** — 「자격 헤더로 닿는 매핑 ∩ 계정 전용 = ∅」은 표면 전수 test 가
     * 실제 매핑을 훑어 잰다. 여기서는 GET 으로 닿는 뿌리 둘로 그 사실의 **거동**만 든다
     * (POST 전용 뿌리는 자격이 통과한 뒤 405 라 이 축을 가리지 못한다).
     */
    @Test
    fun `올바른 자격증명이어도 계정 전용 뿌리는 401 이다`() {
        val headers = HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL) }

        listOf(AccountScopedRoutes.STRATEGY, AccountScopedRoutes.SUGGESTIONS).forEach { root ->
            val response =
                restTemplate.exchange(url(root), HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
            response.statusCode.value() shouldBe 401
            response.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
        }
    }

    /**
     * **자격 헤더로 닿는 경로에서 잰다**(r1 리뷰 MEDIUM). M15/15A 가 전략을 계정 전용으로 옮기면서
     * `/api/strategy` 는 **올바른 자격에도** 401 이다 — 그 경로에서 이 둘을 재면 두 팔이 자격과
     * 무관한 사유로 401 이 되어, 인증기가 아무 값이나 받아들이도록 바뀌어도 초록이다(공허).
     */
    @Test
    fun `자격증명이 없든 틀리든 사유를 나누지 않는다`() {
        val wrongHeaders = HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, "wrong-value") }

        val missing = restTemplate.getForEntity(url(CREDENTIAL_REACHABLE_PATH), Map::class.java)
        val wrong =
            restTemplate.exchange(
                url(CREDENTIAL_REACHABLE_PATH),
                HttpMethod.GET,
                HttpEntity<Void>(wrongHeaders),
                Map::class.java,
            )

        missing.statusCode.value() shouldBe 401
        wrong.statusCode.value() shouldBe 401
        missing.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
        wrong.body?.get("code") shouldBe ErrorCode.UNAUTHENTICATED
        missing.body?.get("message") shouldBe wrong.body?.get("message")
    }

    /** 같은 사유로 자격 헤더가 **닿는** 경로에서 잰다 — 위 test 의 주석 참고. */
    @Test
    fun `401 응답에 자격증명 값이 실리지 않는다`() {
        val headers = HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, "wrong-value") }
        val response =
            restTemplate.exchange(
                url(CREDENTIAL_REACHABLE_PATH),
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )
        (response.body ?: "").contains("wrong-value") shouldBe false
    }
}

/**
 * 자격 헤더 축이 **아직 200 으로 닿는** 경로(M15/15A) — 계정 전용 뿌리가 아닌 자리다. 이 축의
 * 거동을 재는 test 는 전부 여기를 쓴다(계정 전용 뿌리에서 재면 자격과 무관하게 401 이라 공허하다).
 */
private const val CREDENTIAL_REACHABLE_PATH = "/api/notices"
