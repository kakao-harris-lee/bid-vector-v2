package bidvector.workflow.notificationinbox

import bidvector.workflow.notice.blankOrNull
import bidvector.workflow.notice.notInteger32
import bidvector.workflow.notice.parsedInt

/**
 * 원문 질의 → 페이지 요청. 거부 규칙은 **표**다([NOTIFICATION_PAGE_REJECTIONS]) — 분기를
 * 함수 본문에 쌓지 않고 첫 위반 하나만 낸다(어느 입력이 틀렸는지는 응답에 싣지 않으므로
 * 전부 모을 이유가 없다).
 *
 * **정수 파싱 원시 함수는 7C① 의 것을 그대로 쓴다**(`workflow.notice` 의 `internal` helper).
 * 같은 모듈이라 보이고, 같은 문제(「운영자가 보낸 문자열을 조용히 강제 변환하지 않는다」)에
 * 둘째 규칙을 세우면 두 표면의 「유효한 입력」이 갈린다 — 실제로 7C① 이 그 갈림을 한 번
 * 겪었다(`+5`·앞뒤 공백을 `toIntOrNull` 이 받아들여 계약 문서보다 구현이 넓었다).
 */
fun parseNotificationPageParameters(parameters: NotificationPageParameters): NotificationPageResult {
    val rejection = NOTIFICATION_PAGE_REJECTIONS.firstNotNullOfOrNull { rule -> rule(parameters) }
    if (rejection != null) return NotificationPageResult.Rejected(rejection)
    return NotificationPageResult.Accepted(
        NotificationPageRequest(
            page = parameters.page.parsedInt() ?: 0,
            size = parameters.size.parsedInt() ?: NotificationInboxLimits.DEFAULT_PAGE_SIZE,
            unreadOnly = parameters.unreadOnly.parsedBoolean() ?: false,
        ),
    )
}

/** 순서가 곧 우선순위다 — 형식 검사가 범위 검사보다 앞에 온다(뒤 규칙이 파싱된 값을 전제한다). */
private val NOTIFICATION_PAGE_REJECTIONS: List<(NotificationPageParameters) -> NotificationPageRejection?> =
    listOf(
        { p -> NotificationPageRejection.PAGE_NOT_INTEGER.takeIf { p.page.notInteger32() } },
        { p -> NotificationPageRejection.PAGE_NEGATIVE.takeIf { (p.page.parsedInt() ?: 0) < 0 } },
        { p -> NotificationPageRejection.SIZE_NOT_INTEGER.takeIf { p.size.notInteger32() } },
        { p -> NotificationPageRejection.SIZE_OUT_OF_RANGE.takeIf { sizeOutOfRange(p) } },
        { p -> NotificationPageRejection.UNREAD_ONLY_NOT_BOOLEAN.takeIf { p.unreadOnly.notBoolean() } },
    )

private fun sizeOutOfRange(parameters: NotificationPageParameters): Boolean {
    val size = parameters.size.parsedInt()
    return size != null && (size < 1 || size > NotificationInboxLimits.MAX_PAGE_SIZE)
}

/**
 * 받는 형태는 **`true`·`false` 두 리터럴뿐**이다(대소문자 무시). `toBoolean()`·
 * `Boolean.parseBoolean` 은 **알 수 없는 값을 조용히 `false` 로** 접어서, `unreadOnly=yes`
 * 라고 보낸 호출자가 「전체 목록」을 받고도 자기가 안읽음만 걸렀다고 믿는다. 그 조용한
 * 오답을 400 으로 바꾼다.
 */
private fun String?.parsedBoolean(): Boolean? =
    when (this?.trim()?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

private fun String?.notBoolean(): Boolean = !blankOrNull() && parsedBoolean() == null
