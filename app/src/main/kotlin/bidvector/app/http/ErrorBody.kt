package bidvector.app.http

import bidvector.adapters.strategy.InvalidStoredStrategyException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

private fun HttpServletRequest.correlationIdOrUnknown(): String = getAttribute(CORRELATION_ID_ATTRIBUTE) as? String ?: "unknown"

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
            is NoHandlerFoundException -> ErrorBody(ErrorCode.NOT_FOUND, "요청한 경로가 없다", correlationId)
            is InvalidStoredStrategyException ->
                ErrorBody(ErrorCode.INVALID_STORED_STRATEGY, "저장된 전략이 유효하지 않다", correlationId)
            else -> ErrorBody(ErrorCode.INTERNAL_ERROR, "요청을 처리하는 중 오류가 발생했다", correlationId)
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
    """{"code":"${escapeJson(code)}","message":"${escapeJson(message)}","correlationId":"${escapeJson(correlationId)}"}"""

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
