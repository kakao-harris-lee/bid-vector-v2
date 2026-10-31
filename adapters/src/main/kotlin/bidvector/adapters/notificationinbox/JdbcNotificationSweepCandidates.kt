package bidvector.adapters.notificationinbox

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.DataSourceConnectionSource
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.NotificationSweepCandidatePort
import bidvector.workflow.bidding.NotificationSweepCase
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * 훑기 후보의 production 구현(M7/7C③-b) — **읽기 하나뿐**이라 이 포트를 쥔 코드가 무엇도 바꿀 수
 * 없다(`OpeningResultFactsPort` 와 같은 규율).
 *
 * 판정을 여기서 하지 않는다 — 창은 KST 달력일로 세는 순수 함수(`sweepNotificationsFor`)의 일이고,
 * 이 클래스는 「무엇을 읽었는가」만 나른다. 그 경계 덕에 창 판정 전수가 Testcontainers 없이 돈다.
 */
class JdbcNotificationSweepCandidates(
    private val connections: ConnectionSource,
) : NotificationSweepCandidatePort {
    /** 조립이 쓰는 생성자 — `DataSource` 하나를 받는다(`ConnectionSource` 는 adapters 안의 타입이다). */
    constructor(dataSource: DataSource) : this(DataSourceConnectionSource(dataSource))

    override fun candidates(
        now: Instant,
        horizon: Instant,
    ): List<NotificationSweepCase> =
        connections.withConnection { connection ->
            connection.prepareStatement(NotificationSweepSql.SELECT_SWEEP_CANDIDATES).use { statement ->
                val from = Timestamp.from(now)
                val to = Timestamp.from(horizon)
                statement.setTimestamp(1, from)
                statement.setTimestamp(2, to)
                statement.setTimestamp(3, from)
                statement.setTimestamp(4, to)
                statement.setTimestamp(5, from)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(rows.toSweepCase())
                        }
                    }
                }
            }
        }
}

/**
 * 행 → 값. 상태 꼬리표는 `valueOf` 로 복원한다 — 어휘가 코드와 DB CHECK 양쪽에 있고 그 둘이
 * 갈렸다면 **불가능한 상태**라 조용히 기본값으로 접지 않는다(어댑터의 기존 관례).
 */
private fun ResultSet.toSweepCase(): NotificationSweepCase =
    NotificationSweepCase(
        account = AccountId(getLong("account_id")),
        notice = NoticeKey(getString("notice_number"), NoticeRound.of(getString("notice_round"))),
        state = BidStateTag.valueOf(getString("state")),
        deadlineAt = getTimestamp("effective_deadline_at")?.toInstant(),
        documentDueAt = getTimestamp("document_due_at")?.toInstant(),
        openedAt = getTimestamp("created_at").toInstant(),
        // 공고 행이 없으면 둘 다 `null` 이다(LEFT JOIN) — `getLong` 은 0 을 내므로 `wasNull` 로 가른다.
        noticeRevision = getLong("notice_revision").takeUnless { wasNull() },
        noticeUpdatedAt = getTimestamp("notice_updated_at")?.toInstant(),
    )
