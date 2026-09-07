package bidvector.adapters.persistence

import bidvector.procurement.NoticeId
import bidvector.procurement.PersistOutcome
import java.sql.Connection
import java.sql.ResultSet

/**
 * `NoticeId`(notice_number, notice_round) 키로 단건 조회하는 공통 형태 — [JdbcNoticeRepository]
 * (`find`·`selectForUpdate`)·[JdbcOpeningResultRepository]·[JdbcQualificationTextRepository]가
 * 공유한다(CPD 중복 제거, sizeGate와 같은 「중복 금지」 원칙).
 */
internal fun <T> Connection.queryNoticeScoped(
    sql: String,
    id: NoticeId,
    mapper: (ResultSet) -> T,
): T? =
    prepareStatement(sql).use { statement ->
        statement.setString(1, id.number.value)
        statement.setString(2, id.round.value)
        statement.executeQuery().use { rs -> if (rs.next()) mapper(rs) else null }
    }

/**
 * `ON CONFLICT ... WHERE ... RETURNING (xmax = 0) AS inserted, revision`의 공통 판독 — 「최신
 * 관측 우선」 upsert([JdbcOpeningResultRepository]·[JdbcQualificationTextRepository]가 공유,
 * Sql.kt KDoc의 PostgreSQL 관용구).
 */
internal fun ResultSet.toUpsertOutcome(): PersistOutcome =
    if (next()) {
        if (getBoolean("inserted")) PersistOutcome.Inserted else PersistOutcome.Updated(getLong("revision"))
    } else {
        // WHERE observed_at >= ... 이 거짓 — 더 늦은 관측이 이미 있다(멱등/과거 재전송).
        PersistOutcome.Unchanged
    }
