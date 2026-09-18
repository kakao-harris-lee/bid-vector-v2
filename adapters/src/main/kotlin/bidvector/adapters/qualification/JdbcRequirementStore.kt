package bidvector.adapters.qualification

import bidvector.adapters.persistence.Sql
import bidvector.procurement.NoticeId
import bidvector.qualification.RequirementCollection
import bidvector.qualification.RequirementSourceField
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

/**
 * `notice_requirement`/`notice_requirement_row` 읽기·쓰기 진입점(D-6F5-4). [find]는
 * [StoredRequirementLicenseGate]가 판정 입력으로 쓴다 — 저장된 것이 없으면 값을 지어내지
 * 않고 `RequirementCollection.DataAbsent`를 그대로 낸다(D-6F5-2, [toRequirementCollection]).
 *
 * **[save]는 매번 헤더를 upsert하고 행을 통째로 교체한다**(delete-then-insert, UPDATE
 * 없음) — 부분 갱신을 두지 않는다(6F-1 `opening_rank_one` 교훈과 같은 방향: 축 전체를 한
 * 번에 갈아 끼운다). **쓰기는 6F-5-b가 소비한다** — 이 slice는 자리만 만든다(scope.md
 * in_scope 목록, D-6F5-1).
 */
class JdbcRequirementStore(
    private val dataSource: DataSource,
) {
    fun find(noticeId: NoticeId): RequirementCollection =
        dataSource.connection.use { connection ->
            val status = connection.selectStatus(noticeId)
            val records = if (status != null) connection.selectRows(noticeId) else emptyList()
            toRequirementCollection(status, records)
        }

    /**
     * `DataAbsent`는 헤더 행을 지운다(V13 `ON DELETE CASCADE`가 자식 행을 함께 지운다) — 그
     * 밖은 헤더를 upsert하고 행을 통째로 교체한다. [JdbcStrategyRepository][bidvector.adapters
     * .strategy.JdbcStrategyRepository]`.save`와 같은 관례로 `catch`를 두지 않는다 —
     * `connection.use { }`가 커밋되지 않은 트랜잭션을 닫으며 롤백한다(business control
     * flow에 exception을 쓰지 않는다, v2-지침서.md §5).
     */
    fun save(
        noticeId: NoticeId,
        collection: RequirementCollection,
    ) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            val stored = collection.toStoredForm()
            if (stored == null) {
                connection.deleteHeader(noticeId)
            } else {
                val (status, records) = stored
                connection.upsertHeader(noticeId, status)
                connection.deleteRows(noticeId)
                connection.insertRows(noticeId, records)
            }
            connection.commit()
        }
    }

    private fun Connection.selectStatus(noticeId: NoticeId): RequirementCollectionStatus? =
        prepareStatement(Sql.SELECT_REQUIREMENT_STATUS).use { statement ->
            statement.bindNoticeId(1, noticeId)
            statement.executeQuery().use { rs ->
                if (rs.next()) RequirementCollectionStatus.valueOf(rs.getString("status")) else null
            }
        }

    private fun Connection.selectRows(noticeId: NoticeId): List<RequirementRowRecord> =
        prepareStatement(Sql.SELECT_REQUIREMENT_ROWS).use { statement ->
            statement.bindNoticeId(1, noticeId)
            statement.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.toRequirementRowRecord() else null }.toList()
            }
        }

    private fun Connection.upsertHeader(
        noticeId: NoticeId,
        status: RequirementCollectionStatus,
    ) {
        prepareStatement(Sql.UPSERT_REQUIREMENT_HEADER).use { statement ->
            statement.bindNoticeId(1, noticeId)
            statement.setString(3, status.name)
            statement.executeUpdate()
        }
    }

    private fun Connection.deleteHeader(noticeId: NoticeId) {
        prepareStatement(Sql.DELETE_REQUIREMENT_HEADER).use { statement ->
            statement.bindNoticeId(1, noticeId)
            statement.executeUpdate()
        }
    }

    private fun Connection.deleteRows(noticeId: NoticeId) {
        prepareStatement(Sql.DELETE_REQUIREMENT_ROWS).use { statement ->
            statement.bindNoticeId(1, noticeId)
            statement.executeUpdate()
        }
    }

    private fun Connection.insertRows(
        noticeId: NoticeId,
        records: List<RequirementRowRecord>,
    ) {
        if (records.isEmpty()) return
        prepareStatement(Sql.INSERT_REQUIREMENT_ROW).use { statement ->
            for (record in records) {
                statement.bindNoticeId(1, noticeId)
                statement.setString(3, record.serialNo)
                statement.setString(4, record.kind.name)
                statement.setNullableString(5, record.groupNo)
                statement.setNullableString(6, record.sourceField?.name)
                statement.setNullableTextArray(7, record.licenseNames)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}

private fun PreparedStatement.bindNoticeId(
    startIndex: Int,
    noticeId: NoticeId,
) {
    setString(startIndex, noticeId.number.value)
    setString(startIndex + 1, noticeId.round.value)
}

private fun PreparedStatement.setNullableString(
    index: Int,
    value: String?,
) {
    if (value != null) setString(index, value) else setNull(index, Types.VARCHAR)
}

/**
 * `license_names`는 PARSED에서만 실린다 — `NULL`(이 행이 UNPARSABLE)과 「빈 배열」을 구분
 * 해야 한다(빈 배열은 `RequirementRow.Parsed.licenseNames`의 init 불변식이 애초에 만들지
 * 않는다 — V13 CHECK가 저장 시점에도 같은 방어를 심층으로 둔다). `setTextArray`
 * (`adapters.persistence`)는 nullable을 다루지 않아 재사용하지 않는다 — 계약이 다르다.
 */
private fun PreparedStatement.setNullableTextArray(
    index: Int,
    values: List<String>?,
) {
    if (values != null) {
        setArray(index, connection.createArrayOf("text", values.toTypedArray()))
    } else {
        setNull(index, Types.ARRAY)
    }
}

private fun ResultSet.toRequirementRowRecord(): RequirementRowRecord =
    RequirementRowRecord(
        serialNo = getString("serial_no"),
        kind = RequirementRowKind.valueOf(getString("kind")),
        groupNo = getString("group_no"),
        sourceField = getString("source_field")?.let(RequirementSourceField::valueOf),
        licenseNames = getNullableTextArray("license_names"),
    )

/** [getTextList][bidvector.adapters.persistence.getTextList]의 nullable 판(NULL을 빈 목록으로 접지 않는다 — 위 계약과 같은 이유). */
private fun ResultSet.getNullableTextArray(column: String): List<String>? {
    val sqlArray = getArray(column) ?: return null

    @Suppress("UNCHECKED_CAST")
    val elements = sqlArray.array as Array<String?>
    val values = elements.filterNotNull()
    check(values.size == elements.size) {
        "$column 배열에 NULL 원소가 있다(전체 ${elements.size}개 중 ${elements.size - values.size}개 NULL) " +
            "— 조용히 거르면 요건 면허명 하나가 사라진 채로 판정에 들어간다"
    }
    return values
}
