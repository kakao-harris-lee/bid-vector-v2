package bidvector.adapters.persistence

import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeRepository
import bidvector.procurement.ObservationKey
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RejectionReason
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import javax.sql.DataSource

/**
 * [NoticeRepository] JDBC 구현(⑤) — `persist` 하나가 항목 트랜잭션 하나다(raw는 이미
 * [JdbcRawObservationStore]가 별도 append했다 — 이 클래스는 canonical fold + audit만 진다).
 * Kotlin 쪽 write 규칙([mergeNoticeRow], `bidvector.procurement.mayOverwrite`)이 먼저 걸러
 * 정상 경로에서는 트리거가 거부할 write를 만들지 않는다 — 그래도 트리거가 거부하면(방어
 * 심층, 예: 이 클래스의 버그) [PSQLException]을 잡아 그 원문 메시지를 `rejected_write`에
 * 함께 기록하고 [PersistOutcome.Rejected]를 낸다.
 */
class JdbcNoticeRepository(
    private val dataSource: DataSource,
) : NoticeRepository {
    override fun persist(
        command: NoticeCollected,
        observationKey: ObservationKey,
    ): PersistOutcome {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            val existing =
                connection.queryNoticeScoped(Sql.SELECT_NOTICE_FOR_UPDATE, command.id) { rs -> rs.toNoticeRow() }
            val outcome =
                if (existing == null) {
                    insertNotice(connection, command, observationKey)
                } else {
                    updateNotice(connection, command.id, existing, command, observationKey)
                }
            connection.commit()
            return outcome
        }
    }

    override fun find(id: NoticeId): Notice? =
        dataSource.connection.use { connection ->
            connection.queryNoticeScoped(Sql.SELECT_NOTICE, id) { rs -> id.reconstructNotice(rs.toNoticeRow()) }
        }

    private fun insertNotice(
        connection: Connection,
        command: NoticeCollected,
        observationKey: ObservationKey,
    ): PersistOutcome {
        val row = command.toNoticeRow()
        connection.prepareStatement(Sql.INSERT_NOTICE).use { statement ->
            bindNoticeColumns(statement, command.id, row, observationKey, includeStatusAndId = true)
            statement.executeUpdate()
        }
        return PersistOutcome.Inserted
    }

    private fun updateNotice(
        connection: Connection,
        id: NoticeId,
        existing: NoticeRow,
        command: NoticeCollected,
        observationKey: ObservationKey,
    ): PersistOutcome {
        val merged = mergeNoticeRow(existing, command)
        if (merged == existing) return PersistOutcome.Unchanged
        return try {
            connection.prepareStatement(Sql.UPDATE_NOTICE).use { statement ->
                bindNoticeColumns(statement, id, merged, observationKey, includeStatusAndId = false)
                statement.executeUpdate()
            }
            PersistOutcome.Updated(existing.revision + 1)
        } catch (rejection: PSQLException) {
            connection.rollback()
            connection.autoCommit = false
            recordRejection(connection, id, observationKey, merged, rejection)
            connection.commit()
            PersistOutcome.Rejected(RejectionReason.NON_AUTHORITATIVE_OVERWRITE)
        }
    }

    private fun recordRejection(
        connection: Connection,
        id: NoticeId,
        observationKey: ObservationKey,
        attempted: NoticeRow,
        cause: PSQLException,
    ) {
        connection.prepareStatement(Sql.INSERT_REJECTED_WRITE).use { statement ->
            var index = 1
            statement.setString(index++, id.number.value)
            statement.setString(index++, id.round.value)
            statement.setString(index++, observationKey.value)
            statement.setString(index++, RejectionReason.NON_AUTHORITATIVE_OVERWRITE.name)
            statement.setString(index, ObservationPayloadCodec.encodeNoticeRow(attempted, cause.message))
            statement.executeUpdate()
        }
    }

    @Suppress("LongParameterList")
    private fun bindNoticeColumns(
        statement: PreparedStatement,
        id: NoticeId,
        row: NoticeRow,
        observationKey: ObservationKey,
        includeStatusAndId: Boolean,
    ) {
        var index = 1
        if (includeStatusAndId) {
            statement.setString(index++, id.number.value)
            statement.setString(index++, id.round.value)
            statement.setString(index++, row.status)
        }
        statement.setString(index++, row.businessCategoryCode)
        statement.setString(index++, row.businessCategoryLabel)
        statement.setBigDecimal(index++, row.baseAmountWon)
        statement.setString(index++, row.baseAmountCurrency)
        statement.setString(index++, row.baseAmountVat)
        statement.setString(index++, row.baseAmountProvenance)
        statement.setString(index++, row.baseAmountProvenanceDetail)
        statement.setBigDecimal(index++, row.estimatedAmountWon)
        statement.setString(index++, row.estimatedAmountCurrency)
        statement.setString(index++, row.estimatedAmountVat)
        statement.setString(index++, row.estimatedAmountProvenance)
        statement.setString(index++, row.estimatedAmountProvenanceDetail)
        statement.setString(index++, row.estimatedAmountSourceKey)
        statement.setBigDecimal(index++, row.allocatedBudgetWon)
        statement.setString(index++, row.allocatedBudgetProvenance)
        statement.setString(index++, row.allocatedBudgetProvenanceDetail)
        statement.setBigDecimal(index++, row.floorRateFraction)
        statement.setString(index++, row.floorRateOriginKind)
        statement.setString(index++, row.floorRateOriginDetail)
        index = bindDeadline(statement, index, row)
        statement.setString(index++, observationKey.value)
        if (!includeStatusAndId) {
            statement.setString(index++, id.number.value)
            statement.setString(index, id.round.value)
        }
    }

    private fun bindDeadline(
        statement: PreparedStatement,
        index: Int,
        row: NoticeRow,
    ): Int {
        val deadlineAt = row.deadlineAt
        if (deadlineAt != null) {
            statement.setTimestamp(index, Timestamp.from(deadlineAt))
        } else {
            statement.setNull(index, Types.TIMESTAMP)
        }
        return index + 1
    }
}
