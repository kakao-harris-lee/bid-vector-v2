package bidvector.app.http

import bidvector.workflow.access.BrowserSessionKey
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import java.time.Duration

/**
 * 세션 쿠키의 **유일한 생성·판독 자리**(M7/7D 산출물 3). 헤더 문자열을 손으로 조립하지 않고
 * Spring 의 [ResponseCookie] 빌더를 쓴다 — 속성 표기(`SameSite` 포함)를 우리가 다시 정의하지
 * 않는다.
 *
 * **속성 넷의 근거.**
 *  · `HttpOnly` — 화면 스크립트가 세션 값을 읽을 수 없다. CSRF 값은 따로 응답 본문으로 준다
 *    (그 값이 새어도 세션이 되지 않는다 — 단방향 유도).
 *  · `Secure` — 기본이 **참**이고, 로컬 http 개발 실행에서만 설정으로 내린다
 *    (`bidvector.access.cookie-secure=false`). 운영 TLS 는 7F 가 든다.
 *  · `SameSite=Strict` — 이 앱에는 다른 사이트에서 돌아오는 진입(OAuth 전환·외부 링크 복귀)이
 *    **없다**. 그러니 `Lax` 가 사는 유일한 이유(교차 사이트 최상위 탐색에서 쿠키를 싣는 것)가
 *    여기서는 이득이 없고, `Strict` 는 CSRF 요구의 앞단에서 한 겹 더 막는다.
 *  · `Path=/` · `Max-Age` — 절대 창과 같은 길이다. 브라우저가 먼저 버려도 서버 쪽 만료가
 *    정본이다(쿠키 만료에 인증을 의존하지 않는다).
 */
class SessionCookieWriter(
    private val secure: Boolean,
    private val absoluteWindow: Duration,
) {
    /** 로그인 응답의 `Set-Cookie` 값. */
    fun issued(key: BrowserSessionKey): String = build(key.cookieValue(), absoluteWindow).toString()

    /**
     * 로그아웃 응답의 `Set-Cookie` 값 — 즉시 만료다. **이것이 로그아웃의 전부가 아니다**:
     * 서버 쪽 폐기가 정본이고(우회 ③) 이 쿠키 삭제는 브라우저 정리일 뿐이다.
     */
    fun cleared(): String = build("", Duration.ZERO).toString()

    private fun build(
        value: String,
        maxAge: Duration,
    ): ResponseCookie =
        ResponseCookie
            .from(NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAge)
            .build()

    companion object {
        const val NAME = "bidvector_session"

        /**
         * 요청에서 세션 값을 읽는 **유일한 자리** — 읽는 즉시 [BrowserSessionKey] 로 감싸므로
         * 호출부에 raw 문자열이 남지 않는다. 쿠키 이름 비교를 손으로 하지 않고 이름 → 쿠키
         * 맵으로 찾는다(같은 이름의 쿠키가 여럿일 때도 한 값으로 고정된다).
         */
        fun read(request: HttpServletRequest): BrowserSessionKey? =
            request.cookies
                ?.associateBy(Cookie::getName)
                ?.get(NAME)
                ?.value
                ?.takeIf { it.isNotBlank() }
                ?.let { BrowserSessionKey(it) }
    }
}
