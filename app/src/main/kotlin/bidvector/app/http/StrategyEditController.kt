package bidvector.app.http

import bidvector.app.wiring.StrategyEditExecutor
import bidvector.sharedkernel.AccountId
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.OperatorId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * 전략 편집 endpoint 여섯(D-6A2b-1) — 기존 읽기 경로의 쓰기 쪽이다.
 * `EditStrategyWorkflow` 의 command 넷 + `begin` + 조회가 전부이고 새 동작을 만들지
 * 않는다(그 상태 기계·멱등·stale revision·만료는 이미 판정됐다, D-M4-1 채널 독립).
 *
 * **컨트롤러는 조립된 실행기만 받는다**(D-6A2b-8) — 저장소·outbox·`ConnectionSource` 를
 * 이 층에서 볼 수 없다(ArchUnit 의존 게이트가 구조로 강제한다). 요청 → command 변환과
 * 결과 → 상태 코드 변환만 한다(알려진 키 밖의 본문 키는 400 — D-6A2b-25): 변환표는 각각 `StrategyEditRequests.kt`·
 * `EditSessionResponse.kt` 한 자리씩이고 전수 `when` 이라 새 사유·새 필드가 생기면
 * 컴파일이 누락을 잡는다.
 *
 * 인증은 이 클래스가 **판정하지 않는다** — 경로가 `/api` 하위라 기본 거부 필터가 그대로 덮는다.
 * 다만 그 판정의 **결과**(주체)는 여기서 실행기로 넘긴다(verifier r1 F2): 감사 행과 이벤트 봉투가
 * 같은 사람을 가리키려면 둘이 같은 자리에서 와야 한다. 요청 본문·헤더에서 받지 않으므로
 * 행위자를 위조할 길은 없다(우회 (8)).
 */
@RestController
@RequestMapping(STRATEGY_EDIT_SESSIONS)
class StrategyEditController(
    private val executor: StrategyEditExecutor,
) {
    @PostMapping
    fun begin(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        return executor
            .begin(account, actorOf(request), body.requireObject().requireKnownKeys(setOf("field")).field())
            .toResponse(request)
    }

    @GetMapping("/{sessionId}")
    fun view(
        @PathVariable sessionId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        withAccount(request) { account ->
            val session = executor.view(account, sessionIdOf(sessionId))
            if (session == null) sessionNotFound(request) else ResponseEntity.ok(EditSessionResponse.from(session))
        }

    @PostMapping("/{sessionId}/value")
    fun provideValue(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val payload = body.requireObject()
        val field = payload.field()
        return executor
            .provideValue(
                account,
                actorOf(request),
                sessionIdOf(sessionId),
                payload.commandId(),
                field,
                payload.editValue(field),
            ).toResponse(request)
    }

    @PostMapping("/{sessionId}/confirm")
    fun confirm(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        withAccount(request) { account ->
            val payload = body.requireObject().requireKnownKeys(setOf("commandId", "seenRevision"))
            executor
                .confirm(account, actorOf(request), sessionIdOf(sessionId), payload.commandId(), payload.seenRevision())
                .toResponse(request)
        }

    @PostMapping("/{sessionId}/edit")
    fun requestEdit(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        withAccount(request) { account ->
            val payload = body.requireObject().requireKnownKeys(setOf("commandId", "field"))
            executor
                .requestEdit(account, actorOf(request), sessionIdOf(sessionId), payload.commandId(), payload.field())
                .toResponse(request)
        }

    @PostMapping("/{sessionId}/cancel")
    fun cancel(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        withAccount(request) { account ->
            val payload = body.requireObject().requireKnownKeys(setOf("commandId"))
            executor
                .cancel(account, actorOf(request), sessionIdOf(sessionId), payload.commandId())
                .toResponse(request)
        }

    /**
     * **계정 전용 핸들러의 공통 머리 한 자리**(M15/15A) — 세션 계정을 꺼내고 없으면 401 이다.
     *
     * 이 자리가 생긴 이유는 중복이다. 계정 인자가 들어오면서 command 핸들러 넷의 머리 두 줄이
     * 글자까지 같아졌고 `cpdCheck` 가 그것을 잡았다. 핸들러마다 다른 것은 **읽는 키와 부르는
     * command** 뿐이므로, 같은 두 줄을 네 벌 두는 대신 한 자리에 두고 다른 부분만 람다가 진다.
     *
     * 거동은 그대로다 — 주체를 본문 판독 **전에** 꺼내므로 인증되지 않은 요청은 본문 형식 오류가
     * 아니라 401 로 떨어진다(`ProfileCommandController` 와 같은 관례).
     */
    private fun withAccount(
        request: HttpServletRequest,
        action: (AccountId) -> ResponseEntity<Any>,
    ): ResponseEntity<Any> = request.authenticatedAccount()?.let(action) ?: unauthenticated(request)
}

/**
 * 편집 세션의 뿌리(M15/15A) — `@RequestMapping` 과 `AccountScopedRoutes.STRATEGY` 가 같은 문자열을
 * 쓰게 한다. 전략 뿌리 아래라 계정 전용 판정이 이 경로들에도 **선언 하나로** 따라온다.
 */
const val STRATEGY_EDIT_SESSIONS: String = AccountScopedRoutes.STRATEGY + "/edit-sessions"

/**
 * 행위자 — 기본 거부 필터가 적은 **인증 주체** 그대로다(감사 행이 읽는 바로 그 값). 세션 경로는
 * `account:<id>` 이고, 이 경로들은 M15/15A 이후 세션으로만 닿는다.
 */
private fun actorOf(request: HttpServletRequest): OperatorId = OperatorId(request.authenticatedSubject())

/**
 * 경로 변수 → [EditSessionId]. 빈 값은 [EditSessionId] 의 생성 불변식이 `IllegalArgumentException`
 * 으로 막는데, 그것은 500 으로 새는 형태다 — 형식 오류로 옮겨 400 으로 낸다(D-6A2b-7).
 */
private fun sessionIdOf(raw: String): EditSessionId {
    if (raw.isBlank()) throw InvalidEditRequestException("sessionId 가 비어 있다")
    return EditSessionId(raw)
}
