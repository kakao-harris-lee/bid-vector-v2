package bidvector.adapters.bidding

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.DataSourceConnectionSource
import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.tag
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.AppliedTransition
import bidvector.workflow.bidding.BidCaseStorePort
import bidvector.workflow.bidding.OpenCaseResult
import bidvector.workflow.bidding.TimelineEntry
import bidvector.workflow.bidding.TransitionWrite
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * 입찰 건 저장소(V21) — **모든 문장이 `account_id` 로 좁혀진다**([BiddingSql]).
 *
 * 전이는 **한 트랜잭션**이다: 건 갱신(조건부) → 타임라인 한 줄 → 부수 산출(초안 또는 기록).
 * 셋이 갈라지면 「상태는 바뀌었는데 타임라인에 없는」 전이가 생기고, 그것은 append-only
 * 타임라인이 막으려던 바로 그 구멍이다.
 *
 * 갱신 · 삭제 문장이 타임라인 · 초안 · 기록 셋에 **없다** — 권한(V21 GRANT)과 트리거
 * (`reject_mutation`)가 같은 경계를 두 겹 더 진다.
 */
class JdbcBidCaseStore(
    private val connections: ConnectionSource,
) : BidCaseStorePort {
    /** 조립이 쓰는 생성자 — `DataSource` 하나를 받는다(`ConnectionSource` 는 adapters 안의 타입이다). */
    constructor(dataSource: DataSource) : this(DataSourceConnectionSource(dataSource))

    override fun open(
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
        at: Instant,
        deadlineAt: Instant?,
    ): OpenCaseResult =
        connections.withConnection { connection ->
            connection.inTransaction {
                val inserted = insertCase(connection, account, notice, kind, at, deadlineAt)
                if (inserted != null) {
                    insertTransition(
                        connection = connection,
                        account = account,
                        caseId = inserted.id,
                        caseVersion = 0,
                        fromState = null,
                        toState = BidStateTag.WATCHING.name,
                        command = bidvector.bidding.OPEN_CASE_COMMAND,
                        actorKind = "USER",
                        actorDetail = null,
                        reason = null,
                        retroactive = false,
                        occurredAt = at,
                    )
                    OpenCaseResult.Opened(inserted)
                } else {
                    // `DO NOTHING` 이 0 행을 낸 경우 — 이미 그 (계정, 공고, 종류)의 건이 있다.
                    OpenCaseResult.AlreadyOpen(
                        requireNotNull(selectByKey(connection, account, notice, kind)) {
                            "삽입이 충돌했는데 기존 건을 찾지 못했다"
                        },
                    )
                }
            }
        }

    override fun settleCandidates(
        account: AccountId,
        kind: BidKind,
        judgedStates: Set<BidStateTag>,
        now: Instant,
    ): List<BidCase> =
        connections.withConnection { connection ->
            connection.prepareStatement(BiddingSql.SELECT_SETTLE_CANDIDATES).use { statement ->
                var index = 1
                statement.setLong(index++, account.value)
                statement.setString(index++, kind.name)
                val states = judgedStates.map { it.name }.toTypedArray()
                statement.setArray(index++, connection.createArrayOf("text", states))
                statement.setTimestamp(index++, Timestamp.from(now))
                statement.setTimestamp(index, Timestamp.from(now))
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(BidCaseRow.toCase(rs)) }
                }
            }
        }

    override fun load(
        account: AccountId,
        caseId: BidCaseId,
    ): BidCase? = connections.withConnection { connection -> selectById(connection, account, caseId) }

    override fun list(
        account: AccountId,
        kind: BidKind,
        state: BidStateTag?,
    ): List<BidCase> =
        connections.withConnection { connection ->
            connection.prepareStatement(BiddingSql.SELECT_CASES).use { statement ->
                var index = 1
                statement.setLong(index++, account.value)
                statement.setString(index++, kind.name)
                // 같은 값을 두 번 바인딩한다 — `?::text IS NULL OR state = ?::text` 의 두 자리다.
                statement.setString(index++, state?.name)
                statement.setString(index, state?.name)
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(BidCaseRow.toCase(rs)) }
                }
            }
        }

    override fun timeline(
        account: AccountId,
        caseId: BidCaseId,
    ): List<TimelineEntry> =
        connections.withConnection { connection ->
            connection.prepareStatement(BiddingSql.SELECT_TIMELINE).use { statement ->
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
        }

    override fun applyTransition(
        account: AccountId,
        applied: AppliedTransition,
    ): TransitionWrite =
        connections.withConnection { connection ->
            connection.inTransaction {
                if (selectById(connection, account, applied.caseId) == null) {
                    return@inTransaction TransitionWrite.CaseNotFound
                }
                val nextVersion = applied.expectedVersion + 1
                if (!updateCaseState(connection, account, applied, nextVersion)) {
                    return@inTransaction TransitionWrite.VersionConflict
                }
                insertTransition(
                    connection = connection,
                    account = account,
                    caseId = applied.caseId,
                    caseVersion = nextVersion,
                    fromState = applied.fromState.tag.name,
                    toState = applied.next.tag.name,
                    command = applied.command.name,
                    actorKind = applied.actorKind.name,
                    actorDetail = applied.actorDetail,
                    reason = applied.reason?.value,
                    retroactive = applied.retroactive,
                    occurredAt = applied.occurredAt,
                )
                BidEffectWriter.write(connection, account, applied)
                TransitionWrite.Applied
            }
        }

    @Suppress("LongParameterList")
    private fun insertCase(
        connection: Connection,
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
        at: Instant,
        deadlineAt: Instant?,
    ): BidCase? =
        connection.prepareStatement(BiddingSql.INSERT_CASE).use { statement ->
            var index = statement.bindCaseKey(account, notice, kind)
            statement.setTimestamp(index++, deadlineAt.toTimestampOrNull())
            statement.setTimestamp(index, Timestamp.from(at))
            statement.executeQuery().use { rs -> if (rs.next()) BidCaseRow.toCase(rs) else null }
        }

    private fun selectById(
        connection: Connection,
        account: AccountId,
        caseId: BidCaseId,
    ): BidCase? =
        connection.prepareStatement(BiddingSql.SELECT_CASE_BY_ID).use { statement ->
            var index = 1
            statement.setLong(index++, account.value)
            statement.setLong(index, caseId.value)
            statement.executeQuery().use { rs -> if (rs.next()) BidCaseRow.toCase(rs) else null }
        }

    private fun selectByKey(
        connection: Connection,
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
    ): BidCase? =
        connection.prepareStatement(BiddingSql.SELECT_CASE_BY_KEY).use { statement ->
            statement.bindCaseKey(account, notice, kind)
            statement.executeQuery().use { rs -> if (rs.next()) BidCaseRow.toCase(rs) else null }
        }

    private fun updateCaseState(
        connection: Connection,
        account: AccountId,
        applied: AppliedTransition,
        nextVersion: Int,
    ): Boolean {
        val next: BidState = applied.next
        return connection.prepareStatement(BiddingSql.UPDATE_CASE_STATE).use { statement ->
            var index = 1
            statement.setString(index++, next.tag.name)
            statement.setInt(index++, nextVersion)
            statement.setTimestamp(index++, BidCaseRow.remindAtOf(next).toTimestampOrNull())
            statement.setTimestamp(index++, BidCaseRow.openingAtOf(next).toTimestampOrNull())
            statement.setTimestamp(index++, BidCaseRow.documentDueAtOf(next).toTimestampOrNull())
            statement.setString(index++, BidCaseRow.skipReasonOf(next))
            statement.setTimestamp(index++, Timestamp.from(applied.occurredAt))
            statement.setLong(index++, account.value)
            statement.setLong(index++, applied.caseId.value)
            statement.setInt(index, applied.expectedVersion)
            statement.executeUpdate() == 1
        }
    }

    @Suppress("LongParameterList")
    private fun insertTransition(
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

/**
 * 건의 키 넷(계정 · 공고번호 · 차수 · 종류)을 **한 자리에서** 바인딩한다 — 두 문장
 * (`INSERT_CASE` · `SELECT_CASE_BY_KEY`)이 같은 앞부분을 쓰므로 순서가 갈리면 조용히
 * 엉뚱한 건을 집는다. 다음에 쓸 인덱스를 돌려준다.
 */
private fun PreparedStatement.bindCaseKey(
    account: AccountId,
    notice: NoticeKey,
    kind: BidKind,
): Int {
    var index = 1
    setLong(index++, account.value)
    setString(index++, notice.noticeNumber)
    setString(index++, notice.noticeRound.value)
    setString(index++, kind.name)
    return index
}

/**
 * `Connection` 하나를 트랜잭션 경계로 쓴다 — 전이 셋(건 · 타임라인 · 부수 산출)이 함께 서거나
 * 함께 없다.
 *
 * **`catch` 를 두지 않는다**(`TransactionBoundary` 와 같은 관례, detekt
 * `TooGenericExceptionCaught` · v2-지침서 §5 「business control flow 에 exception 을 쓰지
 * 않는다」) — [block] 이 어떤 예외로 실패하든 그대로 전파시키고, `finally` 가 **커밋 여부를
 * 플래그로 보고** 커밋되지 않았을 때만 롤백한다.
 */
internal fun <T> Connection.inTransaction(block: () -> T): T {
    val previous = autoCommit
    autoCommit = false
    var committed = false
    try {
        val result = block()
        commit()
        committed = true
        return result
    } finally {
        if (!committed) rollback()
        autoCommit = previous
    }
}
