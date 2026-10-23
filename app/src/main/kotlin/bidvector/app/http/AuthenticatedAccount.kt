package bidvector.app.http

import bidvector.workflow.access.AccountId
import jakarta.servlet.http.HttpServletRequest

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
