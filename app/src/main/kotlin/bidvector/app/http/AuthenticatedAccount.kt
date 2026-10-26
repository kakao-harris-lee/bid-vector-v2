package bidvector.app.http

import bidvector.workflow.access.AccountId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * 요청 attribute 키 — 세션 인증이 낸 **typed 계정**(M7/7B 계약 D-7B-2).
 *
 * audit 주체 라벨([AUDIT_SUBJECT_ATTRIBUTE] 의 `account:<값>`)과 **다른 자리**다. 그 라벨은
 * 감사 행에 남기는 문자열이고, 소유 경계(R-2)는 문자열 왕복 위에 서지 않는다 — 라벨 규약이
 * 바뀌면(접두·구분자·숫자 표기) 파싱이 조용히 다른 계정을 내거나 `null` 을 낸다.
 *
 * 이 속성을 적는 곳은 [RequestAuthenticationFilter] 하나다: 판정 **전에** 지우고, 통과한 경로가
 * 계정을 냈을 때만(세션 경로) 다시 적는다. 자격 헤더 경로는 계정을 내지 않으므로 이 속성이
 * 없고, 계정 범위 경로([AccountScopedRoutes])는 그때 401 이다.
 */
const val AUTHENTICATED_ACCOUNT_ATTRIBUTE = "bidvector.http.account"

/**
 * 인증된 **계정** — 계정 범위 핸들러가 주체를 얻는 유일한 접근자다. 주체 라벨 문자열을 읽지
 * 않는다(구조 test `AccountCarrierStructureTest` 가 production 의 `account:` 파싱 0 을 잰다).
 */
internal fun HttpServletRequest.authenticatedAccount(): AccountId? =
    getAttribute(AUTHENTICATED_ACCOUNT_ATTRIBUTE) as? AccountId

/**
 * 계정 범위 경로의 **401 한 벌** — 계정 없는 인증(자격 헤더 경로)과 무인증을 **가르지 않는다**
 * (D-6A1-6: 사유를 나누면 「그 계정이 있다」가 샌다).
 *
 * 접근자 바로 옆에 있는 이유: [authenticatedAccount] 가 `null` 일 때의 답은 하나뿐인데 그 둘이
 * 떨어져 있으면 뒤 slice 가 각자의 401 을 짓는다. `BidCaseHttp` 에서 옮겨 왔다(M7/7C④ 2b — 거동
 * 무변경, 입찰함 호출부가 그대로 이 함수를 부른다).
 */
internal fun unauthenticated(request: HttpServletRequest): ResponseEntity<Any> =
    ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(ErrorMapping.unauthenticated(request.correlationIdOrUnknown()))
