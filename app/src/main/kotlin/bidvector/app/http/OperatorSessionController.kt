package bidvector.app.http

import bidvector.app.wiring.BrowserAccessExecutor
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.SessionDescription
import bidvector.workflow.access.SignInOutcome
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * 세션 endpoint 셋(M7/7D 산출물 3) — 로그인·현재 세션·로그아웃.
 *
 * **컨트롤러는 조립된 실행기만 받는다**(D-6A2b-8) — 저장소·`ConnectionSource` 를 이 층에서 볼
 * 수 없다(의존 게이트가 구조로 강제한다). 요청 → 값 변환과 결과 → 상태 코드 변환만 한다.
 *
 * **인증은 이 클래스가 하지 않는다.** [RequestAuthenticationFilter] 가 모든 경로에서 판정하고,
 * 그 허용 목록에 있는 것은 로그인 하나다. 그래서 [signIn] 은 무인증 요청으로도 닿고 나머지
 * 둘은 닿지 않는다 — 그 사실을 이 클래스가 아니라 필터와 그 test 가 진다.
 *
 * **로그인 CSRF 대책**(설계 검토 (0)): 이 endpoint 는 `application/json` 본문만 받는다
 * (`consumes`). 다른 사이트의 HTML form 은 `application/x-www-form-urlencoded` ·
 * `multipart/form-data` · `text/plain` 밖의 Content-Type 을 **CORS preflight 없이 보낼 수
 * 없고**, 이 앱에는 CORS 설정이 없어 그 preflight 가 서지 않는다 → 415 로 떨어진다. 그리고
 * 발급 쿠키가 `SameSite=Strict` 라 강제 로그인이 성립해도 그 뒤의 교차 사이트 탐색에 쿠키가
 * 실리지 않는다.
 */
@RestController
@RequestMapping("/api/session")
class OperatorSessionController(
    private val access: BrowserAccessExecutor,
    private val cookies: SessionCookieWriter,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun signIn(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val payload = body.requireObject().requireKnownKeys(setOf("loginName", "passphrase"))
        val outcome =
            access.signIn(
                LoginName(payload.requiredNonBlankText("loginName")),
                ClearPassphrase.wrap(payload.requiredNonBlankText("passphrase")),
            )
        return when (outcome) {
            is SignInOutcome.Granted -> {
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .header(HttpHeaders.SET_COOKIE, cookies.issued(outcome.session.key))
                    .body(
                        SessionResponse(
                            csrf = outcome.session.stamp.headerValue(),
                            expiresAt = outcome.session.absoluteExpiresAt.toString(),
                        ),
                    )
            }

            is SignInOutcome.Refused -> unauthenticated(request)
        }
    }

    /**
     * 지금 내 세션 — 화면이 새로 뜰 때 CSRF 값을 다시 받는 자리다.
     *
     * 쿠키가 없거나 살아 있지 않으면 **404** 다(401 이 아니다): 이 요청은 이미 인증을 지났고
     * (쿠키 경로든 기존 운영자 자격 헤더 경로든), 없는 것은 자격이 아니라 **브라우저 세션이라는
     * 자원**이다. 어느 쪽인지 말하지 않는 것은 인증 실패의 규율이고 여기는 그 자리가 아니다.
     */
    @GetMapping
    fun current(request: HttpServletRequest): ResponseEntity<Any> {
        // 쿠키가 없는 것과 세션이 살아 있지 않은 것은 **같은 404** 다 — 한 식으로 접는다.
        val description = SessionCookieWriter.read(request)?.let { access.describe(it) }
        return description?.let { ResponseEntity.ok<Any>(it.toResponse()) } ?: sessionAbsent(request)
    }

    /**
     * 로그아웃 — **서버 쪽 폐기**가 본체이고 쿠키 삭제가 뒤따른다(우회 ③). 쿠키가 없는 요청
     * (기존 자격 헤더 경로)도 같은 204 다 — 멱등이고, 「세션이 있었는지」를 응답으로 알리지 않는다.
     */
    @DeleteMapping
    fun signOut(request: HttpServletRequest): ResponseEntity<Void> {
        SessionCookieWriter.read(request)?.let { access.signOut(it) }
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, cookies.cleared())
            .build()
    }

    private fun unauthenticated(request: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorMapping.unauthenticated(request.correlationIdOrUnknown()))

    private fun sessionAbsent(request: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorBody(ErrorCode.NOT_FOUND, "요청한 경로가 없다", request.correlationIdOrUnknown()))
}

/**
 * 세션 응답 — **평탄하다**(D-6A1-20 ⓑ: 중첩 object 속성 없음). `data class` 가 **아니다**:
 * 컴파일러가 합성하는 `equals` 는 [csrf] 값을 내용 비교하고, 그것이 곧 비밀 값의 「빠른 비교」가
 * 되어 `ConstantTimeComparisonStructureTest` 가 막는 형태가 된다([OperatorCredential] 과 같은
 * 근거). 이 타입은 한 번 직렬화되고 버려지므로 `equals`/`copy` 가 필요하지 않다.
 */
class SessionResponse(
    val csrf: String,
    val expiresAt: String,
)

private fun SessionDescription.toResponse(): SessionResponse =
    SessionResponse(csrf = stamp.headerValue(), expiresAt = absoluteExpiresAt.toString())
