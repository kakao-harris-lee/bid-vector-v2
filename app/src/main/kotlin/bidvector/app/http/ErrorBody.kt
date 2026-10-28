package bidvector.app.http

import bidvector.adapters.evaluation.CandidateCapExceededException
import bidvector.adapters.evaluation.InvalidEvaluationRequestException
import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.app.wiring.MaxActiveBidsNotConfiguredException
import bidvector.app.wiring.NoticeNotFoundException
import bidvector.workflow.strategy.EditSessionConflictException
import bidvector.workflow.suggestion.StrategyChangedDuringComputationException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException

/**
 * 편집 요청의 형식 오류(D-6A2b-7) — 400 `INVALID_REQUEST`. 메시지는 응답에 실리지
 * 않는다(아래 [ErrorMapping] 이 고정 문구로만 옮긴다) — 진단용이다. 던지는 자리는
 * `StrategyEditRequests.kt` 의 파싱 함수들이고, 오류 어휘는 이 파일 하나가 갖는다.
 */
class InvalidEditRequestException(
    message: String,
) : RuntimeException(message)

/**
 * 공고 검색 질의의 형식·범위 오류(M7/7C ①) — 400 `INVALID_REQUEST`. 메시지는 거부 사유
 * 어휘(`NoticeSearchRejection`)를 진단용으로 나르고 **응답에는 실리지 않는다**(아래
 * [ErrorMapping] 이 고정 문구로만 옮긴다 — 어느 입력이 틀렸는지는 싣지 않는다).
 *
 * **생성자는 문구 하나만 받는다.** 질의를 **읽다가** 깨진 경우의 원래 실패는 `initCause` 로
 * 단다 — 생성자 매개변수로 받으면 주입 표면 게이트가 「이 class 가 `Throwable` 을 주입받는다」로
 * 읽어 그 이름을 주입 허용 목록에 올려야 한다(이 class 는 주입 대상이 아니다, 실측). 원인은
 * 어느 쪽으로 달든 응답에 닿지 않는다 — 같은 매핑표가 고정 문구만 낸다.
 */
class InvalidNoticeSearchException(
    message: String,
) : RuntimeException(message)

/**
 * 개찰 결과 조회 질의의 형식 오류(M7/7C⑤-a) — 400 `INVALID_REQUEST`. 메시지는 거부 사유 어휘
 * (`OpeningResultQueryRejection`)를 진단용으로 나르고 **응답에는 실리지 않는다**.
 *
 * 공고 검색의 거부와 **다른 타입**인 이유: 코드·고정 문구가 같아도 어느 표면이 거부했는지가
 * 타입으로 남아야 매핑표를 읽는 사람이 그 표면을 찾을 수 있다. 생성자가 문구 하나만 받는 것은
 * [InvalidNoticeSearchException] 과 같은 이유다(주입 표면 게이트).
 */
class InvalidOpeningResultQueryException(
    message: String,
) : RuntimeException(message)

/**
 * 요청 attribute 키 — [RequestAuditFilter]가 요청마다 한 번 발급한 correlation id·
 * [RequestAuthenticationFilter]가 판정한 subject 라벨을 필터 체인과 [GlobalErrorHandler]가
 * 공유하는 유일한 통로다(전역 상태가 아니라 요청 스코프 attribute — DI 대상이 아닌 값을
 * 정적으로 들고 있지 않는다).
 */
const val CORRELATION_ID_ATTRIBUTE = "bidvector.http.correlationId"
const val AUDIT_SUBJECT_ATTRIBUTE = "bidvector.http.subject"

/**
 * 인증된 주체 — **필터가 적은 값**이다(요청이 보낸 값이 아니다, verifier r1 F2). 감사 행의
 * subject 와 **같은 속성**을 읽으므로 둘이 갈릴 수 없다: 갈리게 하려면 필터 한 자리를 고쳐야
 * 하고 그러면 두 값이 함께 움직인다.
 *
 * 속성이 없는 경로는 없다(기본 거부 필터가 허용·거부 양쪽에서 적는다) — 그래도 기본값은
 * 「신원 없음」으로 둔다(없는 것을 운영자로 올리지 않는다).
 */
internal fun HttpServletRequest.authenticatedSubject(): String =
    getAttribute(AUDIT_SUBJECT_ATTRIBUTE) as? String ?: RequestAuthenticationFilter.UNAUTHENTICATED_SUBJECT

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

    /**
     * M7/7C ② — 요청한 **공고**가 없다. [NOT_FOUND](요청한 **경로**가 없다)와 다른 코드다:
     * 고정 문구가 하나면 「경로를 잘못 불렀다」와 「그 공고가 없다」를 호출부가 구분할 수 없다.
     * 공고의 존재는 공개 정보라 구분이 아무것도 흘리지 않는다(인증 실패와 다른 축이다).
     */
    const val NOTICE_NOT_FOUND = "NOTICE_NOT_FOUND"

    // D-6A3-4·D-6A3-7·D-6A3-5 — 평가 dry-run endpoint 셋(전부 409/400).
    const val MAX_ACTIVE_BIDS_NOT_CONFIGURED = "MAX_ACTIVE_BIDS_NOT_CONFIGURED"
    const val CANDIDATE_CAP_EXCEEDED = "CANDIDATE_CAP_EXCEEDED"
    const val INVALID_REQUEST = "INVALID_REQUEST"

    // M7/7C①b — 제안 목록 계산 중 전략(또는 프로필)이 두 번 연속 바뀌었다(409). 낡은 결과도
    // 지어낸 결과도 내지 않는다.
    const val STRATEGY_CHANGED_DURING_COMPUTATION = "STRATEGY_CHANGED_DURING_COMPUTATION"

    // D-6A2b-6·7 — 편집 endpoint 여섯. 거부 사유 일곱은 전부 409 이고 코드가
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

    // M7/7B — 입찰 건. 전이 거부는 409 이고 코드가 사유를 가른다(상태 코드가 아니라 본문).
    // `NOTICE_SNAPSHOT_UNAVAILABLE` 은 초안을 만들려는데 공고를 뜰 수 없는 경우다 —
    // 스냅샷 없는 초안을 만들지 않는다(§6.1 acceptance).
    const val NOTICE_SNAPSHOT_UNAVAILABLE = "NOTICE_SNAPSHOT_UNAVAILABLE"

    // M7/7C④ 2b — 한 계정의 시평액 행 수 상한(`ProfileInputPolicy.CAPACITY_ROWS_MAX`). 409 다:
    // 요청은 올바르고 **계정의 상태가 받지 않는다**. 상한 값은 본문에 싣지 않는다(세 필드).
    const val CAPACITY_ROWS_EXCEEDED = "CAPACITY_ROWS_EXCEEDED"
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

    /**
     * [chain.doFilter]를 완전히 벗어난 예외의 최후 방어선([RequestAuditFilter])에서도 쓴다.
     *
     * **표는 하나다 — 아래 두 `when` 은 크기 게이트(함수 50줄) 때문에 기계적으로 나눈
     * 같은 표의 앞뒤다.** 갈래를 더할 자리가 둘이 됐지만 **기본값은 여전히 여기
     * 한 곳**이고, 어느 갈래도 `throwable.message` 를 응답에 싣지 않는다 — 두 함수의
     * 반환 타입이 (코드, 고정 문구) 쌍이라 예외 값이 본문에 닿을 통로 자체가 없다.
     */
    fun forThrowable(
        throwable: Throwable,
        correlationId: String,
    ): ErrorBody {
        val (code, message) =
            readPathCode(throwable)
                ?: editPathCode(throwable)
                ?: (ErrorCode.INTERNAL_ERROR to "요청을 처리하는 중 오류가 발생했다")
        return ErrorBody(code, message, correlationId)
    }

    /** 조회·dry-run 이 내는 갈래. */
    private fun readPathCode(throwable: Throwable): Pair<String, String>? =
        when (throwable) {
            is NoHandlerFoundException -> {
                ErrorCode.NOT_FOUND to "요청한 경로가 없다"
            }

            is InvalidStoredStrategyException -> {
                ErrorCode.INVALID_STORED_STRATEGY to "저장된 전략이 유효하지 않다"
            }

            is MaxActiveBidsNotConfiguredException -> {
                ErrorCode.MAX_ACTIVE_BIDS_NOT_CONFIGURED to "전략에 여력 상한이 설정되지 않았다"
            }

            is CandidateCapExceededException -> {
                ErrorCode.CANDIDATE_CAP_EXCEEDED to "후보 스캔이 상한을 초과했다"
            }

            is StrategyChangedDuringComputationException -> {
                ErrorCode.STRATEGY_CHANGED_DURING_COMPUTATION to "계산하는 동안 전략이 바뀌었다 — 다시 조회하라"
            }

            is InvalidEvaluationRequestException -> {
                ErrorCode.INVALID_REQUEST to "요청 값이 유효하지 않다"
            }

            // M7/7C ② — 요청한 공고가 없다(평가를 시작하지 않았다).
            is NoticeNotFoundException -> {
                ErrorCode.NOTICE_NOT_FOUND to "요청한 공고를 찾을 수 없다"
            }

            // M7/7C ① — 공고 검색 질의의 형식·범위 오류. 같은 코드·같은 고정 문구다.
            is InvalidNoticeSearchException -> {
                ErrorCode.INVALID_REQUEST to "요청 값이 유효하지 않다"
            }

            // M7/7C⑤-a — 개찰 결과 조회 질의의 형식 오류. 같은 코드·같은 고정 문구다.
            is InvalidOpeningResultQueryException -> {
                ErrorCode.INVALID_REQUEST to "요청 값이 유효하지 않다"
            }

            // D-6A3-19 — 비JSON·빈 본문은 컨트롤러의 명시 검증에 닿기 전에 Jackson 이 던진다.
            is HttpMessageNotReadableException -> {
                ErrorCode.INVALID_REQUEST to "요청 본문을 읽을 수 없다"
            }

            else -> {
                null
            }
        }

    /**
     * 편집 쓰기가 내는 갈래 — 본문을 받는 쓰기 표면이다.
     * 형식·상태 오류가 기본 분기(500)로 떨어지면 그것이 스택 노출의 문이 된다(D-6A2b-7).
     */
    private fun editPathCode(throwable: Throwable): Pair<String, String>? =
        when (throwable) {
            is InvalidEditRequestException -> ErrorCode.INVALID_REQUEST to "요청 값이 유효하지 않다"
            is EditSessionConflictException -> ErrorCode.EDIT_SESSION_CONFLICT to "편집 세션이 동시에 바뀌었다"
            is HttpRequestMethodNotSupportedException -> ErrorCode.METHOD_NOT_ALLOWED to "이 경로가 지원하지 않는 메서드다"
            is HttpMediaTypeNotSupportedException -> ErrorCode.UNSUPPORTED_MEDIA_TYPE to "지원하지 않는 미디어 타입이다"
            else -> null
        }
}

/**
 * 3자 이스케이프만 하는 최소 JSON 직렬화(D-6A1-9/19의 정신과 같은 축 — 이 파일이 유일한
 * 오류 형태 생성 경로이듯, 이 함수가 servlet Filter 층(Spring MVC 진입 전)에서 [ErrorBody]를
 * 응답에 싣는 유일한 통로다). Filter 층은 Spring의 `HttpMessageConverter` 파이프라인 밖이라
 * (`RequestAuthenticationFilter`의 401, `RequestAuditFilter`의 fail-closed 500) Jackson을
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
 * 도, 그 밖 매핑표에 없는 어떤 [Throwable]도 여기 한 곳을 지난다. **예외 하나** — 406 은
 * 본문이 없다(아래 [mediaTypeNotAcceptable]). [correlationId]는
 * [RequestAuditFilter]가 요청 시작 시 발급해 request attribute에 심어 둔 값을 그대로
 * 읽는다(발급 지점 하나, 재발급하지 않는다).
 */
@RestControllerAdvice
class GlobalErrorHandler {
    /**
     * 404 계열 — 경로가 없거나(D-6A1-21) 요청한 공고가 없다(M7/7C ②). 같은 상태 코드이고
     * 본문의 코드가 사유를 가른다(D-6A2b-6 과 같은 규율).
     */
    @ExceptionHandler(
        NoHandlerFoundException::class,
        NoticeNotFoundException::class,
    )
    fun notFound(
        exception: Throwable,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.NOT_FOUND, exception, request)

    @ExceptionHandler(InvalidStoredStrategyException::class)
    fun invalidStoredStrategy(
        exception: InvalidStoredStrategyException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.INTERNAL_SERVER_ERROR, exception, request)

    /**
     * 409 계열 — 저장소 상태가 요청과 맞지 않는다. 넷 다 같은 표([ErrorMapping])를 지나
     * 서로 다른 코드를 낸다(상태 코드가 아니라 본문의 코드가 사유를 가른다, D-6A2b-6).
     */
    @ExceptionHandler(
        MaxActiveBidsNotConfiguredException::class,
        CandidateCapExceededException::class,
        EditSessionConflictException::class,
        StrategyChangedDuringComputationException::class,
    )
    fun conflict(
        exception: Throwable,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.CONFLICT, exception, request)

    /**
     * 400 계열 — 요청 형식·값이 유효하지 않다(D-6A3-19·D-6A2b-7). 예외 메시지는 어느
     * 갈래에서도 응답에 실리지 않는다.
     */
    @ExceptionHandler(
        InvalidEvaluationRequestException::class,
        HttpMessageNotReadableException::class,
        InvalidEditRequestException::class,
        InvalidNoticeSearchException::class,
        InvalidOpeningResultQueryException::class,
    )
    fun badRequest(
        exception: Throwable,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.BAD_REQUEST, exception, request)

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

    /**
     * **406 만 본문이 없다**(D-6A2b-29, 실측). 클라이언트가 「JSON 은 받지
     * 않겠다」고 말한 요청에 JSON 오류 본문을 내는 것이 오히려 협상 위반이고, 실제로 Spring 도
     * 그 본문을 쓸 수 없다(`content-length: 0` 실측). 「모든 오류는 `ErrorBody`」의 유일한
     * 예외이며, 추적은 audit 행이 진다(406 도 행이 1 는다 — 실측).
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun mediaTypeNotAcceptable(): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build()

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaTypeNotSupported(
        exception: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> = respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception, request)

    /**
     * 매핑표 밖의 모든 [Throwable] — 500 이다.
     *
     * **요청 디코딩 실패는 여기까지 오지 않는다**(verifier r2 R2-M-2 · r3 R3-M-1). 잘못된 퍼센트
     * 인코딩은 서블릿이 질의 매개변수를 **읽는 순간** 깨지는데, 그 읽기를 컨트롤러가 자기 안의 한
     * 자리로 모아 [InvalidNoticeSearchException] 으로 옮긴다 — 매핑표가 그것을 400 으로 받는다.
     * 앞 판은 이 자리에서 원인 사슬을 뒤져 400 을 냈고, 그러려면 디코딩 실패 예외 계열을 이 층이
     * **이름으로** 들어야 했다(그 이름은 HTTP 층의 바깥 참조 허용 밖이다 — 전송 표면 게이트가
     * 실측으로 막았다). 읽는 자리에서 옮기면 그 이름이 필요 없고, 영향 범위도 그 경로 하나다.
     */
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
