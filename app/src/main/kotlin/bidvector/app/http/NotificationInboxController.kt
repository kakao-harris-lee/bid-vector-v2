package bidvector.app.http

import bidvector.app.wiring.NotificationInboxQuery
import bidvector.workflow.notificationinbox.MarkReadOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPage
import bidvector.workflow.notificationinbox.NotificationPageParameters
import bidvector.workflow.notificationinbox.NotificationPageResult
import bidvector.workflow.notificationinbox.parseNotificationPageParameters
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * 계약이 든 질의 매개변수 이름 — 반복 거부의 판정 대상이다. 문서와의 등식은 계약 test 가 잰다.
 */
private val DECLARED_PARAMETERS = listOf("page", "size", "unreadOnly")

/**
 * 알림함 endpoint(M7/7C③ · NOTI-10) — **계정 전용**이다.
 *
 * 인증 경로 둘 중 계정을 내는 것은 세션뿐이므로(운영자 자격 헤더의 주체는 `operator` 이고 계정
 * 행이 없다) 이 표면은 자격 헤더로 오면 401 이다. 소유 경계(R-2)를 세우려면 `account.id` 가
 * 있어야 하고, 그 식별자 없이 「누구의 알림함인가」가 성립하지 않는다.
 *
 * **controller 는 쓰기 포트를 모른다.** 알림을 **기록**하는 포트는 app 조립의 어느 자리에도
 * 꽂히지 않고(아키텍처 게이트의 포트 호출 쌍 허용 목록에 그 쌍이 하나도 없다) 이 class 는
 * `app.wiring` 의 조회기 하나만 받는다. 그래서 「HTTP 로 알림을 위조한다」가 게이트를 바꾸지
 * 않고는 성립하지 않는다 — 이 문장이 규율이 아니라 조립의 성질이다.
 *
 * **계정은 본문에서 오지 않는다** — 7B 의 typed 접근자([authenticatedAccount])가 세션 인증이
 * 심은 요청 속성에서 읽는다. 요청이 자기 계정을 말할 수 있으면 소유 경계가 요청자의 주장에
 * 의존한다. 주체 라벨 **문자열을 파싱하지 않는다** — 라벨 규약이 바뀌면 파싱이 조용히 다른
 * 계정을 내거나 `null` 을 낸다(`AccountCarrierStructureTest` 가 그 부재를 구조로 잰다).
 */
@RestController
@RequestMapping(AccountScopedRoutes.NOTIFICATIONS)
class NotificationInboxController(
    private val notifications: NotificationInboxQuery,
) {
    @GetMapping
    fun list(request: HttpServletRequest): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val parameters = readDeclaredParameters(request)
        return when (val parsed = parseNotificationPageParameters(parameters)) {
            is NotificationPageResult.Rejected ->
                throw InvalidNotificationRequestException(parsed.reason.name)

            is NotificationPageResult.Accepted -> respond(notifications.list(account, parsed.request))
        }
    }

    /**
     * 읽음 처리(단건). 상태를 바꾸므로 세션 경로에서 CSRF 가 함께 요구된다.
     *
     * 본문을 **원시 트리로 받는다** — data class 로 받으면 알려진 키 밖을 조용히 무시하고
     * 강제 변환을 한다(사유는 `NotificationInboxRequests.kt`). 비JSON·빈 본문은 이 핸들러에
     * 닿기 전에 Jackson 이 던지고 매핑표가 400 으로 받는다(D-6A3-19).
     */
    @PostMapping("/read")
    fun markRead(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val target = body.parseNotificationReadTarget()
        return when (target) {
            is NotificationTargetResult.Item -> respond(notifications.markRead(account, target.key))

            // 이 파싱은 묶음을 내지 않는다 — 소진 `when` 을 닫기 위한 갈래이고, 파싱이 갈래를
            // 늘리면 컴파일이 이 자리를 막는다(조용히 무시되지 않는다).
            is NotificationTargetResult.Notice ->
                throw InvalidNotificationRequestException("단건 경로가 공고 묶음을 받았다")
        }
    }

    /** 읽음 처리(한 공고의 묶음 전체). 안읽음 항목만 바뀐다. */
    @PostMapping("/read-notice")
    fun markNoticeRead(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val target = body.parseNoticeReadTarget()
        return when (target) {
            is NotificationTargetResult.Notice ->
                respond(notifications.markNoticeRead(account, target.notice))

            is NotificationTargetResult.Item ->
                throw InvalidNotificationRequestException("묶음 경로가 단건을 받았다")
        }
    }

    /**
     * 계약이 든 이름들의 **원문 값 목록** — 이 요청의 질의를 읽는 **유일한 자리**다. 서블릿
     * 컨테이너가 질의 전체를 한 번에 해석하므로, 깨진 퍼센트 인코딩이 **어느 매개변수에 있든**
     * (계약 밖 이름이어도) 이 읽기가 깨진다 — 그 실패를 전역 폴백(500)이 아니라 같은 거부
     * 하나로 옮긴다(7C① 이 같은 자리에서 같은 이유로 하는 일이다).
     */
    private fun readDeclaredParameters(request: HttpServletRequest): NotificationPageParameters {
        val values =
            try {
                DECLARED_PARAMETERS.associateWith { name -> request.getParameterValues(name)?.toList().orEmpty() }
            } catch (failure: IllegalStateException) {
                throw InvalidNotificationRequestException("질의를 해석할 수 없다").apply { initCause(failure) }
            }
        val repeated = values.filterValues { it.size > 1 }.keys
        if (repeated.isNotEmpty()) {
            // 값 둘을 조용히 합치지 않는다 — 계약에 없는 결합이라 400 으로 끊는다(7C① 과 같은 규율).
            throw InvalidNotificationRequestException("같은 매개변수가 반복됐다: $repeated")
        }
        return NotificationPageParameters(
            page = values["page"]?.firstOrNull(),
            size = values["size"]?.firstOrNull(),
            unreadOnly = values["unreadOnly"]?.firstOrNull(),
        )
    }

    private fun respond(page: NotificationInboxPage): ResponseEntity<Any> =
        ResponseEntity
            .ok()
            .header(NOTIFICATION_TOTAL_COUNT_HEADER, page.totalCount.toString())
            .header(NOTIFICATION_UNREAD_COUNT_HEADER, page.unreadCount.toString())
            .body(page.items.map(NotificationItemResponse::from))

    private fun respond(outcome: MarkReadOutcome): ResponseEntity<Any> =
        ResponseEntity.ok(
            MarkNotificationReadResponse(
                markedCount = outcome.markedCount,
                unreadCount = outcome.unreadCount,
            ),
        )

    /**
     * 계정 주체가 없는 요청 — 운영자 자격 헤더 경로로 들어온 경우다. 7D 의 필터는 「인증됐는가」
     * 까지만 보므로 그 요청이 여기까지 온다.
     *
     * **본문 모양을 직접 만들지 않는다**: 401 의 형태는 `ErrorMapping` 한 자리가 정하고 이
     * 함수는 그것을 그대로 쓴다(계약 test 가 401 본문의 키 집합을 `ErrorBody` 와 완전 일치로
     * 잠근다 — 빈 본문으로 두면 그 등식이 깨진다). 사유를 나누지 않는 것도 같은 자리의 관례다.
     */
    private fun unauthenticated(request: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorMapping.unauthenticated(request.correlationIdOrUnknown()))
}
