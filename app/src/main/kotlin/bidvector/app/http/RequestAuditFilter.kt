package bidvector.app.http

import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.strategy.Clock
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.util.ContentCachingResponseWrapper
import java.time.Duration
import java.time.Instant

/**
 * 전 요청 audit(D-6A1-7, 운영자 결정 ③) — [OperatorCredentialFilter]보다 **바깥**에
 * 등록해(`BidVectorApplication`) 인증 실패·예외에서도 행이 생기게 한다(우회 (2)). 요청
 * correlation id의 **유일한 발급점**이기도 하다 — [CORRELATION_ID_ATTRIBUTE]에 심어
 * 필터 체인·[GlobalErrorHandler]가 공유한다.
 *
 * **D-6A1-17(fail-closed) — audit 쓰기가 성공해야 실제 응답을 내보낸다.** 컨트롤러 응답을
 * [ContentCachingResponseWrapper]([copyBodyToResponse]까지 실 응답에 닿지 않는다, Spring
 * 표준 클래스 재사용)로 버퍼링한다 — audit insert가 실패하면 그 버퍼를 **내보내지 않고**
 * 고정 500 [ErrorBody]로 실 응답을 직접 쓴다(원 조회 결과가 새지 않는다).
 *
 * **D-6A1-21 실측 결과가 이 설계의 전제다** — `spring.mvc.throw-exception-if-no-handler-found`
 * + `spring.web.resources.add-mappings=false`(`BidVectorApplication`)로 모든 실패 경로가
 * [GlobalErrorHandler]를 지나는 **하나의 REQUEST 디스패치**로 닫힌다(컨테이너의 별도 ERROR
 * 재디스패치가 없다 — `RequestAuditFilterDispatchTest`가 실측한다). 아래 `runCatching`은
 * 그래도 컨트롤러·디스패처를 완전히 벗어나는 예외(핸들러 해석 자체의 실패 등)에 대한
 * 최후 방어선이다.
 */
class RequestAuditFilter(
    private val clock: Clock,
    private val correlationIdFactory: CorrelationIdFactory,
    private val record: (ApiAuditRecord) -> Unit,
) : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        val correlationId = correlationIdFactory.newId().value
        httpRequest.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId)

        val start = clock.now()
        val wrapper = ContentCachingResponseWrapper(httpResponse)
        val dispatchOutcome =
            runCatching {
                chain.doFilter(httpRequest, wrapper)
                wrapper.status
            }

        val auditOutcome = recordAudit(httpRequest, dispatchOutcome, start, correlationId)
        respond(httpResponse, wrapper, dispatchOutcome, auditOutcome, correlationId)
    }

    /** audit은 성공·실패 어느 쪽에서도 시도한다(우회 (2)) — 이 함수는 그 시도의 결과만 낸다. */
    private fun recordAudit(
        httpRequest: HttpServletRequest,
        dispatchOutcome: Result<Int>,
        start: Instant,
        correlationId: String,
    ): Result<Unit> {
        val subject = httpRequest.getAttribute(AUDIT_SUBJECT_ATTRIBUTE) as? String ?: "unauthenticated"
        val statusForAudit = dispatchOutcome.getOrDefault(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        val durationMillis = Duration.between(start, clock.now()).toMillis()
        return runCatching {
            record(
                ApiAuditRecord(
                    occurredAt = start,
                    subject = subject,
                    method = httpRequest.method,
                    path = httpRequest.requestURI,
                    status = statusForAudit,
                    durationMillis = durationMillis,
                    correlationId = correlationId,
                ),
            )
        }
    }

    /** D-6A1-17 — audit이 실패하면 성공한 dispatch 결과라도 내보내지 않는다(fail-closed). */
    private fun respond(
        httpResponse: HttpServletResponse,
        wrapper: ContentCachingResponseWrapper,
        dispatchOutcome: Result<Int>,
        auditOutcome: Result<Unit>,
        correlationId: String,
    ) {
        when {
            dispatchOutcome.isFailure -> {
                writeDirectError(httpResponse, dispatchOutcome.exceptionOrNull()!!, correlationId)
            }

            auditOutcome.isFailure -> {
                writeDirectError(httpResponse, auditOutcome.exceptionOrNull()!!, correlationId)
            }

            else -> {
                wrapper.copyBodyToResponse()
            }
        }
    }

    /**
     * `wrapper`가 아니라 실 [response]에 직접 쓴다 — 버퍼는 아직 클라이언트에 나가지
     * 않았으므로(커밋 전) `reset()`으로 지우고 고정 오류로 대체한다. 이미 커밋됐다면
     * (대용량 응답이 컨테이너 버퍼를 채워 조기 flush된 경우) 되돌릴 수 없다 — 알려진
     * 제한(D-6A1-17 대가, 이 slice는 endpoint가 읽기 하나뿐이라 응답이 작아 실무에서
     * 일어나지 않는다).
     */
    private fun writeDirectError(
        response: HttpServletResponse,
        throwable: Throwable,
        correlationId: String,
    ) {
        if (response.isCommitted) return
        response.reset()
        response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(ErrorMapping.forThrowable(throwable, correlationId).toJson())
        response.writer.flush()
    }
}

/** 감사 행 하나 — [bidvector.adapters.audit.ApiAuditRow]로의 매핑은 조립(`BidVectorApplication`)이 진다. */
data class ApiAuditRecord(
    val occurredAt: Instant,
    val subject: String,
    val method: String,
    val path: String,
    val status: Int,
    val durationMillis: Long,
    val correlationId: String,
)
