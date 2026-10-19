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
 *
 * **이름에 `__Host-` 접두를 쓴다**(code-review r2 M-3). 속성 넷만으로는 **쿠키 그림자**가 남는다:
 * 형제 서브도메인(또는 평문 http 로 노출된 다른 호스트)을 쥔 공격자가 부모 도메인에 같은 이름의
 * 쿠키를 심으면 브라우저는 둘을 함께 보내고, 어느 쪽이 이기는지는 전송 순서에 달렸다. 공격자
 * 쿠키가 이기면 피해자는 **공격자의 세션으로 조용히 조작**하게 된다(`HttpOnly` 라 화면이 그
 * 사실을 볼 수 없다). 접두는 브라우저가 `Secure` + `Path=/` + `Domain` 없음을 **강제**하게 하고
 * 하위·형제 도메인이 그 이름을 덮어쓰지 못하게 한다 — 지금 속성 집합이 이미 그 셋을 만족하므로
 * 거동 변화는 없다.
 *
 * **접두는 `Secure` 와 한 몸이라 이름이 그 설정을 따라간다.** 로컬 http 개발(`cookie-secure=false`)
 * 에서 접두 이름을 쓰면 브라우저가 그 쿠키를 **버린다** — 그래서 그때만 평문 이름이다. 운영
 * 반입(7F 의 TLS)은 기본값(참) 위에 서므로 접두 이름이 선다.
 */
class SessionCookieWriter(
    private val secure: Boolean,
    private val absoluteWindow: Duration,
) {
    /**
     * 이 조립이 쓰는 쿠키 이름 **하나** — 쓰기와 읽기가 같은 값을 본다. `read` 가 companion 이
     * 아니라 인스턴스 메서드인 이유다(이름이 설정에 달렸으므로 정적으로 알 수 없다).
     */
    val name: String = if (secure) HOST_PREFIXED_NAME else PLAIN_NAME

    /** 로그인 응답의 `Set-Cookie` 값. */
    fun issued(key: BrowserSessionKey): String = build(key.cookieValue(), absoluteWindow).toString()

    /**
     * 로그아웃 응답의 `Set-Cookie` 값 — 즉시 만료다. **이것이 로그아웃의 전부가 아니다**:
     * 서버 쪽 폐기가 정본이고(우회 ③) 이 쿠키 삭제는 브라우저 정리일 뿐이다.
     */
    fun cleared(): String = build("", Duration.ZERO).toString()

    /**
     * 요청에서 세션 값을 읽는 **유일한 자리** — 읽는 즉시 [BrowserSessionKey] 로 감싸므로
     * 호출부에 raw 문자열이 남지 않는다.
     *
     * **같은 이름이 둘 이상이면 거부한다**(code-review r2 M-3, fail-closed). 앞 판은 이름 → 쿠키
     * 맵으로 접어 **마지막 값이 이겼고**, 그 순서는 브라우저가 정한다 — 그림자 공격이 「조용한
     * 탈취」가 될 수 있는 자리였다. 거부로 떨어뜨리면 최악이 「로그인 화면으로 돌아감」이다.
     * `__Host-` 접두가 1차 방어이고 이것이 2차다(접두를 쓸 수 없는 로컬 http 에서도 선다).
     */
    fun read(request: HttpServletRequest): BrowserSessionKey? {
        // 이름 비교를 `==` 로 쓰지 않는다 — 그 자리는 `Intrinsics.areEqual` 로 컴파일되고
        // `ConstantTimeComparisonStructureTest` 가 이 패키지에서 그것을 허용 목록으로 가둔다.
        // 비교 대상이 비밀이 아니라 **쿠키 이름**이지만, 목록을 넓히는 대신 이름 → 쿠키 **묶음**
        // 조회로 같은 일을 한다(그 조회가 중복 판정에 필요한 묶음을 그대로 준다).
        val candidates =
            request.cookies
                ?.groupBy(Cookie::getName)
                ?.get(name)
                .orEmpty()
        if (candidates.size != 1) return null
        return candidates
            .single()
            .value
            ?.takeIf { it.isNotBlank() }
            ?.let { BrowserSessionKey(it) }
    }

    private fun build(
        value: String,
        maxAge: Duration,
    ): ResponseCookie =
        ResponseCookie
            .from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            // `Domain` 을 **적지 않는다** — 적으면 `__Host-` 접두가 깨지고(브라우저가 버린다)
            // 하위 도메인으로 쿠키가 번진다. 접두가 그 사실을 이름으로 못박는다.
            .path("/")
            .maxAge(maxAge)
            .build()

    companion object {
        /** 운영(TLS)에서 쓰는 이름 — 접두가 브라우저에 `Secure`·`Path=/`·`Domain` 없음을 강제한다. */
        const val HOST_PREFIXED_NAME = "__Host-bidvector_session"

        /** 로컬 http 개발 전용 — 접두는 `Secure` 를 요구하고 그 실행에는 `Secure` 가 없다. */
        const val PLAIN_NAME = "bidvector_session"
    }
}
