package bidvector.app.http

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.notificationinbox.NotificationInboxItem
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationKind
import tools.jackson.databind.JsonNode

/** 전체 건수 — 페이지 길이와 다른 값이다(7C① 이 채택한 계약 형태를 잇는다). */
const val NOTIFICATION_TOTAL_COUNT_HEADER = "X-Total-Count"

/** 안읽음 전수 — 목록 필터와 무관한 계정 전체 값이다(화면 배지가 읽는다). */
const val NOTIFICATION_UNREAD_COUNT_HEADER = "X-Unread-Count"

/**
 * 읽음 처리 요청의 **명시** 형식 검증 — 본문을 원시 트리로 받아 여기서 판정한다
 * (`StrategyEditRequests.kt` 와 같은 규율 · 같은 사유).
 *
 * **왜 data class 로 받지 않는가**: Jackson 에 data class 를 주면 ⓐ 알려진 키 밖을 **조용히
 * 무시**하고 ⓑ `3` → `"3"` 같은 강제 변환을 한다. ⓐ 는 계약 문서가 선언한 400 을 거짓으로
 * 만들고(오탈자 `noticeRund` 가 성공으로 보인다), ⓑ 는 「보낸 것과 처리된 것이 다르다」다.
 * 이 체계가 이미 그 자리를 원시 트리로 닫아 두었으므로 같은 길을 따른다.
 *
 * **helper 를 공유하지 않고 같은 모양으로 둔다**: 기존 helper 들은
 * [InvalidEditRequestException] 을 던진다 — 그 예외 타입이 **어느 표면이 거부했는가**를 나르는
 * 진단이라(응답은 구별하지 않는다) 알림함 거부가 편집 어휘로 나가면 그 진단이 거짓이 된다.
 * 규칙은 같고 어휘만 다르다.
 */
private fun JsonNode.requireInboxObjectBody(): JsonNode {
    if (!isObject) rejectInbox("요청 본문이 JSON object 가 아니다")
    return this
}

/** 알려진 키 밖은 거부한다 — 조용히 무시하면 오탈자가 성공으로 보인다(D-6A2b-25 관례). */
private fun JsonNode.requireKnownInboxKeys(allowed: Set<String>): JsonNode {
    val unknown = propertyNames().filterNot { it in allowed }.sorted()
    if (unknown.isNotEmpty()) rejectInbox("알 수 없는 본문 키: $unknown")
    return this
}

/**
 * 문자열 칸 하나 — 없음·JSON `null`·비문자열·공백을 각각 거부한다(강제 변환 0).
 *
 * `null` 을 **부재와 같이** 다루는 것은 기존 판독기의 규율이다(`"kind": null` 을 「안 보냄」과
 * 다르게 다루면 호출자가 둘을 구별할 이유가 없는데 진단만 갈린다).
 */
private fun JsonNode.requiredInboxText(key: String): String {
    val node = get(key)
    return when {
        node == null || node.isNull -> rejectInbox("$key 가 없다")
        !node.isString -> rejectInbox("$key 는 문자열이어야 한다")
        node.stringValue().isBlank() -> rejectInbox("$key 는 빈 문자열일 수 없다")
        else -> node.stringValue()
    }
}

/**
 * 거부를 **한 자리에서** 던진다. 판독기마다 `throw` 를 흩어 놓으면 갈래가 늘수록 그 수가
 * 함께 늘고(detekt `ThrowsCount`), 무엇보다 「이 표면이 거부하는 방법」이 여러 자리로 흩어진다.
 * `Nothing` 을 돌려주므로 `when` 의 각 갈래가 값 자리에 그대로 설 수 있다 — 갈래를 더해도
 * 던지는 자리는 하나다.
 */
private fun rejectInbox(reason: String): Nothing = throw InvalidNotificationRequestException(reason)

/**
 * 공고 쌍을 값으로 읽는다. 형식을 어긴 입력은 **도메인 타입의 생성자가 던지므로** 그것을
 * 이 표면의 거부로 옮긴다 — 도메인 규칙(차수는 제로패딩 세 자리 · 번호는 비어 있지 않음)을
 * 이 층에서 다시 적지 않는다(두 벌이 되면 갈린다).
 */
private fun JsonNode.noticeId(): NoticeId {
    val number = requiredInboxText("noticeNumber")
    val round = requiredInboxText("noticeRound")
    return runCatching { NoticeId(NoticeNumber.of(number), NoticeRound.of(round)) }
        .getOrElse { failure ->
            throw InvalidNotificationRequestException("공고 키 형식이 아니다").apply { initCause(failure) }
        }
}

/** 종류 어휘 밖의 문자열은 거부다 — `valueOf` 의 예외를 쓰지 않고 어휘 전수에서 찾는다. */
private fun JsonNode.notificationKind(): NotificationKind {
    val token = requiredInboxText("kind")
    return NotificationKind.entries.firstOrNull { it.name == token }
        ?: rejectInbox("알 수 없는 kind 토큰: $token")
}

/** 읽음 처리(단건) — 신원은 「종류 + 공고 쌍」이다. 계정·시각 칸은 **허용 키에 없다**. */
fun JsonNode.parseNotificationReadTarget(): NotificationItemKey {
    val body = requireInboxObjectBody().requireKnownInboxKeys(ITEM_TARGET_KEYS)
    return NotificationItemKey(body.notificationKind(), body.noticeId())
}

/** 읽음 처리(공고 묶음 전체) — 종류 축만 없는 같은 모양이다. */
fun JsonNode.parseNoticeReadTarget(): NoticeId {
    val body = requireInboxObjectBody().requireKnownInboxKeys(NOTICE_TARGET_KEYS)
    return body.noticeId()
}

/**
 * 허용 키 — **계정 칸도 시각 칸도 없다**. 소유 경계는 세션이 정하고 읽은 시각은 서버가
 * 정한다. 그 부재를 계약 test 가 어휘로도 잠근다.
 */
private val ITEM_TARGET_KEYS = setOf("kind", "noticeNumber", "noticeRound")
private val NOTICE_TARGET_KEYS = setOf("noticeNumber", "noticeRound")

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
