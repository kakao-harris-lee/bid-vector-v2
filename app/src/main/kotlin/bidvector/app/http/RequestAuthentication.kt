package bidvector.app.http

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * 인증 경로 하나의 판정 — 통과면 audit 주체 라벨을 함께 낸다. 거부의 **사유를 나누지
 * 않는다**(D-6A1-6): 자격이 없는 것과 틀린 것이 같은 값이다.
 */
sealed interface AuthenticationResult {
    class Authenticated(
        val subject: String,
    ) : AuthenticationResult

    data object Denied : AuthenticationResult
}

/**
 * 인증 경로 하나 — 요청에서 자기 자격을 찾아 판정한다. **경로를 모른다**(어느 endpoint 인지
 * 보지 않는다): 무인증으로 열리는 자리를 정하는 것은 아래 [RequestAuthenticationFilter] 의
 * 허용 목록 하나다.
 */
interface RequestAuthenticator {
    fun authenticate(request: HttpServletRequest): AuthenticationResult
}

/**
 * **모든 경로 기본 거부 필터 하나**(M7/7D 산출물 4) — 6A-1 의 `OperatorCredentialFilter` 가
 * 지던 자리를 이어받고, 인증 **경로**를 열거가 아니라 **구성**으로 받는다(설계 검토 (1)).
 *
 * **우회 ⑦ — 이 필터는 경로 이름을 모른다.** 등록(`BidVectorApplication`)이 urlPatterns 를
 * 와일드카드 하나로 명시하므로 어떤 새 endpoint 도 이 필터 밖에서 태어나지 않는다. 그리고
 * 두 인증 경로가 **한 등록**에서 판정되므로, 「한쪽 경로만 지나가는 endpoint」라는 상태가
 * 구성상 존재하지 않는다 — 경로를 더해도 이 필터를 지나고, 이 필터는 [OPEN_ROUTES] 밖
 * 전부를 거부한다.
 *
 * **허용 목록은 하나뿐이고 집합 등식 test 가 그것을 잠근다**(`BrowserSessionAuthenticationTest`).
 * 비교는 `(메서드, 경로)` **정확 일치**다 — 변형 표기(`/api/session/`·대소문자·경로 매개변수
 * `;a=b`·`/api/./session`)는 이 집합에 들지 못해 **거부 쪽으로** 떨어진다(fail-closed: 허용
 * 목록의 불일치는 401 이고, 그 반대 방향의 사고가 없다).
 *
 * 실패 응답은 6A-1 과 **같은 모양**이다 — 401 [ErrorBody] 하나이고, 세션·CSRF·자격 헤더 중
 * 무엇이 어긋났는지 말하지 않는다.
 */
class RequestAuthenticationFilter(
    private val authenticators: List<RequestAuthenticator>,
) : Filter {
    init {
        require(authenticators.isNotEmpty()) {
            "인증 경로가 하나도 없는 등록은 모든 요청을 거부한다 — 조립 오류다"
        }
    }

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        if (OPEN_ROUTES.contains(routeOf(httpRequest))) {
            // 로그인 자체는 아직 주체가 없다 — audit 행은 남고 라벨은 실패와 같은 값이다
            // (주체가 아니라 「신원 없음」을 뜻한다. 그 요청의 결과는 상태 코드가 가른다).
            httpRequest.setAttribute(AUDIT_SUBJECT_ATTRIBUTE, UNAUTHENTICATED_SUBJECT)
            chain.doFilter(request, response)
            return
        }

        // `filterIsInstance` 를 쓰지 않는다 — reified inline 이라 stdlib(`_Sequences.kt`)의 람다
        // 클래스가 이 모듈 아카이브에 복사되고 `jarContentGate` 가 「게이트를 통과한 소스가
        // 아니다」로 끊는다(실측 — `ManagementSurface` 가 같은 자리에서 같은 이유로 `as?` 를 쓴다).
        // `firstNotNullOfOrNull` 의 람다는 이 파일의 것이고, **첫 통과에서 멈춘다**(자격 헤더가
        // 통과하면 세션 조회 트랜잭션이 아예 열리지 않는다).
        val granted =
            authenticators.firstNotNullOfOrNull {
                it.authenticate(httpRequest) as? AuthenticationResult.Authenticated
            }

        if (granted == null) {
            httpRequest.setAttribute(AUDIT_SUBJECT_ATTRIBUTE, UNAUTHENTICATED_SUBJECT)
            writeUnauthenticated(httpRequest, httpResponse)
            return
        }

        httpRequest.setAttribute(AUDIT_SUBJECT_ATTRIBUTE, granted.subject)
        chain.doFilter(request, response)
    }

    /**
     * DispatcherServlet 밖(Spring MVC 진입 전)이라 [GlobalErrorHandler] 를 지나지 않는다 —
     * 이 필터 자신이 [ErrorBody] 형태로 직접 응답한다(6A-1 과 같은 자리, 같은 함수).
     */
    private fun writeUnauthenticated(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val correlationId = request.getAttribute(CORRELATION_ID_ATTRIBUTE) as? String ?: "unknown"
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(ErrorMapping.unauthenticated(correlationId).toJson())
    }

    /**
     * **조립 실측용 창**(M7/7D) — 꽂힌 인증 경로 그대로다. 구성으로 받는 자리는 「다른 몸통을
     * 꽂을 수 있다」는 뜻이므로(`app.injection.carrier-exemptions` 의 둘째 쌍), 출하 조립에
     * **실제로** 무엇이 꽂혔는지를 test 가 등식으로 재야 한다. `internal` 이라 모듈 밖에서는
     * 보이지 않고, production 의 어느 코드도 이 값을 읽지 않는다.
     *
     * **타입 이름을 여기서 만들지 않는다** — `javaClass.name` 은 반사 접근이고 production 전
     * 층의 반사는 등재 쌍만 허용된다(`collection.reflection.*` 게이트). 이름이 필요한 쪽은
     * test 이므로 그 계산을 test 에 둔다.
     */
    internal val configuredAuthenticators: List<RequestAuthenticator> get() = authenticators

    companion object {
        /** audit 주체 라벨 — 신원이 없는 요청(실패·로그인 시도) 전부가 이 값이다. */
        const val UNAUTHENTICATED_SUBJECT = "unauthenticated"

        /**
         * **무인증으로 열리는 자리 전부**(계약 산출물 4 「로그인 endpoint 만」). 이 집합이
         * 하나라는 것과, 그 밖 모든 등록 매핑이 401 이라는 것을 test 가 각각 잠근다.
         */
        val OPEN_ROUTES: Set<String> = setOf("POST /api/session")

        fun routeOf(request: HttpServletRequest): String = "${request.method} ${request.requestURI}"
    }
}
