package bidvector.adapters.notificationinbox

import bidvector.adapters.notice.beginReadOnlySearchTransaction
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.MarkReadOutcome
import bidvector.workflow.notificationinbox.NoticeDisplay
import bidvector.workflow.notificationinbox.NoticeGroupCounts
import bidvector.workflow.notificationinbox.NotificationDetail
import bidvector.workflow.notificationinbox.NotificationInboxItem
import bidvector.workflow.notificationinbox.NotificationInboxPage
import bidvector.workflow.notificationinbox.NotificationInboxStore
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationKind
import bidvector.workflow.notificationinbox.NotificationPageRequest
import bidvector.workflow.notificationinbox.notificationFactOf
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * 알림함 **읽기·읽음 처리**의 production 구현(M7/7C③).
 *
 * `JdbcNotificationInbox`(쓰기)와 **갈라 둔다**. 앞 판은 한 class 가 두 포트를 함께 구현했고
 * 「격리는 class 가 아니라 주입이 진다」고 적었다 — 그 문장은 참이지만 `typeShapeGate` 는 어댑터
 * class 하나가 구현하는 인터페이스를 **하나로** 못박는다(래칫, ADR 0007 §5). 그 래칫을 올리는
 * 것은 이 slice 의 범위가 아니라 **구조를 갈랐다**. 가르고 보니 포트 분리의 뜻과도 더 맞는다 —
 * 읽기 class 는 `record` 를 **갖고 있지도 않다**(주입으로만 막던 것이 타입으로도 막힌다).
 *
 * **목록은 읽기 전용 트랜잭션에서 돈다**(7C① 의 helper 재사용) — 그 안에서는 어떤 DML 도 DB 가
 * 거부하므로, 읽기 경로에 쓰기를 넣으려면 술어나 이름이 아니라 **트랜잭션 설정**을 바꿔야 한다.
 *
 * **`SQLException` 을 값으로 접지 않는다**(쓰기 포트와 다른 점) — 그 경로의 실패는 HTTP 층의
 * 오류 매핑이 500 으로 옮기는 것이 맞고, 「목록이 비었다」와 「목록을 못 읽었다」를 같은 값으로
 * 만들면 화면이 거짓을 보인다.
 */
class JdbcNotificationInboxStore(
    private val dataSource: DataSource,
) : NotificationInboxStore {
    override fun list(
        account: AccountId,
        request: NotificationPageRequest,
    ): NotificationInboxPage =
        dataSource.connection.use { connection ->
            connection.beginReadOnlySearchTransaction()
            try {
                val counts = connection.readCounts(account)
                NotificationInboxPage(
                    items = connection.readPage(account, request),
                    totalCount = if (request.unreadOnly) counts.unread else counts.totalAll,
                    unreadCount = counts.unread,
                )
            } finally {
                // 읽기만 한 트랜잭션이라 되돌릴 것이 없다 — 열어 둔 채 반납하지 않으려고 닫는다.
                connection.rollback()
            }
        }

    override fun markRead(
        account: AccountId,
        item: NotificationItemKey,
        at: Instant,
    ): MarkReadOutcome =
        dataSource.connection.use { connection ->
            connection.inTransaction {
                val marked =
                    prepareStatement(NotificationInboxSql.MARK_ITEM_READ).use { statement ->
                        statement.setTimestamp(1, Timestamp.from(at))
                        statement.setLong(2, account.value)
                        statement.setString(3, item.kind.name)
                        statement.setString(4, item.notice.number.value)
                        statement.setString(5, item.notice.round.value)
                        statement.executeUpdate()
                    }
                MarkReadOutcome(marked, readCounts(account).unread)
            }
        }

    override fun markNoticeRead(
        account: AccountId,
        notice: NoticeId,
        at: Instant,
    ): MarkReadOutcome =
        dataSource.connection.use { connection ->
            connection.inTransaction {
                val marked =
                    prepareStatement(NotificationInboxSql.MARK_NOTICE_READ).use { statement ->
                        statement.setTimestamp(1, Timestamp.from(at))
                        statement.setLong(2, account.value)
                        statement.setString(3, notice.number.value)
                        statement.setString(4, notice.round.value)
                        statement.executeUpdate()
                    }
                MarkReadOutcome(marked, readCounts(account).unread)
            }
        }

    private fun Connection.readPage(
        account: AccountId,
        request: NotificationPageRequest,
    ): List<NotificationInboxItem> =
        prepareStatement(NotificationInboxSql.SELECT_PAGE).use { statement ->
            var index = 1
            statement.setLong(index++, account.value)
            statement.setBoolean(index++, request.unreadOnly)
            statement.setInt(index++, request.size)
            statement.setLong(index, request.offset)
            statement.executeQuery().use { rows ->
                val items = mutableListOf<NotificationInboxItem>()
                while (rows.next()) items += rows.toItem()
                items
            }
        }

    private fun Connection.readCounts(account: AccountId): InboxCounts =
        prepareStatement(NotificationInboxSql.SELECT_COUNTS).use { statement ->
            statement.setLong(1, account.value)
            statement.executeQuery().use { rows ->
                rows.next()
                InboxCounts(totalAll = rows.getLong("total_all"), unread = rows.getLong("unread"))
            }
        }
}

private data class InboxCounts(
    val totalAll: Long,
    val unread: Long,
)

/**
 * 행 하나 → 항목. 저장된 `(종류, detail)` 짝은 **사실로 복원한다** — 두 값을 따로 들고 가면
 * 어긋난 짝이 응답까지 흘러간다(저장 층의 짝 CHECK 가 막는 상태이지만, 복원 자리에서 한 번 더
 * 닫는 것이 공짜다).
 *
 * 표시 칸은 **합쳐 온 공고 행이 있을 때만** 짓는다. 판정 기준이 `joined_notice_number` 인 것은
 * 제목이 그 표에서 이미 nullable 이라서다 — 제목으로 판정하면 「행이 없다」와 「제목 칸이
 * 비었다」가 같은 답을 낸다.
 */
private fun ResultSet.toItem(): NotificationInboxItem {
    val notice =
        NoticeId(
            number = NoticeNumber(getString("notice_number")),
            round = NoticeRound(getString("notice_round")),
        )
    return NotificationInboxItem(
        notice = notice,
        fact =
            notificationFactOf(
                kind = NotificationKind.valueOf(getString("kind")),
                detail = getString("detail_code")?.let(NotificationDetail::valueOf),
            ),
        occurredAt = getTimestamp("occurred_at").toInstant(),
        readAt = getTimestamp("read_at")?.toInstant(),
        group =
            NoticeGroupCounts(
                itemCount = getInt("group_item_count"),
                unreadCount = getInt("group_unread_count"),
            ),
        display = if (getString("joined_notice_number") == null) null else toNoticeDisplay(),
    )
}

private fun ResultSet.toNoticeDisplay(): NoticeDisplay =
    NoticeDisplay(
        title = getString("notice_title"),
        status = getString("notice_status"),
        deadlineAt = getTimestamp("deadline_at")?.toInstant(),
    )
