package bidvector.app.http

import bidvector.app.wiring.BrowserAccessExecutor
import bidvector.workflow.access.AccessVerdict
import bidvector.workflow.access.AccountId
import bidvector.workflow.access.CsrfDemand
import bidvector.workflow.access.CsrfStamp
import jakarta.servlet.http.HttpServletRequest

/**
 * 세션 쿠키 **인증 경로**(M7/7D 산출물 4) — [RequestAuthenticationFilter] 한 등록이 구성으로
 * 받는 둘째 경로다. 판정 자체(폐기·만료·CSRF 일치)는 use case 가 하고, 이 클래스는 **HTTP
 * 어휘를 그 입력으로 옮기는 일만** 한다.
 *
 * **우회 ② — CSRF 요구는 기본이 「요구」다.** 면제 메서드를 [CSRF_EXEMPT_METHODS] 로 열거하고
 * 그 밖 **전부**(POST·PUT·PATCH·DELETE, 그리고 앞으로 생길 어떤 메서드든)가 요구 쪽으로
 * 떨어진다 — 상태를 바꾸는 메서드를 열거하면 목록 밖의 새 메서드가 조용히 면제된다.
 *
 * 쿠키 값은 읽는 즉시 `BrowserSessionKey` 로 감싼다 — 이 클래스에 raw 문자열이 머무는 필드가
 * 없고(`OperatorCredential` 과 같은 규율), 세션 비교는 저장된 해시와 use case 가 한다.
 *
 * 쿠키 **판독기를 받는다**(code-review r2 M-3) — 이름이 설정(`Secure`)에 달려 있어 정적으로 알
 * 수 없다. 쓰는 쪽과 읽는 쪽이 같은 객체를 보므로 두 이름이 갈릴 수 없다.
 */
class BrowserSessionAuthenticator(
    private val access: BrowserAccessExecutor,
    private val cookies: SessionCookieWriter,
) : RequestAuthenticator {
    override fun authenticate(request: HttpServletRequest): AuthenticationResult {
        val key = cookies.read(request) ?: return AuthenticationResult.Denied
        return when (val verdict = access.authenticate(key, demandOf(request))) {
            is AccessVerdict.Granted -> AuthenticationResult.Authenticated(subjectOf(verdict.account), verdict.account)
            is AccessVerdict.Denied -> AuthenticationResult.Denied
        }
    }

    private fun demandOf(request: HttpServletRequest): CsrfDemand =
        if (CSRF_EXEMPT_METHODS.contains(request.method)) {
            CsrfDemand.Exempt
        } else {
            CsrfDemand.Required(CsrfStamp.wrapOrNull(request.getHeader(CSRF_HEADER)))
        }

    companion object {
        /** 이름에 스캔 어휘를 쓰지 않는다(D-6A1-9 일반화) — `Csrf` 하나로 적는다. */
        const val CSRF_HEADER = "X-Operator-Csrf"

        /**
         * 상태를 바꾸지 않는 메서드 — 이 셋만 CSRF 를 요구하지 않는다. `TRACE` 는 여기 없다
         * (요구 쪽에 두면 어떤 경우에도 통과하지 않는다 — 그쪽이 안전한 방향이다).
         */
        val CSRF_EXEMPT_METHODS: Set<String> = setOf("GET", "HEAD", "OPTIONS")

        /**
         * audit 주체의 값 규약(V20 주석) — 열을 바꾸지 않고 **값으로** 계정을 가리킨다.
         * 계정 식별자는 비밀이 아니다(쿠키·CSRF 값과 달리 추측해도 아무 권한이 없다).
         */
        fun subjectOf(account: AccountId): String = "account:${account.value}"
    }
}
