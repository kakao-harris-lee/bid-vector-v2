package bidvector.adapters.bidding

import bidvector.bidding.BidCaseId
import bidvector.bidding.BidStateTag
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.TimelineEntry
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

/**
 * 타임라인 행 읽기 · 쓰기 — 전 타임라인 · 마지막 한 줄 두 질의가 **같은 행 해석**을 쓴다(D-7B-5 C3-L8). 쓰기는 append-only 한
 * 줄 넣기뿐이다(갱신 · 삭제 문장이 없다 — V21 GRANT · 트리거가 같은 경계를 진다).
 */
internal object BidTimelineRows {
    fun read(
        connection: Connection,
        sql: String,
        account: AccountId,
        caseId: BidCaseId,
    ): List<TimelineEntry> =
        connection.prepareStatement(sql).use { statement ->
            var index = 1
            statement.setLong(index++, account.value)
            statement.setLong(index, caseId.value)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            TimelineEntry(
                                caseVersion = rs.getInt("case_version"),
                                fromState = rs.getString("from_state")?.let(BidStateTag::valueOf),
                                toState = BidStateTag.valueOf(rs.getString("to_state")),
                                command = rs.getString("command"),
                                actorKind = BidCaseRow.actorKind(rs.getString("actor_kind")),
                                actorDetail = rs.getString("actor_detail"),
                                reason = rs.getString("reason"),
                                retroactive = rs.getBoolean("retroactive"),
                                occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                            ),
                        )
                    }
                }
            }
        }

    @Suppress("LongParameterList")
    fun insert(
        connection: Connection,
        account: AccountId,
        caseId: BidCaseId,
        caseVersion: Int,
        fromState: String?,
        toState: String,
        command: String,
        actorKind: String,
        actorDetail: String?,
        reason: String?,
        retroactive: Boolean,
        occurredAt: Instant,
    ) {
        connection.prepareStatement(BiddingSql.INSERT_TRANSITION).use { statement ->
            var index = 1
            statement.setLong(index++, caseId.value)
            statement.setLong(index++, account.value)
            statement.setInt(index++, caseVersion)
            statement.setString(index++, fromState)
            statement.setString(index++, toState)
            statement.setString(index++, command)
            statement.setString(index++, actorKind)
            statement.setString(index++, actorDetail)
            statement.setString(index++, reason)
            statement.setBoolean(index++, retroactive)
            statement.setTimestamp(index, Timestamp.from(occurredAt))
            statement.executeUpdate()
        }
    }
}
