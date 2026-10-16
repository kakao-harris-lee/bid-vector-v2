package bidvector.adapters.notice

import bidvector.adapters.persistence.reconstructNotice
import bidvector.adapters.persistence.toNoticeRow
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
 *
 * **쓰기 부재는 이름 게이트가 아니라 연결이 진다**(verifier r1 F-1) — 이 어댑터가 여는 것은
 * [beginReadOnlySearchTransaction] 의 **읽기 전용 트랜잭션**이라 그 안에서는 어떤 DML 도
 * DB 가 거부한다. 쓰기를 넣으려면 술어나 이름이 아니라 이 트랜잭션 설정을 바꿔야 하고,
 * 그것은 실 DB test 가 잡는다.
 */
class JdbcNoticeSearch(
    private val dataSource: DataSource,
) : NoticeSearchPort {
    override fun search(criteria: NoticeSearchCriteria): NoticeSearchPage {
        val statement = noticeSearchStatement(criteria)
        return dataSource.connection.use { connection ->
            connection.beginReadOnlySearchTransaction()
            try {
                val total = connection.countMatches(statement)
                if (total == 0L) {
                    NoticeSearchPage(emptyList(), 0L, connection.emptyReason())
                } else {
                    NoticeSearchPage(connection.readPage(statement, criteria), total, NoticeSearchState.MATCHED)
                }
            } finally {
                // 읽기만 한 트랜잭션이라 되돌릴 것이 없다 — 열어 둔 채 반납하지 않으려고 닫는다.
                connection.rollback()
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

/**
 * 검색이 쓰는 **읽기 전용 트랜잭션**을 연다(verifier r1 F-1 처방) — 이 연결에서 실행되는
 * `INSERT`·`UPDATE`·`DELETE` 는 **DB 가** `read-only transaction` 오류로 거부한다.
 *
 * `Connection.setReadOnly(true)` 가 아니라 **문장으로** 거는 이유: 드라이버의 `readOnlyMode`
 * 설정에 따라 autocommit 연결에서 아무 일도 하지 않을 수 있다. 문장은 그 설정과 무관하게
 * 이 트랜잭션에 걸린다. `autoCommit = false` 가 먼저여야 문장이 트랜잭션 **안**에 들어간다
 * (autocommit 에서는 자기 문장 하나로 끝나 다음 질의에 효력이 없다).
 *
 * 스냅숏 축은 열어 둔다 — 격리 수준은 기본값이라 세 질의가 한 순간을 보지 않는다(알려진
 * 제한, `REPEATABLE READ` 를 같은 문장에 더하면 닫힌다).
 */
internal fun Connection.beginReadOnlySearchTransaction() {
    autoCommit = false
    createStatement().use { statement -> statement.execute("SET TRANSACTION READ ONLY") }
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
