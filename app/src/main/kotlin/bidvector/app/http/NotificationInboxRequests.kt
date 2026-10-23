package bidvector.app.http

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.notificationinbox.NotificationInboxItem
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationKind

/** 전체 건수 — 페이지 길이와 다른 값이다(7C① 이 채택한 계약 형태를 잇는다). */
const val NOTIFICATION_TOTAL_COUNT_HEADER = "X-Total-Count"

/** 안읽음 전수 — 목록 필터와 무관한 계정 전체 값이다(화면 배지가 읽는다). */
const val NOTIFICATION_UNREAD_COUNT_HEADER = "X-Unread-Count"

/**
 * 읽음 처리(단건) 요청. **합성 id 가 없다** — 항목의 신원이 「종류 + 공고 쌍」이고 그 셋은
 * 호출자가 자기 계정의 목록에서 이미 본 값이다. 계정은 **본문에서 오지 않는다**(세션이 낸다).
 *
 * 알려진 키 셋 밖은 400 이다(D-6A2b-25 관례). 전부 `String?` 으로 받아 형식 오류가 프레임워크의
 * 타입 변환 실패(500 경로)로 새지 않게 한다 — 7C① 과 같은 규율.
 */
data class MarkNotificationReadRequest(
    val kind: String? = null,
    val noticeNumber: String? = null,
    val noticeRound: String? = null,
)

/** 읽음 처리(공고 묶음 전체) 요청 — 종류 축만 없는 같은 모양이다. */
data class MarkNoticeNotificationsReadRequest(
    val noticeNumber: String? = null,
    val noticeRound: String? = null,
)

/** 파싱 결과 — 거부를 삼키지 않도록 소진 `when` 으로 소비한다. */
sealed interface NotificationTargetResult {
    data class Item(
        val key: NotificationItemKey,
    ) : NotificationTargetResult

    data class Notice(
        val notice: NoticeId,
    ) : NotificationTargetResult

    data object Rejected : NotificationTargetResult
}

/**
 * 공고 쌍을 값으로 읽는다. 형식을 어긴 입력은 **도메인 타입의 생성자가 던지므로** 그것을
 * `null` 로 접어 거부로 옮긴다 — 도메인 규칙(차수는 제로패딩 세 자리 · 번호는 비어 있지 않음)을
 * 이 층에서 다시 적지 않는다(두 벌이 되면 갈린다).
 */
private fun noticeIdOrNull(
    number: String?,
    round: String?,
): NoticeId? {
    if (number.isNullOrBlank() || round.isNullOrBlank()) return null
    return runCatching { NoticeId(NoticeNumber.of(number), NoticeRound.of(round)) }.getOrNull()
}

/** 종류 어휘 밖의 문자열은 거부다 — `valueOf` 의 예외를 값으로 접는다. */
private fun kindOrNull(raw: String?): NotificationKind? =
    raw?.let { name -> NotificationKind.entries.firstOrNull { it.name == name } }

fun MarkNotificationReadRequest.parse(): NotificationTargetResult {
    val kind = kindOrNull(kind) ?: return NotificationTargetResult.Rejected
    val notice = noticeIdOrNull(noticeNumber, noticeRound) ?: return NotificationTargetResult.Rejected
    return NotificationTargetResult.Item(NotificationItemKey(kind, notice))
}

fun MarkNoticeNotificationsReadRequest.parse(): NotificationTargetResult {
    val notice = noticeIdOrNull(noticeNumber, noticeRound) ?: return NotificationTargetResult.Rejected
    return NotificationTargetResult.Notice(notice)
}

/**
 * 항목 하나의 응답 — **평탄하다**(D-6A1-20 ⓑ: 속성은 스칼라이거나 스칼라 배열). 그래서 「건별
 * 묶음」을 중첩으로 실을 수 없고, 항목마다 자기 묶음의 계수를 싣고 클라이언트가 공고 쌍으로
 * 묶는다.
 *
 * **[noticeKnown] 이 평탄화의 대가를 메운다**(D-7C3-1 ⑦ 검토 요청 항목). 도메인에서는 공고
 * 표시 칸이 **객체의 유무**로 두 경우를 가른다 — 「공고 행이 없다」와 「행은 있고 제목 칸이
 * 비었다」. 응답은 평탄해야 해서 그 객체가 nullable 스칼라 셋으로 납작해지고, 그러면 두 경우가
 * 같은 `null` 묶음이 된다. 이 boolean 이 그 구분을 명시로 들고 간다 — 「제목이 null 이면 행이
 * 없는 것」이라는 **추론을 클라이언트에 맡기지 않는다**(`status` 가 NOT NULL 이라 그 추론이
 * 오늘은 성립하지만, 그것은 우연이고 계약이 아니다).
 *
 * [noticeKnown] 이 `false` 인 이유 둘(**90일 파기** · **아직 수집되지 않음**)은 **가르지
 * 않는다** — 저장소에 묘비가 없어 가를 근거가 없다. 있는 척하지 않는다.
 */
data class NotificationItemResponse(
    val kind: String,
    val detail: String?,
    val noticeNumber: String,
    val noticeRound: String,
    val occurredAt: String,
    val read: Boolean,
    val readAt: String?,
    val noticeItemCount: Int,
    val noticeUnreadCount: Int,
    val noticeKnown: Boolean,
    val noticeTitle: String?,
    val noticeStatus: String?,
    val noticeDeadlineAt: String?,
) {
    companion object {
        fun from(item: NotificationInboxItem): NotificationItemResponse =
            NotificationItemResponse(
                kind = item.fact.kind.name,
                detail = item.fact.detail?.name,
                noticeNumber = item.notice.number.value,
                noticeRound = item.notice.round.value,
                occurredAt = item.occurredAt.toString(),
                read = item.read,
                readAt = item.readAt?.toString(),
                noticeItemCount = item.group.itemCount,
                noticeUnreadCount = item.group.unreadCount,
                noticeKnown = item.display != null,
                noticeTitle = item.display?.title,
                noticeStatus = item.display?.status,
                noticeDeadlineAt = item.display?.deadlineAt?.toString(),
            )
    }
}

/** 읽음 처리의 응답 — 화면이 배지를 다시 묻지 않게 처리 뒤 안읽음 전수를 함께 낸다. */
data class MarkNotificationReadResponse(
    val markedCount: Int,
    val unreadCount: Long,
)
