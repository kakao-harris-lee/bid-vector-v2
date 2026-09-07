package bidvector.adapters.persistence

import bidvector.procurement.NoticeId
import bidvector.procurement.ObservationKey
import bidvector.procurement.PersistOutcome
import bidvector.procurement.QualificationText
import bidvector.procurement.QualificationTextRepository
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

/** [QualificationTextRepository] JDBC 구현 — [JdbcOpeningResultRepository]와 같은 「최신 관측 우선」 패턴. */
class JdbcQualificationTextRepository(
    private val dataSource: DataSource,
) : QualificationTextRepository {
    override fun persist(
        text: QualificationText,
        observationKey: ObservationKey,
    ): PersistOutcome =
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.UPSERT_QUALIFICATION_TEXT).use { statement ->
                bindQualificationText(statement, text, observationKey)
                statement.executeQuery().use { it.toUpsertOutcome() }
            }
        }

    override fun find(id: NoticeId): QualificationText? =
        dataSource.connection.use { connection ->
            connection.queryNoticeScoped(Sql.SELECT_QUALIFICATION_TEXT, id) { rs -> rs.toQualificationText(id) }
        }
}

private fun bindQualificationText(
    statement: PreparedStatement,
    text: QualificationText,
    observationKey: ObservationKey,
) {
    var index = 1
    statement.setString(index++, text.noticeId.number.value)
    statement.setString(index++, text.noticeId.round.value)
    statement.setString(index++, text.rawText)
    statement.setTimestamp(index++, Timestamp.from(text.observedAt))
    statement.setString(index, observationKey.value)
}

private fun ResultSet.toQualificationText(id: NoticeId): QualificationText =
    QualificationText(id, getString("raw_text"), getTimestamp("observed_at").toInstant())
