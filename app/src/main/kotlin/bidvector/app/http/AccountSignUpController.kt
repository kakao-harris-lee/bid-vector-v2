package bidvector.app.http

import bidvector.app.wiring.BrowserAccessExecutor
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.SignUpOutcome
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/** 가입 endpoint 의 경로 **한 자리** — 허용 목록·계약 문서·이 매핑이 같은 문자열을 본다. */
const val ACCOUNTS_PATH: String = "/api/accounts"

/**
 * 열린 가입(M15/15B 산출물 1) — 계정을 만들고 **곧바로 로그인 세션을 낸다**. 초대 코드도 운영자
 * 승인도 지나지 않는다(운영자 지시 2026-11-21, 테스트 단계 진입용).
 *
 * **응답이 `POST /api/session` 과 같은 모양**인 것이 계약이다: 같은 `Set-Cookie` 와 같은
 * [SessionResponse]. 그래서 화면은 가입 뒤 따로 로그인하지 않는다.
 *
 * **거부의 사유를 나눈다** — 로그인과 반대다. 로그인에서 사유를 나누면 「그 계정이 있다」가 새지만,
 * 가입은 그 사실을 **말해야 하는** 자리다(이미 쓰는 이름은 쓸 수 없다). 칸별로 가르는 것도 같은
 * 이유다: 화면이 어느 칸 옆에 문장을 둘지 알아야 하고, 그 규칙은 공개된 값이다.
 *
 * **무인증 CSRF 대책은 로그인과 같다**([OperatorSessionController] 의 그 절): `application/json`
 * 만 받으므로 교차 사이트 HTML form 이 preflight 없이 닿지 못하고, 발급 쿠키가 `SameSite=Strict` 다.
 *
 * **인증은 이 클래스가 하지 않는다** — [RequestAuthenticationFilter] 의 허용 목록이 이 경로를 연다.
 * 남용 상한만 이 자리에 있다([SignUpThrottle]): 키가 원격 주소라 도메인 어휘가 아니다.
 */
@RestController
@RequestMapping(ACCOUNTS_PATH)
class AccountSignUpController(
    private val access: BrowserAccessExecutor,
    private val cookies: SessionCookieWriter,
    private val throttle: SignUpThrottle,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun signUp(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val payload = body.requireObject().requireKnownKeys(setOf("loginName", "passphrase"))
        val login = LoginName(payload.requiredNonBlankText("loginName"))
        val clear = ClearPassphrase.wrap(payload.requiredNonBlankText("passphrase"))
        // **상한이 KDF 보다 앞**이다 — 뒤에 두면 거부당할 요청도 해시를 한 번 돌리고, 그 비용이
        // 무인증으로 닿는 자리의 증폭이 된다.
        if (!throttle.admit(request.remoteAddr.orEmpty())) return refused(request, RATE_LIMITED)
        return respond(access.signUp(login, clear), request)
    }

    /** 결과 → 응답은 **소진 `when`** 이다 — 갈래가 늘면 컴파일이 이 자리를 먼저 가리킨다. */
    private fun respond(
        outcome: SignUpOutcome,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        when (outcome) {
            is SignUpOutcome.Granted -> {
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

            SignUpOutcome.RefusedLoginNameShape -> refused(request, LOGIN_NAME_INVALID)
            SignUpOutcome.RefusedPassphraseShape -> refused(request, PASSPHRASE_INVALID)
            SignUpOutcome.RefusedDuplicate -> refused(request, ACCOUNT_EXISTS)
            SignUpOutcome.SessionUnavailable -> refused(request, SESSION_UNAVAILABLE)
        }

    /**
     * 거부 응답 하나 — 상태 코드와 (코드, 고정 문구) 쌍이 **표 하나**에서 온다. 문면에 입력값을
     * 싣지 않는다([ErrorBody] 의 규율 그대로).
     */
    private fun refused(
        request: HttpServletRequest,
        refusal: SignUpRefusal,
    ): ResponseEntity<Any> =
        ResponseEntity
            .status(refusal.status)
            .body(ErrorBody(refusal.code, refusal.message, request.correlationIdOrUnknown()))

    private companion object {
        val LOGIN_NAME_INVALID =
            SignUpRefusal(HttpStatus.BAD_REQUEST, ErrorCode.LOGIN_NAME_INVALID, "아이디의 형식이 올바르지 않다")
        val PASSPHRASE_INVALID =
            SignUpRefusal(HttpStatus.BAD_REQUEST, ErrorCode.PASSPHRASE_INVALID, "비밀번호의 형식이 올바르지 않다")

        /** **다른 정보를 함께 내지 않는다** — 그 계정이 언제 생겼는지·누구인지 0. */
        val ACCOUNT_EXISTS = SignUpRefusal(HttpStatus.CONFLICT, ErrorCode.ACCOUNT_EXISTS, "이미 쓰이는 아이디다")
        val RATE_LIMITED =
            SignUpRefusal(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.SIGN_UP_RATE_LIMITED, "가입 시도가 너무 잦다")

        /**
         * 계정은 섰는데 세션이 서지 않았다 — **계정을 지우지 않는다**(권한이 새는 방향이 아니다).
         * 그 사람은 로그인으로 들어올 수 있으므로 화면이 그렇게 안내한다.
         */
        val SESSION_UNAVAILABLE =
            SignUpRefusal(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SIGN_UP_SESSION_UNAVAILABLE,
                "계정은 만들어졌으나 로그인 세션을 열지 못했다",
            )
    }
}

/**
 * 거부 하나의 (상태, 코드, 고정 문구). **`data class` 가 아니다** — 한 번 읽히고 버려지는 표의
 * 행이라 `equals`/`copy` 가 필요하지 않다.
 */
private class SignUpRefusal(
    val status: HttpStatus,
    val code: String,
    val message: String,
)
