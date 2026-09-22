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
 * (허용 목록이 아니라 기본 거부).
 *
 * **우회 (4)(D-6A1-43) — 비교는 [OperatorCredential.matches] 하나뿐이다.** 이 필터는 raw
 * `String`을 직접 비교하지 않는다 — 원시 문자열은 [OperatorCredential.wrap]으로 즉시 감싸고,
 * 그 뒤로는 상수 시간 비교 외의 어떤 비교 연산도 **타입이 제공하지 않는다**(자세한 근거는
 * [OperatorCredential] KDoc). 이전 판은 `MessageDigest.isEqual`을 직접 호출하는 규율을
 * `ConstantTimeComparisonStructureTest`(게이트)로만 지켰는데, 그 게이트는 이 slice 안에서
 * 세 번(파일 하나 → 이름 하나 → 패키지+허용 목록) 넓혀졌고 세 번 다 위치·이름을 바꾸는
 * 다음 라운드에 뚫렸다(verifier r3 MUT-A2~A4). **타입은 코드가 어디로 옮겨지든 따라온다**
 * — 게이트는 회귀 그물로 남긴다.
 *
 * 이름에 스캔 어휘를 쓰지 않는다(D-6A1-9, D-6A1-19가 모든 설정 키로 일반화) — `Credential`.
 */
class OperatorCredentialFilter(
    expectedCredential: String,
) : Filter {
    init {
        require(expectedCredential.isNotBlank()) { "운영자 자격증명은 빈 값일 수 없다" }
    }

    private val expected: OperatorCredential = OperatorCredential.wrap(expectedCredential)

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        val presented = httpRequest.getHeader(CREDENTIAL_HEADER)

        if (presented == null || !expected.matches(OperatorCredential.wrap(presented))) {
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

/**
 * 자격증명 값을 감싸는 전용 타입(D-6A1-43, 운영자 결정 2026-09-23 「타입으로 끝낸다」) —
 * **비교가 상수 시간인 유일한 경로**로 [matches] 하나만 노출한다.
 *
 * **왜 이 타입이 종점인가.** `ConstantTimeComparisonStructureTest`(이전 방어)는 *위치*·
 * *이름*에 건 술어였다 — 비교 코드가 어느 class·어느 패키지에 있는지를 본다. verifier
 * r3 가 그 술어를 세 축에서 동시에 열었다: 같은 파일 안에서 `==`→`Objects.equals`로 한
 * 글자만 바꿔도(MUT-A4), 이웃 패키지로 비교를 옮겨도(MUT-A3), 허용 목록의 simple name을
 * 하위 패키지에서 재사용해도(MUT-A2) 전건 `check`가 초록이었다 — **위치·이름 술어는
 * 코드를 옮기면 진다.** 타입은 코드가 어디에 있든 따라온다: 이 타입은 **빠른 비교를
 * 제공하지 않는다** — `equals`/`hashCode`/`toString`을 의도적으로 재정의하지 않는다
 * (data class로 만들면 컴파일러가 내용 기반 `equals`를 합성해 `Intrinsics.areEqual`을
 * 심고, 그것이 이 타입이 막으려는 바로 그 형태가 된다). 기본 `Any.equals`는 참조
 * 동일성이라 서로 다른 [wrap] 호출이 만든 두 인스턴스는 내용이 같아도 항상 `false`다
 * — `==`로 비교를 대체하려는 시도는 **작동하지 않는 방향으로만** 실패한다(항상 인증
 * 거부 — 시간에 따라 새는 방향이 아니다).
 *
 * **(2b) 값 획득 축 — 이 타입이 밖에 무엇을 허락하는가(실측).** 공개 표면은 `wrap(String)`
 * 과 `matches(OperatorCredential): Boolean` 둘뿐이다. 원시 바이트(`bytes`)를 꺼내는
 * accessor가 없어 감싼 뒤에는 원문을 되돌릴 방법이 없다. `toString()`은 재정의하지 않아
 * `Any.toString()`(`클래스명@해시코드`)을 그대로 쓰므로 **값을 포함하지 않는다** — 로그에
 * 실수로 찍혀도(`logger.info("credential={}", credential)`) 원문이 새지 않는다
 * (`OperatorCredentialTest`가 실측한다). `equals`/`hashCode`도 값을 비교·해시하지
 * 않으므로 컬렉션(`Set`·`Map` 키)에 넣어도 내용 기반 조회가 성립하지 않는다.
 */
class OperatorCredential private constructor(
    private val bytes: ByteArray,
) {
    /** 우회 (4) — 유일한 비교 경로. JDK가 상수 시간을 보장하는 [MessageDigest.isEqual]만 쓴다. */
    fun matches(other: OperatorCredential): Boolean = MessageDigest.isEqual(bytes, other.bytes)

    companion object {
        fun wrap(value: String): OperatorCredential = OperatorCredential(value.toByteArray(StandardCharsets.UTF_8))
    }
}
