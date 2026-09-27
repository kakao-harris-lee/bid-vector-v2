package bidvector.app.http

import bidvector.app.wiring.ProvideValueOutcome
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditSession
import bidvector.workflow.strategy.EditSessionState
import bidvector.workflow.strategy.RejectionReason
import bidvector.workflow.strategy.TransitionOutcome
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * 편집 세션 응답 — **평탄하다**(D-6A1-20 ⓑ, `OpenApiContractTest` 의 순회가 문서와 실제
 * 응답값 양쪽에서 이 평탄함을 잰다). 초안(draft) 전체는 싣지 않는다 — 확인 중인 값은
 * 운영자가 방금 보낸 값이고, 현재 전략은 `GET /api/strategy` 가 낸다.
 */
data class EditSessionResponse(
    val sessionId: String,
    val state: String,
    val field: String?,
    val sessionVersion: Int,
    val expiresAt: String,
    val strategyRevision: Int?,
    /**
     * D-6A2b-18 — 확인 대기 중인 draft 를 뜬 시점의 전략 revision. 클라이언트가 확인 전에
     * 「내 초안이 아직 최신 위에 서 있는가」를 볼 수 있는 유일한 값이다(그 대조 자체는
     * 서버가 한다 — 이 값은 보고이지 신뢰 입력이 아니다). 확인 대기가 아니면 `null`.
     */
    val baseRevision: Int?,
) {
    companion object {
        fun from(session: EditSession): EditSessionResponse =
            EditSessionResponse(
                sessionId = session.id.value,
                state = session.state.token(),
                field = session.state.fieldToken(),
                sessionVersion = session.sessionVersion,
                expiresAt = session.expiresAt.toString(),
                strategyRevision = (session.state as? EditSessionState.Applied)?.revision?.value,
                baseRevision =
                    (session.state as? EditSessionState.WaitingForConfirmation)?.baseRevision?.value,
            )
    }
}

/**
 * 상태의 wire 어휘(전수 `when`) — 저장 어휘(`EditSessionSnapshot` 의 `stateKindName`)는
 * `workflow` 안에서 `private` 이라 재사용할 수 없다. 같은 낱말을 쓰되 출처가 다르다
 * (`StrategyReadController.provenanceLabel` 과 같은 갈래의 알려진 중복 — 모듈 경계가 막았다).
 *
 * `internal` 인 이유는 test 편의가 아니라 **계약 등식**이다(C-5a) — 문서의 `state` enum 과
 * 이 표가 같은 어휘를 말하는지 잴 자리가 필요하고, 그 비교는 이 함수 자신이 유일한 출처다.
 */
internal fun EditSessionState.token(): String =
    when (this) {
        is EditSessionState.WaitingForValue -> "WAITING_FOR_VALUE"
        is EditSessionState.WaitingForConfirmation -> "WAITING_FOR_CONFIRMATION"
        is EditSessionState.Applied -> "APPLIED"
        is EditSessionState.Cancelled -> "CANCELLED"
        EditSessionState.Expired -> "EXPIRED"
    }

private fun EditSessionState.fieldToken(): String? =
    when (this) {
        is EditSessionState.WaitingForValue -> field.token()
        is EditSessionState.WaitingForConfirmation -> field.token()
        is EditSessionState.Applied, is EditSessionState.Cancelled, EditSessionState.Expired -> null
    }

/**
 * 거부 사유 → 구조화 코드(D-6A2b-6, 전수 `when` · `else` 없음) — 새 사유가 생기면
 * 컴파일이 매핑 누락을 잡는다. 사유 일곱은 전부 409 다(상태 충돌) — 상태 코드가 사유를
 * 구분하지 않고 본문의 코드가 구분한다. 예외 메시지·스택·SQL 은 어느 갈래에도 실리지
 * 않는다(`ErrorBody` 는 코드·고정 문구·correlation id 셋뿐이다).
 */
private fun RejectionReason.code(): String =
    when (this) {
        RejectionReason.SessionExpired -> ErrorCode.SESSION_EXPIRED
        RejectionReason.IdempotencyConflict -> ErrorCode.IDEMPOTENCY_CONFLICT
        RejectionReason.ActorMismatch -> ErrorCode.ACTOR_MISMATCH
        RejectionReason.SystemActorNotPermitted -> ErrorCode.SYSTEM_ACTOR_NOT_PERMITTED
        RejectionReason.StaleRevision -> ErrorCode.STALE_REVISION
        RejectionReason.InvalidTransition -> ErrorCode.INVALID_TRANSITION
        RejectionReason.SessionAlreadyActive -> ErrorCode.SESSION_ALREADY_ACTIVE
    }

private fun rejected(
    reason: RejectionReason,
    request: HttpServletRequest,
): ResponseEntity<Any> =
    ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ErrorBody(reason.code(), "편집 세션이 이 명령을 받을 수 없는 상태다", request.correlationIdOrUnknown()))

/** `begin` — 새로 열렸으면 201, 이미 비종단 세션이 있으면 409(우회 (4)). */
internal fun BeginOutcome.toResponse(request: HttpServletRequest): ResponseEntity<Any> =
    when (this) {
        is BeginOutcome.Started -> ResponseEntity.status(HttpStatus.CREATED).body(EditSessionResponse.from(session))
        is BeginOutcome.Rejected -> rejected(reason, request)
    }

/** command 넷 — 세션 부재 404, 거부 409, 그 밖(수용·적용) 200. */
internal fun CommandResult.toResponse(request: HttpServletRequest): ResponseEntity<Any> =
    when (this) {
        CommandResult.SessionNotFound -> sessionNotFound(request)
        is CommandResult.Processed -> outcome.toResponse(request)
    }

private fun TransitionOutcome.toResponse(request: HttpServletRequest): ResponseEntity<Any> =
    when (this) {
        is TransitionOutcome.Rejected -> rejected(reason, request)
        is TransitionOutcome.Accepted -> ResponseEntity.ok(EditSessionResponse.from(session))
        is TransitionOutcome.Applied -> ResponseEntity.ok(EditSessionResponse.from(session))
    }

/**
 * 값 제출 — 불변식 위반은 400 이다(우회 (7)). 어느 불변식이 깨졌는지는 응답에 싣지
 * 않는다: `ErrorBody` 는 세 필드 고정이고 이 slice 가 그 계약을 넓히지 않는다
 * (`OPEN-6A2B-VIOLATION-DETAIL`, `OPEN-6A3-EVALUATION-DETAIL` 과 같은 갈래).
 */
internal fun ProvideValueOutcome.toResponse(request: HttpServletRequest): ResponseEntity<Any> =
    when (this) {
        is ProvideValueOutcome.Invalid -> {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                    ErrorBody(
                        ErrorCode.STRATEGY_VALUE_INVALID,
                        "전략 값이 불변식을 어긴다",
                        request.correlationIdOrUnknown(),
                    ),
                )
        }

        is ProvideValueOutcome.Processed -> {
            result.toResponse(request)
        }
    }

internal fun sessionNotFound(request: HttpServletRequest): ResponseEntity<Any> =
    ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ErrorBody(ErrorCode.SESSION_NOT_FOUND, "편집 세션이 없다", request.correlationIdOrUnknown()))
