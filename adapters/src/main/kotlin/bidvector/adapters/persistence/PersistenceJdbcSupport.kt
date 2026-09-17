package bidvector.adapters.persistence

import bidvector.procurement.NoticeId
import bidvector.procurement.PersistOutcome
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant

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

/**
 * nullable 바인딩 공통 헬퍼(M3/3E) — [JdbcNoticeRepository.bindDeadline]이 손으로 하던 것을
 * `Instant?`·`Int?`·`Boolean?` 셋으로 넓혀 공유한다(CPD 중복 제거, 이 파일 KDoc과 같은 원칙).
 * `setBigDecimal`·`setString`은 JDBC가 이미 `null` 참조를 그대로 받아 `NULL`로 바인딩하므로
 * 여기 없다 — 원시 타입(Timestamp 생성·int·boolean)만 명시적 `setNull` 분기가 필요하다.
 */
internal fun PreparedStatement.setNullableTimestamp(
    index: Int,
    value: Instant?,
) {
    if (value != null) setTimestamp(index, Timestamp.from(value)) else setNull(index, Types.TIMESTAMP)
}

internal fun PreparedStatement.setNullableInt(
    index: Int,
    value: Int?,
) {
    if (value != null) setInt(index, value) else setNull(index, Types.INTEGER)
}

internal fun PreparedStatement.setNullableBoolean(
    index: Int,
    value: Boolean?,
) {
    if (value != null) setBoolean(index, value) else setNull(index, Types.BOOLEAN)
}

/**
 * `TEXT[]` 바인딩(M6/6F-1) — [OpeningCompleteAxisCodec]의 `createArrayOf("integer", ...)`와
 * 같은 관용구를 문자열 목록으로 확장한다. 전략 감시 규칙 다섯 축(`focus_categories` 등)은
 * `StrategyDraft`가 항상 `List<String>`(빈 리스트가 「규칙 없음」)이라 `NULL`을 쓰지 않는다
 * — 이 헬퍼는 nullable을 다루지 않는다(호출부가 항상 빈 리스트 이상을 준다).
 */
internal fun PreparedStatement.setTextArray(
    index: Int,
    values: List<String>,
) {
    setArray(index, connection.createArrayOf("text", values.toTypedArray()))
}

/** [setTextArray]의 역함수 — `NULL`(이 열이 안 실린 관측)이면 빈 목록을 낸다. */
internal fun ResultSet.getTextList(column: String): List<String> {
    val sqlArray = getArray(column) ?: return emptyList()

    @Suppress("UNCHECKED_CAST")
    val elements = sqlArray.array as Array<String?>
    return elements.filterNotNull()
}
