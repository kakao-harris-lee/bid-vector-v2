package bidvector.adapters.result

import bidvector.adapters.bidding.BidCaseRow
import bidvector.adapters.notice.beginReadOnlySearchTransaction
import bidvector.adapters.opening.toCanonicalFacts
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.bidding.SubmittedBidAmount
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.settlement.AwardWinnerTradeName
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.access.AccountId
import bidvector.workflow.opening.CanonicalOpeningFacts
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.result.AwardWinnerNamePort
import bidvector.workflow.result.BatchOpeningFactsPort
import bidvector.workflow.result.RecordedBidReadPort
import bidvector.workflow.result.RecordedRealBid
import bidvector.workflow.result.recordedRealBidOrNull
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import javax.sql.DataSource

/**
 * 결과 조회 어댑터 셋(M7/7C⑤-b) — 기존 행을 **읽기만** 한다(마이그레이션·인덱스·쓰기 0).
 *
 * 셋 다 7C① 이 세운 [beginReadOnlySearchTransaction] 의 읽기 전용 트랜잭션 안에서 돈다
 * (사본을 만들지 않는다 — 술어가 한 자리여야 변이도 한 자리에서 잡힌다). 그 안에서는 어떤
 * DML 도 DB 가 거부하고, 실 DB test 가 조회 전후의 **행 수 불변**과 함께 잰다.
 */
private inline fun <T> DataSource.readOnly(block: (Connection) -> T): T =
    connection.use { connection ->
        connection.beginReadOnlySearchTransaction()
        try {
            block(connection)
        } finally {
            // 읽기만 한 트랜잭션이라 되돌릴 것이 없다 — 열어 둔 채 반납하지 않으려고 닫는다.
            connection.rollback()
        }
    }

/**
 * 투찰 기록이 있는 실투찰 건 목록 — **계정 하나의 것만** 돌려준다.
 *
 * 소유는 질의가 두 겹으로 지고(`bid_record.account_id` 와 조인 조건), 종류는 질의가 거른 뒤
 * [recordedRealBidOrNull] 이 한 번 더 거른다 — 필터가 틀려도 페이퍼는 **값이 서지 않는다**.
 */
class JdbcRecordedBidView(
    private val dataSource: DataSource,
) : RecordedBidReadPort {
    override fun recordedBids(account: AccountId): List<RecordedRealBid> =
        dataSource.readOnly { connection -> connection.selectRecordedBids(account) }

    private fun Connection.selectRecordedBids(account: AccountId): List<RecordedRealBid> =
        prepareStatement(MyBidResultSql.SELECT_RECORDED_BIDS).use { statement ->
            statement.setLong(1, account.value)
            statement.setString(2, MyBidResultSql.REAL_BID_KIND)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) rows.toRecordedRealBid()?.let(::add)
                }
            }
        }
}

private fun ResultSet.toRecordedRealBid(): RecordedRealBid? =
    recordedRealBidOrNull(
        kind = BidKind.valueOf(requireNotNull(getString("bid_kind"))),
        caseId = BidCaseId(getLong("case_id")),
        notice = NoticeKey(requireNotNull(getString("notice_number")), NoticeRound(requireNotNull(getString("notice_round")))),
        recordedState = BidStateTag.valueOf(requireNotNull(getString("state"))),
        amount = toSubmittedAmount(),
        submittedAt = getTimestamp("submitted_at").toInstant(),
        confirmedAt = getTimestamp("confirmed_at").toInstant(),
    )

/**
 * 투찰 금액 — 값만 싣지 않는다(R-BASIS-06). 출처 두 열의 복원은 쓰기 쪽과 **같은 함수**를 쓴다
 * ([BidCaseRow.toOrigin]) — 어휘 사본을 만들면 한쪽만 늘어나는 길이 생긴다.
 */
private fun ResultSet.toSubmittedAmount(): SubmittedBidAmount =
    SubmittedBidAmount(
        won = requireNotNull(getBigDecimal("amount_won")).toLong(),
        currency = Currency.valueOf(requireNotNull(getString("amount_currency"))),
        vatTreatment = VatTreatment.valueOf(requireNotNull(getString("amount_vat"))),
        origin = BidCaseRow.toOrigin(requireNotNull(getString("amount_provenance")), getString("chosen_option_ref")),
    )

/**
 * 공고 여럿의 개찰 사실 — 질의 최대 셋이 **한 연결**에서 돈다: 공고 행(존재·마감) → canonical →
 * (canonical 이 없는 공고가 남을 때만) 개찰 축 원문.
 *
 * 셋째 질의는 7C⑤-a 와 같은 이유로 비싸다(개찰 축 원문 훑기). 묶어 물으므로 **요청당 한 번**
 * 이고, canonical 이 전부 있으면 아예 돌지 않는다.
 *
 * 상태 판정을 하지 않는다 — 사실만 나르고 상태는 7C⑤-a 와 **같은 순수 함수**가 정한다.
 */
class JdbcBatchOpeningFactsView(
    private val dataSource: DataSource,
) : BatchOpeningFactsPort {
    override fun lookupAll(notices: Set<NoticeId>): Map<NoticeId, NoticeOpeningFacts> =
        if (notices.isEmpty()) emptyMap() else dataSource.readOnly { it.readAll(notices) }

    private fun Connection.readAll(notices: Set<NoticeId>): Map<NoticeId, NoticeOpeningFacts> {
        val deadlines = selectNoticeDeadlines(notices)
        val canonical = selectCanonical(notices)
        val awaitingPromotion = notices.filter { it in deadlines && canonical[it] == null }.toSet()
        val observedAxes = if (awaitingPromotion.isEmpty()) emptySet() else selectObservedAxes(awaitingPromotion)
        return notices.associateWith { id ->
            factsOf(id, deadlines, canonical, observedAxes)
        }
    }

    /** 키가 **있으면 공고 행이 있다** — 값이 `null` 인 것(마감 미정)과 다른 사실이다. */
    private fun Connection.selectNoticeDeadlines(notices: Set<NoticeId>): Map<NoticeId, Instant?> =
        prepareStatement(MyBidResultSql.SELECT_NOTICES).use { statement ->
            statement.bindNumbers(notices)
            statement.executeQuery().use { rows ->
                val wanted = notices.byCanonicalKey()
                buildMap {
                    while (rows.next()) {
                        rows.canonicalId(wanted)?.let { put(it, rows.getTimestamp("deadline_at")?.toInstant()) }
                    }
                }
            }
        }

    private fun Connection.selectCanonical(notices: Set<NoticeId>): Map<NoticeId, CanonicalOpeningFacts> =
        prepareStatement(MyBidResultSql.SELECT_CANONICAL).use { statement ->
            statement.bindNumbers(notices)
            statement.executeQuery().use { rows ->
                val wanted = notices.byCanonicalKey()
                buildMap {
                    while (rows.next()) rows.canonicalId(wanted)?.let { put(it, rows.toCanonicalFacts()) }
                }
            }
        }

    /**
     * 개찰 축 원문이 있는 공고들 — 번호와 차수의 대조를 **Kotlin 에서** 한다(COL-05). 원문의
     * 번호는 온 그대로라 SQL 등식으로 맞대면 대소문자·공백이 든 번호에서 조용히 빗나간다.
     */
    private fun Connection.selectObservedAxes(notices: Set<NoticeId>): Set<NoticeId> =
        prepareStatement(MyBidResultSql.SELECT_OPENING_AXIS_RAW_KEYS).use { statement ->
            val endpoints = MyBidResultSql.OPENING_RESULT_ENDPOINTS.map { it.name }.toTypedArray()
            statement.setArray(1, createArrayOf("text", endpoints))
            statement.executeQuery().use { rows ->
                val wanted = notices.byCanonicalKey()
                buildSet {
                    while (rows.next()) rows.foldedId(wanted)?.let(::add)
                }
            }
        }
}

private fun factsOf(
    id: NoticeId,
    deadlines: Map<NoticeId, Instant?>,
    canonical: Map<NoticeId, CanonicalOpeningFacts>,
    observedAxes: Set<NoticeId>,
): NoticeOpeningFacts {
    if (!deadlines.containsKey(id)) return NoticeOpeningFacts.NoticeAbsent
    val found = canonical[id]
    return NoticeOpeningFacts.Present(
        deadlineAt = deadlines[id],
        canonical = found,
        openingAxisObserved = found == null && id in observedAxes,
    )
}

/**
 * 확정 낙찰자 상호 — 이 slice 가 그 열을 읽는 **유일한 자리**이고, 읽은 값은
 * [AwardWinnerTradeName] 밖으로 나가지 않는다(D-7C5B-9).
 *
 * 값이 없거나 공백뿐이면 [AwardWinnerTradeName.NotPublished] 다 — 「낙찰자가 아니다」가 아니라
 * 「상호를 모른다」이고, 그 구별이 접히면 미기록이 패찰이 된다(SET-01 의 3-state).
 */
class JdbcAwardWinnerNameView(
    private val dataSource: DataSource,
) : AwardWinnerNamePort {
    override fun winnerNames(notices: Set<NoticeId>): Map<NoticeId, AwardWinnerTradeName> =
        if (notices.isEmpty()) emptyMap() else dataSource.readOnly { it.selectWinnerNames(notices) }

    private fun Connection.selectWinnerNames(notices: Set<NoticeId>): Map<NoticeId, AwardWinnerTradeName> =
        prepareStatement(MyBidResultSql.SELECT_WINNER_NAMES).use { statement ->
            statement.bindNumbers(notices)
            statement.executeQuery().use { rows ->
                val wanted = notices.byCanonicalKey()
                buildMap {
                    while (rows.next()) {
                        rows.canonicalId(wanted)?.let { put(it, rows.winnerTradeName()) }
                    }
                }
            }
        }
}

private fun ResultSet.winnerTradeName(): AwardWinnerTradeName {
    val raw = getString("final_award_company_name")
    return if (raw.isNullOrBlank()) AwardWinnerTradeName.NotPublished else AwardWinnerTradeName.Published(raw)
}

/** (번호, 차수) → 공고. 돌려받은 행을 **요청한 공고에만** 맞춘다. */
private fun Set<NoticeId>.byCanonicalKey(): Map<Pair<String, String>, NoticeId> =
    associateBy { it.number.value to it.round.value }

/**
 * canonical 표의 행 → 요청한 공고. 그 표들의 번호는 값 객체로 쓰여 **이미 정규형**이라 그대로
 * 맞댄다(7C⑤-a 의 `WHERE notice_number = ?` 와 같은 전제).
 */
private fun ResultSet.canonicalId(wanted: Map<Pair<String, String>, NoticeId>): NoticeId? {
    val number = getString("notice_number") ?: return null
    val round = getString("notice_round") ?: return null
    return wanted[number to round]
}

/**
 * **원문** 행 → 요청한 공고. 번호를 도메인 규칙으로 접어 맞댄다(COL-05). 번호가 서지 않는 행은
 * 키를 갖지 못하고 그냥 지나간다 — 원문은 append-only 라 지울 수 없고, 무방비로 정규화하면
 * 그런 행 하나가 조회 전체를 500 으로 만든다.
 */
private fun ResultSet.foldedId(wanted: Map<Pair<String, String>, NoticeId>): NoticeId? {
    val number = getString("notice_number")?.let(NoticeNumber::ofOrNull)?.value ?: return null
    val round = getString("notice_round") ?: return null
    return wanted[number to round]
}

private fun java.sql.PreparedStatement.bindNumbers(notices: Set<NoticeId>) {
    setArray(1, connection.createArrayOf("text", notices.map { it.number.value }.toTypedArray()))
}
