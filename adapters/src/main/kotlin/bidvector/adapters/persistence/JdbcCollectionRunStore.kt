package bidvector.adapters.persistence

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.CollectionRunStore
import java.sql.Date
import java.sql.Timestamp
import java.sql.Types
import javax.sql.DataSource

/** [CollectionDropReason] → drop_reasons JSON 키(⑥) — sealed variant 이름 + payload를 문자열로 접는다. */
private fun CollectionDropReason.label(): String =
    when (this) {
        CollectionDropReason.CollectionMissingNoticeNumber -> "CollectionMissingNoticeNumber"
        CollectionDropReason.CollectionUnknownField -> "CollectionUnknownField"
        is CollectionDropReason.CollectionContractViolation -> "CollectionContractViolation:$axis"
        is CollectionDropReason.CollectionParseFailure -> "CollectionParseFailure:$kind"
    }

/**
 * [CollectionRunStore] JDBC 구현(⑤·⑥, D-3D-4) — 배치 마지막에 **별도 트랜잭션**으로 기록한다
 * (항목 트랜잭션과 분리). `drop_reasons`는 손으로 짠 flat JSON object로 직렬화한다
 * (Jackson 미도입, [ObservationPayloadCodec]과 같은 판단).
 */
class JdbcCollectionRunStore(
    private val dataSource: DataSource,
) : CollectionRunStore {
    override fun record(
        accounting: CollectionAccounting,
        meta: CollectionRunMeta,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.INSERT_COLLECTION_RUN).use { statement ->
                var index = 1
                statement.setDate(index++, Date.valueOf(meta.referenceDate.date))
                statement.setString(index++, meta.source.name)
                statement.setTimestamp(index++, Timestamp.from(meta.startedAt))
                statement.setTimestamp(index++, Timestamp.from(meta.finishedAt))
                statement.setInt(index++, accounting.received)
                statement.setInt(index++, accounting.normalized)
                statement.setInt(index++, accounting.duplicate)
                statement.setInt(index++, accounting.dropped)
                statement.setString(index++, encodeDropReasons(accounting.dropReasons))
                val sourceTotal = accounting.sourceTotal
                if (sourceTotal != null) {
                    statement.setInt(index, sourceTotal)
                } else {
                    statement.setNull(index, Types.INTEGER)
                }
                index++
                statement.setInt(index++, accounting.pagesFetched)
                statement.setBoolean(index++, accounting.truncated)
                statement.setInt(index++, accounting.unknownFields)
                statement.setString(index++, accounting.truncationCause?.let { it::class.simpleName })
                statement.setInt(index++, accounting.quotaExceeded)
                statement.setInt(index, accounting.backoffSkipped)
                statement.executeUpdate()
            }
        }
    }

    private fun encodeDropReasons(dropReasons: Map<CollectionDropReason, Int>): String =
        dropReasons.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (reason, count) ->
            "\"${reason.label()}\":$count"
        }
}
