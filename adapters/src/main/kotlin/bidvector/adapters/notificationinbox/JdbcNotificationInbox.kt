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
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPage
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import bidvector.workflow.notificationinbox.NotificationInboxStore
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationKind
import bidvector.workflow.notificationinbox.NotificationPageRequest
import bidvector.workflow.notificationinbox.notificationFactOf
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * 알림함의 production 구현(M7/7C③) — 쓰기 포트와 읽기 포트를 **한 클래스가 함께** 든다.
 *
 * **왜 한 클래스인가**: 두 포트는 같은 표 하나의 질의 다섯이고, 나누면 SQL 상수와 행 판독이
 * 두 자리로 갈린다. **격리는 클래스가 아니라 주입이 진다** — app 층은 이 타입을 이름으로 들지
 * 않고(`NotificationInboxWiring` 이 포트 타입으로 꽂는다), controller 는 쓰기 포트의 빈을 받을
 * 수 없다(`app.http.denied-types` + 주입 게이트). 그래서 「읽기 표면에서 쓰기 능력이 새는가」는
 * 클래스 경계가 아니라 그 게이트들이 답한다.
 *
 * **목록은 읽기 전용 트랜잭션에서 돈다**(7C① 의 `beginReadOnlySearchTransaction` 을 그대로
 * 재사용한다 — 같은 문제이고 둘째 구현을 둘 이유가 없다). 그 안에서는 어떤 DML 도 DB 가
 * 거부하므로, 읽기 경로에 쓰기를 넣으려면 술어나 이름이 아니라 **트랜잭션 설정**을 바꿔야 한다.
 *
 * **`SQLException` 을 값으로 접는 것은 쓰기 포트뿐이다**(`OutboxNotificationRequestPort` 선례).
 * 읽기·읽음 처리는 접지 않는다 — 그 경로의 실패는 HTTP 층의 오류 매핑이 500 으로 옮기는 것이
 * 맞고, 「목록이 비었다」와 「목록을 못 읽었다」를 같은 값으로 만들면 화면이 거짓을 보인다.
 */
class JdbcNotificationInbox(
    private val dataSource: DataSource,
) : NotificationInboxPort,
    NotificationInboxStore {
    override fun record(record: NotificationInboxRecord): NotificationInboxOutcome =
        try {
            dataSource.connection.use { connection ->
                connection.inTransaction { upsert(record) }
            }
        } catch (
            @Suppress("SwallowedException") failure: SQLException,
        ) {
            // 조용히 삼키지 않는다 — 값이 그 축이고, 좁은 예외 타입만 잡는다(선례와 같은 규율).
            NotificationInboxOutcome.Failed
        }

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

    /**
     * 한 문장 upsert. `RETURNING (xmax = 0)` 이 삽입과 갱신을 가르므로 **중복 억제가 작동한
     * 자리를 호출자가 관측**할 수 있다(행이 늘지 않았다는 사실이 값으로 나온다).
     */
    private fun Connection.upsert(record: NotificationInboxRecord): NotificationInboxOutcome {
        val occurredAt = Timestamp.from(record.occurredAt)
        return prepareStatement(NotificationInboxSql.UPSERT_ITEM).use { statement ->
            statement.setLong(1, record.account.value)
            statement.setString(2, record.fact.kind.name)
            statement.setString(3, record.notice.number.value)
            statement.setString(4, record.notice.round.value)
            statement.setString(5, record.fact.detail?.name)
            statement.setTimestamp(6, occurredAt)
            // `updated_at` 은 바인딩하지 않는다 — 문장이 `now()` 로 찍는다(SQL 주석의 사유).
            statement.executeQuery().use { rows ->
                rows.next()
                if (rows.getBoolean("inserted")) {
                    NotificationInboxOutcome.Created
                } else {
                    NotificationInboxOutcome.Updated
                }
            }
        }
    }

    private fun Connection.readPage(
        account: AccountId,
        request: NotificationPageRequest,
    ): List<NotificationInboxItem> =
        prepareStatement(NotificationInboxSql.SELECT_PAGE).use { statement ->
            statement.setLong(1, account.value)
            statement.setBoolean(2, request.unreadOnly)
            statement.setInt(3, request.size)
            statement.setLong(4, request.offset)
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
 * 쓰기 경로의 트랜잭션 — 한 문장이어도 명시적으로 연다. autocommit 에 기대면 「이 경로는
 * 트랜잭션이 아니다」가 암묵이 되고, 읽음 처리처럼 갱신과 계수를 **함께** 봐야 하는 자리에서
 * 그 암묵이 깨진다(계수가 남의 커밋을 섞어 읽는다).
 */
private fun <T> Connection.inTransaction(block: Connection.() -> T): T {
    autoCommit = false
    return try {
        val result = block()
        commit()
        result
    } catch (failure: Throwable) {
        rollback()
        throw failure
    }
}

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
