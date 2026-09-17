package bidvector.adapters.persistence

import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.EditSession
import bidvector.workflow.strategy.EditSessionConflictException
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditSessionSnapshot
import bidvector.workflow.strategy.OperatorId
import bidvector.workflow.strategy.toSnapshot
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * [EditSessionRepository] 실 구현(M4/4B 알려진 제한 ② 인계, D-6B1-7). `load`는 원시
 * [EditSessionSnapshot]만 반환한다 — `EditSession`은 만들지 않는다(D-6B1-6, 복원은
 * `workflow` 안 internal `restoreEditSession` 하나뿐). `save`는 이미 완성된
 * [EditSession]의 public 프로퍼티를 [EditSession.toSnapshot]으로 원시 값으로 내려
 * DB 컬럼에 쓴다 — 이 방향은 위조 위험이 없다(안전한 방향, D-6B1-7 KDoc).
 *
 * write 경로는 [Sql.UPSERT_EDIT_SESSION] 한 문뿐이다(우회 (3), 설계 검토 (1) "구성이지
 * 열거가 아니다") — `session_version`을 전제조건으로 검사해 0행이면
 * [EditSessionConflictException]으로 크게 실패한다(D-6B1-4, 조용한 덮어쓰기 금지).
 */
class JdbcEditSessionRepository(
    private val dataSource: DataSource,
) : EditSessionRepository {
    override fun load(id: EditSessionId): EditSessionSnapshot? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.SELECT_EDIT_SESSION).use { statement ->
                statement.setString(1, id.value)
                statement.executeQuery().use { rs -> if (rs.next()) rs.toSnapshot(id) else null }
            }
        }

    override fun save(session: EditSession) {
        val snapshot = session.toSnapshot()
        val affected =
            dataSource.connection.use { connection ->
                connection.prepareStatement(Sql.UPSERT_EDIT_SESSION).use { statement ->
                    bindEditSession(statement, snapshot)
                    statement.executeQuery().use { rs -> if (rs.next()) 1 else 0 }
                }
            }
        if (affected == 0) {
            throw EditSessionConflictException(session.id, expectedVersion = session.sessionVersion - 1)
        }
    }
}

private fun ResultSet.toSnapshot(id: EditSessionId): EditSessionSnapshot =
    EditSessionRow.toSnapshot(
        id = id,
        operator = Actor.Operator(OperatorId(getString("operator_id"))),
        stateKind = getString("state"),
        statePayload = getString("state_payload"),
        expiresAt = getTimestamp("expires_at").toInstant(),
        sessionVersion = getInt("session_version"),
        lastCommand = getString("last_command"),
    )

private fun bindEditSession(
    statement: PreparedStatement,
    snapshot: EditSessionSnapshot,
) {
    var index = 1
    statement.setString(index++, snapshot.id.value)
    statement.setString(index++, snapshot.operator.id.value)
    statement.setString(index++, snapshot.stateKind)
    statement.setString(index++, EditSessionRow.encodeStatePayload(snapshot))
    statement.setTimestamp(index++, Timestamp.from(snapshot.expiresAt))
    statement.setInt(index++, snapshot.sessionVersion)
    statement.setString(index, EditSessionRow.encodeLastCommand(snapshot.lastCommand))
}
