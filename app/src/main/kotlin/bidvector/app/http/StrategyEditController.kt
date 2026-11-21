package bidvector.app.http

import bidvector.app.wiring.StrategyEditExecutor
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
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val session = executor.view(account, sessionIdOf(sessionId)) ?: return sessionNotFound(request)
        return ResponseEntity.ok(EditSessionResponse.from(session))
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
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val payload = body.requireObject().requireKnownKeys(setOf("commandId", "seenRevision"))
        return executor
            .confirm(account, actorOf(request), sessionIdOf(sessionId), payload.commandId(), payload.seenRevision())
            .toResponse(request)
    }

    @PostMapping("/{sessionId}/edit")
    fun requestEdit(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val payload = body.requireObject().requireKnownKeys(setOf("commandId", "field"))
        return executor
            .requestEdit(account, actorOf(request), sessionIdOf(sessionId), payload.commandId(), payload.field())
            .toResponse(request)
    }

    @PostMapping("/{sessionId}/cancel")
    fun cancel(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val payload = body.requireObject().requireKnownKeys(setOf("commandId"))
        return executor
            .cancel(account, actorOf(request), sessionIdOf(sessionId), payload.commandId())
            .toResponse(request)
    }
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
