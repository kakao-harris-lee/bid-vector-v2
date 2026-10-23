package bidvector.adapters.bidding

import bidvector.bidding.BidCaseId
import bidvector.bidding.BidDraft
import bidvector.bidding.BidRecordEntry
import bidvector.sharedkernel.export
import bidvector.workflow.access.AccountId
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp

/**
 * 초안 · 기록의 INSERT — `JdbcBidCaseStore` 에서 떼어 둔다(`sizeGate` 500줄, v2-지침서 §5).
 *
 * **UPDATE · DELETE 문장이 없다.** 초안 수정과 기록 갱신은 `*_seq + 1` 의 새 행이고, 그것이
 * 「스냅샷 불변」의 본체다 — 같은 행을 고치는 경로가 코드에도 권한에도 트리거에도 없다.
 */
internal object BidEffectWriter {
    fun insertDraft(
        connection: Connection,
        account: AccountId,
        caseId: BidCaseId,
        draft: BidDraft,
    ) {
        val seq = nextSeq(connection, BiddingSql.NEXT_DRAFT_SEQ, account, caseId)
        val (provenance, chosenOption) = BidCaseRow.originCodes(draft.amount)
        val snapshot = draft.snapshot
        val base = snapshot.baseAmount?.export()
        connection.prepareStatement(BiddingSql.INSERT_DRAFT).use { statement ->
            var index = 1
            statement.setLong(index++, caseId.value)
            statement.setLong(index++, account.value)
            statement.setInt(index++, seq)
            statement.setLong(index++, draft.amount.won)
            statement.setString(index++, draft.amount.currency.name)
            statement.setString(index++, draft.amount.vatTreatment.name)
            statement.setString(index++, provenance)
            statement.setString(index++, chosenOption)
            statement.setTimestamp(index++, Timestamp.from(draft.presentedAt))
            statement.setLong(index++, snapshot.noticeRevisionNumber)
            statement.setTimestamp(index++, Timestamp.from(snapshot.takenAt))
            statement.setTimestamp(index++, snapshot.deadlineAt?.let(Timestamp::from))
            statement.setBigDecimal(index++, snapshot.floorRate?.rate?.fraction)
            statement.setString(index++, snapshot.floorRate?.origin?.let(::floorOriginKind))
            statement.setString(index++, snapshot.floorRate?.origin?.let(::floorOriginDetail))
            statement.setObject(index++, base?.won)
            statement.setString(index++, base?.currency?.name)
            statement.setString(index++, base?.vatTreatment?.name)
            statement.setString(index, base?.provenance?.let(::provenanceCode))
            statement.executeUpdate()
        }
    }

    fun insertRecord(
        connection: Connection,
        account: AccountId,
        caseId: BidCaseId,
        record: BidRecordEntry,
    ) {
        val seq = nextSeq(connection, BiddingSql.NEXT_RECORD_SEQ, account, caseId)
        val submission = record.submission
        val (provenance, chosenOption) = BidCaseRow.originCodes(submission.amount)
        connection.prepareStatement(BiddingSql.INSERT_RECORD).use { statement ->
            var index = 1
            statement.setLong(index++, caseId.value)
            statement.setLong(index++, account.value)
            statement.setInt(index++, seq)
            statement.setLong(index++, submission.amount.won)
            statement.setString(index++, submission.amount.currency.name)
            statement.setString(index++, submission.amount.vatTreatment.name)
            statement.setString(index++, provenance)
            statement.setString(index++, chosenOption)
            statement.setTimestamp(index++, Timestamp.from(submission.submittedAt))
            statement.setTimestamp(index++, Timestamp.from(record.confirmedAt))
            statement.setObject(index++, latestDraftId(connection, account, caseId))
            statement.setBoolean(index++, record.retroactive)
            statement.setArray(
                index++,
                connection.createArrayOf("int4", submission.drawNumbers.toTypedArray()),
            )
            statement.setString(index++, submission.evidence?.value)
            statement.setDate(index++, record.evidenceRetainUntil?.let(Date::valueOf))
            statement.setString(index, submission.memo?.value)
            statement.executeUpdate()
        }
    }

    private fun nextSeq(
        connection: Connection,
        sql: String,
        account: AccountId,
        caseId: BidCaseId,
    ): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, account.value)
            statement.setLong(2, caseId.value)
            statement.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    /** 기록이 기본값으로 삼은 초안 — 소급 확인이면 초안이 없을 수 있다(그러면 `null`). */
    private fun latestDraftId(
        connection: Connection,
        account: AccountId,
        caseId: BidCaseId,
    ): Long? =
        connection.prepareStatement(BiddingSql.LATEST_DRAFT_ID).use { statement ->
            statement.setLong(1, account.value)
            statement.setLong(2, caseId.value)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }

    /**
     * provenance → 저장 어휘. **반사(`it::class.simpleName`)를 쓰지 않는다** — 타입 이름을
     * 값으로 꺼내면 클래스 이름을 바꾸는 순간 저장된 값의 뜻이 조용히 달라지고, 그 경로는
     * 수집 아키텍처 게이트가 막는 자리이기도 하다(`collection.reflection.*`).
     */
    private fun provenanceCode(provenance: bidvector.sharedkernel.Provenance): String =
        when (provenance) {
            is bidvector.sharedkernel.Provenance.Published -> "Published"
            bidvector.sharedkernel.Provenance.DerivedFromOpening -> "DerivedFromOpening"
            is bidvector.sharedkernel.Provenance.FilledFromBudgetKey -> "FilledFromBudgetKey"
            bidvector.sharedkernel.Provenance.CopiedFromBaseAmount -> "CopiedFromBaseAmount"
            bidvector.sharedkernel.Provenance.OperatorDeclared -> "OperatorDeclared"
            bidvector.sharedkernel.Provenance.Undeclared -> "Undeclared"
        }

    private fun floorOriginKind(origin: bidvector.sharedkernel.FloorRateOrigin): String =
        when (origin) {
            is bidvector.sharedkernel.FloorRateOrigin.NoticeValue -> "NOTICE_VALUE"
            is bidvector.sharedkernel.FloorRateOrigin.StatutoryTable -> "STATUTORY_TABLE"
        }

    private fun floorOriginDetail(origin: bidvector.sharedkernel.FloorRateOrigin): String? =
        when (origin) {
            is bidvector.sharedkernel.FloorRateOrigin.NoticeValue -> origin.noticeRevision.value
            is bidvector.sharedkernel.FloorRateOrigin.StatutoryTable -> origin.effectiveFrom.toString()
        }
}
