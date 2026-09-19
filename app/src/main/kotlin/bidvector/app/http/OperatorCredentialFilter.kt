package bidvector.app.http

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 단일 운영자 자격증명 필터(운영자 결정 2026-09-16 ②, D-6A1-6) — 값은 환경변수 주입,
 * 기본값 없음(생성자가 빈 값을 거부한다 — 조립 시점에 이미 실패한다, `bidvector.persistence`
 * 관례와 같은 fail-fast). 실패는 401이고 사유를 나누지 않는다(없음/틀림을 구분하지 않는다
 * — 구분하면 자격증명 존재 자체를 흘린다).
 *
 * **우회 (1) — 이 필터는 경로 이름을 모른다.** 등록(`BidVectorApplication`)이 urlPatterns를
 * 모든 경로(와일드카드 하나)로 명시해 어떤 새 endpoint도 이 필터 밖에서 태어나지 않는다
 * (허용 목록이 아니라 기본 거부). **우회 (4)** — 비교는 [MessageDigest.isEqual](JDK가
 * 보장하는 상수 시간)만 쓴다, `==`·`String.equals`를 쓰지 않는다.
 *
 * 이름에 스캔 어휘를 쓰지 않는다(D-6A1-9, D-6A1-19가 모든 설정 키로 일반화) — `Credential`.
 */
class OperatorCredentialFilter(
    private val expectedCredential: String,
) : Filter {
    init {
        require(expectedCredential.isNotBlank()) { "운영자 자격증명은 빈 값일 수 없다" }
    }

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        val presented = httpRequest.getHeader(CREDENTIAL_HEADER)

        if (presented == null || !constantTimeEquals(presented, expectedCredential)) {
            httpRequest.setAttribute(AUDIT_SUBJECT_ATTRIBUTE, "unauthenticated")
            writeUnauthenticated(httpRequest, httpResponse)
            return
        }

        httpRequest.setAttribute(AUDIT_SUBJECT_ATTRIBUTE, "operator")
        chain.doFilter(request, response)
    }

    /**
     * DispatcherServlet 밖(Spring MVC 진입 전)이라 [GlobalErrorHandler]를 지나지 않는다 —
     * 이 필터 자신이 [ErrorBody] 형태로 직접 응답한다(`toJson`, D-6A1-9 스캔 어휘 회피와
     * 같은 파일이 유일한 오류 형태 생성 경로라는 불변식을 지킨다).
     */
    private fun writeUnauthenticated(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val correlationId = request.getAttribute(CORRELATION_ID_ATTRIBUTE) as? String ?: "unknown"
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(ErrorMapping.unauthenticated(correlationId).toJson())
    }

    companion object {
        const val CREDENTIAL_HEADER = "X-Operator-Credential"
    }
}

/** 우회 (4) — 길이가 같은 배열에 대해 데이터 값과 무관한 시간을 문서가 보장하는 JDK API. */
private fun constantTimeEquals(
    presented: String,
    expected: String,
): Boolean =
    MessageDigest.isEqual(
        presented.toByteArray(StandardCharsets.UTF_8),
        expected.toByteArray(StandardCharsets.UTF_8),
    )
