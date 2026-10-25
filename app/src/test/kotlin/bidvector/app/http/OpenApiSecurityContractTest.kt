package bidvector.app.http

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 계약 문서의 **보안 선언 축**(M7/7B 에 `OpenApiContractTest` 에서 갈라 나왔다).
 *
 * 가른 경계는 크기가 아니라 **책임**이다 — 저쪽은 응답의 **모양**(평탄 · 키 집합 · ErrorBody)을
 * 재고 이쪽은 **누가 부를 수 있는가**의 선언이 구현과 같은지를 잰다. 두 축이 한 파일에 있으면
 * 「인증 선언이 바뀌었다」와 「응답 모양이 바뀌었다」가 같은 실패로 보인다.
 *
 * **M7/7B 가 더한 축**: 입찰함은 계정 전용이라 자격 헤더로 닿지 않는다. 그래서 비계정
 * operation 은 전처럼 「자격 헤더 대안을 남긴다」를, 계정 전용 operation 은 「세션만 적는다」를
 * **양방향 정확 일치**로 잰다. 어느 쪽이 참인지는 선언이 아니라 실제 판정이 정하고
 * (`ProductionHttpSurfaceTest` 의 축 probe), 이 파일은 문서가 그 판정과 같은지를 본다.
 *
 * `@Suppress("UNCHECKED_CAST")` 는 YAML 로더가 내는 `Any?` 를 문서 구조대로 좁히는 자리다 —
 * 원본 `OpenApiContractTest` 와 **같은 이유·같은 범위**이고, 문서가 그 모양이 아니면 아래
 * 단언들이 먼저 붉어진다.
 */
@Suppress("UNCHECKED_CAST")
class OpenApiSecurityContractTest : HttpIntegrationTestBase() {
    private val spec: Map<String, Any?> by lazy { loadOpenApiSpec() }

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
                .filterKeys { !accountScoped(it) }
                .filterNot { (_, requirement) -> alternativesOf(requirement).contains(setOf(CREDENTIAL_SCHEME)) }
                .keys

        missing shouldBe emptySet()
    }

    /**
     * **계정 전용 operation 은 자격 헤더 대안을 적지 않는다**(M7/7B, 팀장 결정 2026-10-22 (b) 의 문서 쪽).
     *
     * 위 test 가 막는 것은 「문서가 구현보다 **좁아지는**」 것이다 — 필터가 자격 헤더를 받는데
     * 문서가 안 적으면 기계 호출자가 막힌 줄 안다. 입찰함은 그 전제가 **반대**다: 자격 헤더
     * 경로에는 계정이 없어 구현이 실제로 401 을 낸다(소유 경계, R-2). 그 operation 에
     * `operatorCredential` 을 적으면 문서가 구현보다 **넓어지고**, 호출자는 되는 줄 알고
     * 401 을 받는다 — 같은 결함의 반대쪽이다.
     *
     * 그래서 계정 전용 쪽은 **세션만 적혔는지를 양방향으로** 잰다. 어느 쪽이 맞는지는
     * 선언이 아니라 실제 판정이 정하고(`ProductionHttpSurfaceTest` 의 축 probe), 이 test 는
     * 문서가 그 판정과 같은지를 본다.
     */
    @Test
    fun `계정 전용 operation 은 세션만 적는다 — 문서가 구현보다 넓어지지 않는다`() {
        val accountOperations = authenticatedOperations().filterKeys { accountScoped(it) }
        accountOperations.keys.shouldNotBeEmpty()

        val wrong =
            accountOperations
                .filterNot { (path, requirement) ->
                    val alternatives = alternativesOf(requirement)
                    val expected =
                        if (stateChanging(path)) {
                            listOf(setOf(SESSION_SCHEME, CSRF_SCHEME))
                        } else {
                            listOf(setOf(SESSION_SCHEME))
                        }
                    alternatives == expected
                }.keys

        wrong shouldBe emptySet()
    }

    /** 문서의 operation 좌표(`METHOD /path`)가 계정 전용 뿌리에 속하는가 — 선언 한 자리를 읽는다. */
    private fun accountScoped(operation: String): Boolean = AccountScopedRoutes.covers(operation.substringAfter(" "))

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
