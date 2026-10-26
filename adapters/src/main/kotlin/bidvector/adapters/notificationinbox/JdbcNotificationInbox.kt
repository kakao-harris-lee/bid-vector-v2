package bidvector.adapters.notificationinbox

import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationInboxOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import java.sql.Connection
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
) : NotificationInboxPort {
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

    /**
     * 한 문장 upsert. `RETURNING (xmax = 0)` 이 삽입과 갱신을 가르므로 **중복 억제가 작동한
     * 자리를 호출자가 관측**할 수 있다(행이 늘지 않았다는 사실이 값으로 나온다).
     */
    private fun Connection.upsert(record: NotificationInboxRecord): NotificationInboxOutcome {
        val occurredAt = Timestamp.from(record.occurredAt)
        return prepareStatement(NotificationInboxSql.UPSERT_ITEM).use { statement ->
            var index = 1
            statement.setLong(index++, record.account.value)
            statement.setString(index++, record.fact.kind.name)
            statement.setString(index++, record.notice.number.value)
            statement.setString(index++, record.notice.round.value)
            statement.setString(index++, record.fact.detail?.name)
            statement.setTimestamp(index, occurredAt)
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

}

/**
 * 쓰기 경로의 트랜잭션 — 한 문장이어도 명시적으로 연다. autocommit 에 기대면 「이 경로는
 * 트랜잭션이 아니다」가 암묵이 되고, 읽음 처리처럼 갱신과 계수를 **함께** 봐야 하는 자리에서
 * 그 암묵이 깨진다(계수가 남의 커밋을 섞어 읽는다).
 */
internal fun <T> Connection.inTransaction(block: Connection.() -> T): T {
    autoCommit = false
    var committed = false
    try {
        val result = block()
        commit()
        committed = true
        return result
    } finally {
        // 잡아서 **다시 던지기만** 하던 `catch` 를 없앴다 — 그러면 넓은 예외를 잡는 자리가
        // 아예 생기지 않고, 되돌림은 「커밋하지 못한 모든 경로」에서 똑같이 돈다(예외든
        // 이른 return 이든). 삼킬 수 있는 자리가 구조상 없다.
        if (!committed) rollback()
    }
}

