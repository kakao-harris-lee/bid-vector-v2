package bidvector.app.http

import bidvector.app.wiring.StrategyEditExecutor
import bidvector.workflow.strategy.EditSessionId
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
 * 전략 편집 endpoint 여섯(M6/6A-2b D-6A2b-1) — 6A-1 이 연 읽기 경로의 쓰기 쪽이다.
 * `EditStrategyWorkflow` 의 command 넷 + `begin` + 조회가 전부이고 새 동작을 만들지
 * 않는다(그 상태 기계·멱등·stale revision·만료는 M4 가 이미 판정했다, D-M4-1 채널 독립).
 *
 * **컨트롤러는 조립된 실행기만 받는다**(D-6A2b-8) — 저장소·outbox·`ConnectionSource` 를
 * 이 층에서 볼 수 없다(ArchUnit 의존 게이트가 구조로 강제한다). 요청 → command 변환과
 * 결과 → 상태 코드 변환만 한다: 변환표는 각각 `StrategyEditRequests.kt`·
 * `StrategyEditResponses.kt` 한 자리씩이고 전수 `when` 이라 새 사유·새 필드가 생기면
 * 컴파일이 누락을 잡는다.
 *
 * 인증은 이 클래스가 알지 못한다 — 경로가 `/api` 하위라 6A-1 의 필터 체인이 그대로 덮는다
 * (D-6A2b-5, 필터 두 파일 무편집). 행위자도 받지 않는다(실행기의 상수, 우회 (8)).
 */
@RestController
@RequestMapping("/api/strategy/edit-sessions")
class StrategyEditController(
    private val executor: StrategyEditExecutor,
) {
    @PostMapping
    fun begin(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> = executor.begin(body.requireObject().field()).toResponse(request)

    @GetMapping("/{sessionId}")
    fun view(
        @PathVariable sessionId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val session = executor.view(sessionIdOf(sessionId)) ?: return sessionNotFound(request)
        return ResponseEntity.ok(EditSessionResponse.from(session))
    }

    @PostMapping("/{sessionId}/value")
    fun provideValue(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val payload = body.requireObject()
        val field = payload.field()
        return executor
            .provideValue(sessionIdOf(sessionId), payload.commandId(), field, payload.editValue(field))
            .toResponse(request)
    }

    @PostMapping("/{sessionId}/confirm")
    fun confirm(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val payload = body.requireObject()
        return executor.confirm(sessionIdOf(sessionId), payload.commandId(), payload.seenRevision()).toResponse(request)
    }

    @PostMapping("/{sessionId}/edit")
    fun requestEdit(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val payload = body.requireObject()
        return executor.requestEdit(sessionIdOf(sessionId), payload.commandId(), payload.field()).toResponse(request)
    }

    @PostMapping("/{sessionId}/cancel")
    fun cancel(
        @PathVariable sessionId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val payload = body.requireObject()
        return executor.cancel(sessionIdOf(sessionId), payload.commandId()).toResponse(request)
    }
}

/**
 * 경로 변수 → [EditSessionId]. 빈 값은 [EditSessionId] 의 생성 불변식이 `IllegalArgumentException`
 * 으로 막는데, 그것은 500 으로 새는 형태다 — 형식 오류로 옮겨 400 으로 낸다(D-6A2b-7).
 */
private fun sessionIdOf(raw: String): EditSessionId {
    if (raw.isBlank()) throw InvalidEditRequestException("sessionId 가 비어 있다")
    return EditSessionId(raw)
}
