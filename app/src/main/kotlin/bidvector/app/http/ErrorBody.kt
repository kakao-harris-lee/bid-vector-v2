package bidvector.app.http

import bidvector.adapters.evaluation.CandidateCapExceededException
import bidvector.adapters.evaluation.InvalidEvaluationRequestException
import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.app.wiring.MaxActiveBidsNotConfiguredException
import bidvector.workflow.strategy.EditSessionConflictException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException

/**
 * 요청 attribute 키 — [RequestAuditFilter]가 요청마다 한 번 발급한 correlation id·
 * [OperatorCredentialFilter]가 판정한 subject 라벨을 필터 체인과 [GlobalErrorHandler]가
 * 공유하는 유일한 통로다(전역 상태가 아니라 요청 스코프 attribute — DI 대상이 아닌 값을
 * 정적으로 들고 있지 않는다).
 */
const val CORRELATION_ID_ATTRIBUTE = "bidvector.http.correlationId"
const val AUDIT_SUBJECT_ATTRIBUTE = "bidvector.http.subject"

internal fun HttpServletRequest.correlationIdOrUnknown(): String =
    getAttribute(CORRELATION_ID_ATTRIBUTE) as? String ?: "unknown"

/**
 * 오류 응답 형태(D-6A1-7 우회 (3)) — 코드·고정 문구·correlation id 셋뿐이다. 예외 메시지·
 * 스택트레이스를 담지 않는다.
 */
data class ErrorBody(
    val code: String,
    val message: String,
    val correlationId: String,
)

/** [ErrorBody]의 고정 code 값 — 매핑표 밖 사유를 이 상수 밖으로 새지 않게 한다. */
object ErrorCode {
    const val UNAUTHENTICATED = "UNAUTHENTICATED"
    const val NOT_FOUND = "NOT_FOUND"
    const val INVALID_STORED_STRATEGY = "INVALID_STORED_STRATEGY"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"

    // M6/6A-3+6F-3 D-6A3-4·D-6A3-7·D-6A3-5 — 평가 dry-run endpoint 셋(전부 409/400).
    const val MAX_ACTIVE_BIDS_NOT_CONFIGURED = "MAX_ACTIVE_BIDS_NOT_CONFIGURED"
    const val CANDIDATE_CAP_EXCEEDED = "CANDIDATE_CAP_EXCEEDED"
    const val INVALID_REQUEST = "INVALID_REQUEST"

    // M6/6A-2b D-6A2b-6·7 — 편집 endpoint 여섯. 거부 사유 일곱은 전부 409 이고 코드가
    // 사유를 가른다(상태 코드가 아니라 본문이 구분한다). 나머지 넷은 세션 부재(404)·
    // 낙관적 동시성 충돌(409)·값 불변식 위반(400)·메서드/미디어 타입 불일치(405/415)다.
    const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
    const val SESSION_EXPIRED = "SESSION_EXPIRED"
    const val IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
    const val ACTOR_MISMATCH = "ACTOR_MISMATCH"
    const val SYSTEM_ACTOR_NOT_PERMITTED = "SYSTEM_ACTOR_NOT_PERMITTED"
    const val STALE_REVISION = "STALE_REVISION"
    const val INVALID_TRANSITION = "INVALID_TRANSITION"
    const val SESSION_ALREADY_ACTIVE = "SESSION_ALREADY_ACTIVE"
    const val EDIT_SESSION_CONFLICT = "EDIT_SESSION_CONFLICT"
    const val STRATEGY_VALUE_INVALID = "STRATEGY_VALUE_INVALID"
    const val METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED"
    const val UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE"
}

/**
 * 도메인 실패를 HTTP로 옮기는 **유일한** 매핑표(scope.md in_scope 「매핑표는 이 파일
 * 하나」) — 이 표에 없는 [Throwable]은 전부 [ErrorCode.INTERNAL_ERROR] 고정 문구로만
 * 응답한다(우회 (3) 폐쇄). `throwable.message`를 응답에 실을 수 있는 분기를 이 표
 * 어디에도 두지 않는다 — 새 분기를 더할 때도 이 불변식을 지킨다.
 */
object ErrorMapping {
    fun unauthenticated(correlationId: String): ErrorBody =
        ErrorBody(ErrorCode.UNAUTHENTICATED, "인증에 실패했다", correlationId)

    /** [chain.doFilter]를 완전히 벗어난 예외의 최후 방어선([RequestAuditFilter])에서도 쓴다. */
    fun forThrowable(
        throwable: Throwable,
        correlationId: String,
    ): ErrorBody =
        when (throwable) {
            is NoHandlerFoundException -> {
                ErrorBody(ErrorCode.NOT_FOUND, "요청한 경로가 없다", correlationId)
            }

            is InvalidStoredStrategyException -> {
                ErrorBody(ErrorCode.INVALID_STORED_STRATEGY, "저장된 전략이 유효하지 않다", correlationId)
            }

            is MaxActiveBidsNotConfiguredException -> {
                ErrorBody(ErrorCode.MAX_ACTIVE_BIDS_NOT_CONFIGURED, "전략에 여력 상한이 설정되지 않았다", correlationId)
            }

            is CandidateCapExceededException -> {
                ErrorBody(ErrorCode.CANDIDATE_CAP_EXCEEDED, "후보 스캔이 상한을 초과했다", correlationId)
            }

            is InvalidEvaluationRequestException -> {
                ErrorBody(ErrorCode.INVALID_REQUEST, "요청 값이 유효하지 않다", correlationId)
            }

            // M6/6A-3+6F-3 D-6A3-19(검토 라운드 1 contract-keeper V1 · verifier MEDIUM) —
            // 비JSON·빈 본문은 EvaluationDryRunController의 parseCurrentActiveBids 에
            // 닿기 전에 Jackson 자체가 여기서 던진다(HttpMessageNotReadableException).
            // 같은 코드(INVALID_REQUEST)로 옮긴다 — 예외 메시지는 싣지 않는다(D-6A1-7 불변식).
            is HttpMessageNotReadableException -> {
                ErrorBody(ErrorCode.INVALID_REQUEST, "요청 본문을 읽을 수 없다", correlationId)
            }

            // M6/6A-2b D-6A2b-7 — 편집 endpoint 가 이 앱의 첫 쓰기 표면이다. 형식 오류가
            // 기본 분기(500)로 떨어지면 그것이 스택 노출의 문이 된다.
            is InvalidEditRequestException -> {
                ErrorBody(ErrorCode.INVALID_REQUEST, "요청 값이 유효하지 않다", correlationId)
            }

            is EditSessionConflictException -> {
                ErrorBody(ErrorCode.EDIT_SESSION_CONFLICT, "편집 세션이 동시에 바뀌었다", correlationId)
            }

            is HttpRequestMethodNotSupportedException -> {
                ErrorBody(ErrorCode.METHOD_NOT_ALLOWED, "이 경로가 지원하지 않는 메서드다", correlationId)
            }

            is HttpMediaTypeNotSupportedException -> {
                ErrorBody(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입이다", correlationId)
            }

            else -> {
                ErrorBody(ErrorCode.INTERNAL_ERROR, "요청을 처리하는 중 오류가 발생했다", correlationId)
            }
        }
}

/**
 * 3자 이스케이프만 하는 최소 JSON 직렬화(D-6A1-9/19의 정신과 같은 축 — 이 파일이 유일한
 * 오류 형태 생성 경로이듯, 이 함수가 servlet Filter 층(Spring MVC 진입 전)에서 [ErrorBody]를
 * 응답에 싣는 유일한 통로다). Filter 층은 Spring의 `HttpMessageConverter` 파이프라인 밖이라
 * (`OperatorCredentialFilter`의 401, `RequestAuditFilter`의 fail-closed 500) Jackson을
 * 직접 부르지 않고 고정 세 필드짜리 이 함수로 충분하다 — 컨트롤러 응답과
 * [GlobalErrorHandler](이 파일 아래)는 Spring이 이미 배선한 JSON 변환을 그대로 쓴다.
 */
fun ErrorBody.toJson(): String =
    """{"code":"${escapeJson(
        code,
    )}","message":"${escapeJson(message)}","correlationId":"${escapeJson(correlationId)}"}"""

private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

/**
 * DispatcherServlet 안에서 일어나는 모든 예외의 유일한 처리기(우회 (3)·(6)) — 도메인
 * 실패도, `NoHandlerFoundException`(D-6A1-21, `spring.mvc.throw-exception-if-no-handler-found`)
 * 도, 그 밖 매핑표에 없는 어떤 [Throwable]도 여기 한 곳을 지난다. [correlationId]는
 * [RequestAuditFilter]가 요청 시작 시 발급해 request attribute에 심어 둔 값을 그대로
 * 읽는다(발급 지점 하나, 재발급하지 않는다).
 */
@RestControllerAdvice
class GlobalErrorHandler {
    @ExceptionHandler(NoHandlerFoundException::class)
    fun notFound(
        exception: NoHandlerFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.NOT_FOUND, exception, request)

    @ExceptionHandler(InvalidStoredStrategyException::class)
    fun invalidStoredStrategy(
        exception: InvalidStoredStrategyException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.INTERNAL_SERVER_ERROR, exception, request)

    @ExceptionHandler(MaxActiveBidsNotConfiguredException::class)
    fun maxActiveBidsNotConfigured(
        exception: MaxActiveBidsNotConfiguredException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.CONFLICT, exception, request)

    @ExceptionHandler(CandidateCapExceededException::class)
    fun candidateCapExceeded(
        exception: CandidateCapExceededException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.CONFLICT, exception, request)

    @ExceptionHandler(InvalidEvaluationRequestException::class)
    fun invalidEvaluationRequest(
        exception: InvalidEvaluationRequestException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.BAD_REQUEST, exception, request)

    /** D-6A3-19 — 요청 본문 역직렬화 실패(비JSON·빈 본문)도 400 `INVALID_REQUEST` 다. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun httpMessageNotReadable(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.BAD_REQUEST, exception, request)

    /** D-6A2b-7 — 형식·상태 오류 넷. 전부 [ErrorMapping] 의 같은 표를 지난다. */
    @ExceptionHandler(InvalidEditRequestException::class)
    fun invalidEditRequest(
        exception: InvalidEditRequestException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.BAD_REQUEST, exception, request)

    @ExceptionHandler(EditSessionConflictException::class)
    fun editSessionConflict(
        exception: EditSessionConflictException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.CONFLICT, exception, request)

    /**
     * 405 는 `Allow` 헤더를 함께 낸다(RFC 9110 §15.5.6 — 필수) — 값은 Spring 이 그 경로의
     * 매핑에서 도출한 집합 그대로다(손으로 적지 않는다).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotSupported(
        exception: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> {
        val correlationId = request.correlationIdOrUnknown()
        val builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        exception.supportedHttpMethods?.let { builder.allow(*it.toTypedArray()) }
        return builder.body(ErrorMapping.forThrowable(exception, correlationId))
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaTypeNotSupported(
        exception: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception, request)

    @ExceptionHandler(Throwable::class)
    fun fallback(
        exception: Throwable,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.INTERNAL_SERVER_ERROR, exception, request)

    private fun respond(
        status: HttpStatus,
        exception: Throwable,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> {
        val correlationId = request.correlationIdOrUnknown()
        return ResponseEntity.status(status).body(ErrorMapping.forThrowable(exception, correlationId))
    }
}
