package bidvector.app.http

import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import bidvector.workflow.notificationinbox.NotificationItemKey

/**
 * HTTP test 조립의 알림함 쓰기 이중체(M7/7C③-b) — **중복 억제 키를 실제로 진다**.
 *
 * 늘 `Created` 를 내는 이중체로 두면 표면의 `RECORDED_NEW` / `RECORDED_UPDATED` 갈래를 가를 수
 * 없고, 그러면 「같은 공고를 다시 물으면 항목이 늘지 않는다」가 HTTP 층에서 미측정으로 남는다.
 * 키는 7C③ 의 PK 와 같은 축이다(계정 · 종류 · 공고) — 실 DB 의 upsert 는 `adapters` 쪽 test 가
 * 따로 잰다.
 *
 * [failing] 이면 저장 실패를 흉내 낸다 — 표면이 `NOT_RECORDED` + `INBOX_WRITE_FAILED` 를 내는지
 * 재는 자리다(그 갈래는 실 DB 에서 만들기 어렵다).
 */
class InMemoryNotificationInbox(
    private val failing: Boolean = false,
) : NotificationInboxPort {
    val records = mutableListOf<NotificationInboxRecord>()
    private val seen = mutableSetOf<Pair<Long, NotificationItemKey>>()

    override fun record(record: NotificationInboxRecord): NotificationInboxOutcome {
        if (failing) return NotificationInboxOutcome.Failed
        records += record
        val key = record.account.value to NotificationItemKey(record.fact.kind, record.notice)
        return if (seen.add(key)) NotificationInboxOutcome.Created else NotificationInboxOutcome.Updated
    }
}
