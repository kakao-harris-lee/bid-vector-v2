package bidvector.adapters.bidding

import bidvector.adapters.persistence.ConnectionSource
import bidvector.bidding.BidCase
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.TransitionEffect
import bidvector.bidding.tag
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.AppliedTransition
import bidvector.workflow.bidding.BidCaseStorePort
import bidvector.workflow.bidding.OpenCaseResult
import bidvector.workflow.bidding.TimelineEntry
import bidvector.workflow.bidding.TransitionWrite
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

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
    override fun open(
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
        at: Instant,
    ): OpenCaseResult =
        connections.withConnection { connection ->
            connection.inTransaction {
                val inserted = insertCase(connection, account, notice, kind, at)
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

    override fun load(
        account: AccountId,
        caseId: BidCaseId,
    ): BidCase? =
        connections.withConnection { connection ->
            connection.prepareStatement(BiddingSql.SELECT_CASE_BY_ID).use { statement ->
                statement.setLong(1, account.value)
                statement.setLong(2, caseId.value)
                statement.executeQuery().use { rs -> if (rs.next()) BidCaseRow.toCase(rs) else null }
            }
        }

    override fun list(
        account: AccountId,
        kind: BidKind,
        state: BidStateTag?,
    ): List<BidCase> =
        connections.withConnection { connection ->
            connection.prepareStatement(BiddingSql.SELECT_CASES).use { statement ->
                statement.setLong(1, account.value)
                statement.setString(2, kind.name)
                statement.setString(3, state?.name)
                statement.setString(4, state?.name)
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
                statement.setLong(1, account.value)
                statement.setLong(2, caseId.value)
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
                writeEffect(connection, account, applied)
                TransitionWrite.Applied
            }
        }

    private fun writeEffect(
        connection: Connection,
        account: AccountId,
        applied: AppliedTransition,
    ) {
        when (val effect = applied.effect) {
            TransitionEffect.None -> Unit
            is TransitionEffect.DraftWritten ->
                BidEffectWriter.insertDraft(connection, account, applied.caseId, effect.draft)

            is TransitionEffect.RecordWritten ->
                BidEffectWriter.insertRecord(connection, account, applied.caseId, effect.record)
        }
    }

    private fun insertCase(
        connection: Connection,
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
        at: Instant,
    ): BidCase? =
        connection.prepareStatement(BiddingSql.INSERT_CASE).use { statement ->
            statement.setLong(1, account.value)
            statement.setString(2, notice.noticeNumber)
            statement.setString(3, notice.noticeRound.value)
            statement.setString(4, kind.name)
            statement.setTimestamp(5, Timestamp.from(at))
            statement.executeQuery().use { rs -> if (rs.next()) BidCaseRow.toCase(rs) else null }
        }

    private fun selectById(
        connection: Connection,
        account: AccountId,
        caseId: BidCaseId,
    ): BidCase? =
        connection.prepareStatement(BiddingSql.SELECT_CASE_BY_ID).use { statement ->
            statement.setLong(1, account.value)
            statement.setLong(2, caseId.value)
            statement.executeQuery().use { rs -> if (rs.next()) BidCaseRow.toCase(rs) else null }
        }

    private fun selectByKey(
        connection: Connection,
        account: AccountId,
        notice: NoticeKey,
        kind: BidKind,
    ): BidCase? =
        connection.prepareStatement(BiddingSql.SELECT_CASE_BY_KEY).use { statement ->
            statement.setLong(1, account.value)
            statement.setString(2, notice.noticeNumber)
            statement.setString(3, notice.noticeRound.value)
            statement.setString(4, kind.name)
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
            // 통보 예정일 축은 이 slice 의 전이가 쓰지 않는다 — 값을 지어내지 않고 그대로 둔다.
            statement.setTimestamp(index++, null)
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

/** `Connection` 하나를 트랜잭션 경계로 쓴다 — 전이 셋(건·타임라인·부수 산출)이 함께 서거나 함께 없다. */
internal fun <T> Connection.inTransaction(block: () -> T): T {
    val previous = autoCommit
    autoCommit = false
    return try {
        val result = block()
        commit()
        result
    } catch (failure: Throwable) {
        rollback()
        throw failure
    } finally {
        autoCommit = previous
    }
}
