package bidvector.adapters.persistence

import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.notice.NoticeSearchCriteria
import bidvector.workflow.notice.NoticeSearchPage
import bidvector.workflow.notice.NoticeSearchPort
import bidvector.workflow.notice.NoticeSearchState
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * [NoticeSearchPort] 의 production 구현 — `notice` 표(V1)의 기존 행을 **읽기만** 한다
 * (마이그레이션·인덱스·쓰기 0). 운영자 입력은 전부 바인딩 매개변수로 들어가고 질의 문면은
 * 조건의 유무만 보고 조립된다([noticeSearchStatement]) — 문자열 조립 0.
 *
 * **도메인 타입을 직접 만들지 않는다**([JdbcCandidateSource] 와 같은 규율) — 복원은 기존
 * [reconstructNotice]·[toNoticeRow] 를 그대로 쓴다.
 *
 * 질의는 최대 셋이고 **한 커넥션**에서 돈다: 전체 건수 → (건수가 1 이상일 때만) 페이지 →
 * (건수가 0 일 때만) 「수집된 공고가 하나라도 있는가」. 셋째 질의가 빈 결과의 두 뜻을
 * 가르고, 비어 있지 않은 답에서는 돌지 않는다.
 */
class JdbcNoticeSearch(
    private val dataSource: DataSource,
) : NoticeSearchPort {
    override fun search(criteria: NoticeSearchCriteria): NoticeSearchPage {
        val statement = noticeSearchStatement(criteria)
        return dataSource.connection.use { connection ->
            val total = connection.countMatches(statement)
            if (total == 0L) {
                NoticeSearchPage(emptyList(), 0L, connection.emptyReason())
            } else {
                NoticeSearchPage(connection.readPage(statement, criteria), total, NoticeSearchState.MATCHED)
            }
        }
    }

    private fun Connection.countMatches(statement: NoticeSearchStatement): Long =
        prepareStatement(NoticeSearchSql.selectCount(statement.clause)).use { prepared ->
            prepared.bindAll(statement.bindings)
            prepared.executeQuery().use { rs -> if (rs.next()) rs.getLong("total") else 0L }
        }

    private fun Connection.readPage(
        statement: NoticeSearchStatement,
        criteria: NoticeSearchCriteria,
    ): List<Notice> =
        prepareStatement(NoticeSearchSql.selectPage(statement.clause)).use { prepared ->
            val next = prepared.bindAll(statement.bindings)
            prepared.bindOne(next, NoticeSearchBinding.Count(criteria.size.toLong()))
            prepared.bindOne(next + 1, NoticeSearchBinding.Count(criteria.offset))
            prepared.executeQuery().use { rs -> rs.readNotices() }
        }

    private fun Connection.emptyReason(): NoticeSearchState =
        prepareStatement(NoticeSearchSql.SELECT_ANY_COLLECTED).use { prepared ->
            val collected = prepared.executeQuery().use { rs -> rs.next() && rs.getBoolean("collected") }
            if (collected) NoticeSearchState.NO_MATCH else NoticeSearchState.NOTHING_COLLECTED
        }

    private fun ResultSet.readNotices(): List<Notice> =
        generateSequence { if (next()) rowId().reconstructNotice(toNoticeRow()) else null }.toList()

    private fun ResultSet.rowId(): NoticeId =
        NoticeId(NoticeNumber(getString("notice_number")), NoticeRound(getString("notice_round")))
}

/** 값 목록을 1번 자리부터 순서대로 바인딩하고 **다음 빈 자리**를 돌려준다. */
private fun PreparedStatement.bindAll(bindings: List<NoticeSearchBinding>): Int {
    bindings.forEachIndexed { offset, binding -> bindOne(offset + 1, binding) }
    return bindings.size + 1
}

/** 타입별 바인딩 — `when` 소진이라 값 종류가 늘면 컴파일이 이 자리를 막는다. */
private fun PreparedStatement.bindOne(
    index: Int,
    binding: NoticeSearchBinding,
) {
    when (binding) {
        is NoticeSearchBinding.Text -> setString(index, binding.value)
        is NoticeSearchBinding.Amount -> setLong(index, binding.won)
        is NoticeSearchBinding.Moment -> setTimestamp(index, Timestamp.from(binding.value))
        is NoticeSearchBinding.Count -> setLong(index, binding.value)
    }
}
