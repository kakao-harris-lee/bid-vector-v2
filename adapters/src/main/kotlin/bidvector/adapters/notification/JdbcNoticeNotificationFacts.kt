package bidvector.adapters.notification

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.DataSourceConnectionSource
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * 공고 표시 사실의 **읽기 전용** 어댑터(D-6). 쓰기 문장이 없고 열도 다섯뿐이다 — 발송 축이 공고
 * 표에서 보는 것이 그 다섯이라는 사실을 질의 자신이 적는다.
 *
 * **relay 의 트랜잭션 경계를 공유하지 않는다.** 렌더링은 relay 의 T1·T2 **사이**에서 일어나므로
 * (`RelayOutboxNotifications` 가 `dispatch` 를 `inTransaction` 밖에서 부른다) 자기 연결을 열고 닫는
 * 쪽이 정직하다 — 남의 경계에 올라타면 그 경계가 없는 호출 순서에서 실행 시점에 터진다.
 */
class JdbcNoticeNotificationFacts(
    dataSource: DataSource,
) : NoticeNotificationFactsPort {
    private val connections: ConnectionSource = DataSourceConnectionSource(dataSource)

    override fun factsFor(reference: NoticeReference): NoticeNotificationFacts? =
        connections.withConnection { connection ->
            connection.prepareStatement(NoticeNotificationSql.SELECT_DISPLAY_FACTS).use { statement ->
                statement.setString(1, reference.number)
                statement.setString(2, reference.round)
                statement.executeQuery().use { rows -> if (rows.next()) factsOf(rows) else null }
            }
        }

    private fun factsOf(rows: ResultSet): NoticeNotificationFacts =
        NoticeNotificationFacts(
            title = rows.getString("notice_title"),
            demandAgencyName = rows.getString("demand_agency_name"),
            deadlineAt = rows.getTimestamp("deadline_at")?.toInstant(),
            baseAmountWon = rows.getBigDecimal("base_amount_won"),
            baseAmountCurrency = rows.getString("base_amount_currency"),
        )
}

/**
 * 발송 축이 공고 표에서 읽는 전부 — **다섯 열**이다. `SELECT *` 를 쓰지 않는 이유는 그러면 이 어댑터가
 * 무엇을 보는지가 질의에 적히지 않고, 열이 늘 때 조용히 함께 읽기 때문이다.
 */
internal object NoticeNotificationSql {
    const val SELECT_DISPLAY_FACTS = """
        SELECT notice_title, demand_agency_name, deadline_at, base_amount_won, base_amount_currency
        FROM notice
        WHERE notice_number = ? AND notice_round = ?
    """
}
