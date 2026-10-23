package bidvector.adapters.opening

import bidvector.adapters.notice.beginReadOnlySearchTransaction
import bidvector.adapters.persistence.OpeningRankOneKind
import bidvector.adapters.persistence.ProvenanceCodec
import bidvector.procurement.NoticeId
import bidvector.procurement.ObservedBidAmount
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Rate
import bidvector.workflow.opening.CanonicalOpeningFacts
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OpeningResultFactsPort
import bidvector.workflow.opening.OpeningResultFigures
import bidvector.workflow.opening.ProvisionalRankOne
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import javax.sql.DataSource

/**
 * [OpeningResultFactsPort] 의 production 구현(M7/7C⑤-a) — 기존 행을 **읽기만** 한다
 * (마이그레이션·인덱스·쓰기 0).
 *
 * **쓰기 부재는 이름이 아니라 연결이 진다** — 7C① 이 세운 [beginReadOnlySearchTransaction] 의
 * 읽기 전용 트랜잭션을 그대로 쓴다(사본을 만들지 않는다: 술어가 한 자리여야 변이도 한 자리에서
 * 잡힌다). 그 안에서는 어떤 DML 도 DB 가 거부하고, 실 DB test 가 그것과 **무쓰기 지문**을 함께
 * 잰다.
 *
 * 질의는 최대 셋이고 **한 연결**에서 돈다: 공고 행(존재·마감) → canonical 개찰 결과 →
 * (canonical 이 없을 때만) 개찰 축 원문 존재. 셋째 질의가 「승격 전 ↔ 수집 전」을 가르고,
 * canonical 이 있으면 돌지 않는다(그 질의가 원문 표를 훑는 쪽이다 — 알려진 제한).
 *
 * **상태 판정을 하지 않는다** — 사실만 나르고 상태는 순수 함수가 정한다
 * (`resolveOpeningResultView`). 그래서 상태 전수 test 가 DB 없이 돈다.
 */
class JdbcOpeningResultView(
    private val dataSource: DataSource,
) : OpeningResultFactsPort {
    override fun lookup(id: NoticeId): NoticeOpeningFacts =
        dataSource.connection.use { connection ->
            connection.beginReadOnlySearchTransaction()
            try {
                connection.read(id)
            } finally {
                // 읽기만 한 트랜잭션이라 되돌릴 것이 없다 — 열어 둔 채 반납하지 않으려고 닫는다
                // (7C① 과 같은 자리·같은 이유).
                connection.rollback()
            }
        }

    private fun Connection.read(id: NoticeId): NoticeOpeningFacts {
        val notice = selectNotice(id) ?: return NoticeOpeningFacts.NoticeAbsent
        val canonical = selectCanonical(id)
        return NoticeOpeningFacts.Present(
            deadlineAt = notice.deadlineAt,
            canonical = canonical,
            openingAxisObserved = canonical == null && openingAxisObserved(id),
        )
    }

    /** 공고 행 하나 — 없으면 `null`(공고 부재). 마감은 `null` 일 수 있다(값 부재와 행 부재는 다르다). */
    private fun Connection.selectNotice(id: NoticeId): NoticeRow? =
        prepareStatement(OpeningResultViewSql.SELECT_NOTICE).use { statement ->
            statement.bindNotice(id)
            statement.executeQuery().use { rows ->
                if (rows.next()) NoticeRow(rows.getTimestamp("deadline_at")?.toInstant()) else null
            }
        }

    private fun Connection.selectCanonical(id: NoticeId): CanonicalOpeningFacts? =
        prepareStatement(OpeningResultViewSql.SELECT_RESULT).use { statement ->
            statement.bindNotice(id)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toCanonicalFacts() else null }
        }

    /** 개찰 결과 축 원문이 이 공고에 있는가 — 등식 둘 + 축 둘([OpeningResultViewSql] KDoc). */
    private fun Connection.openingAxisObserved(id: NoticeId): Boolean =
        prepareStatement(OpeningResultViewSql.SELECT_OPENING_AXIS_EXISTS).use { statement ->
            val endpoints = OpeningResultViewSql.OPENING_RESULT_ENDPOINTS.map { it.name }.toTypedArray()
            statement.setArray(1, createArrayOf("text", endpoints))
            statement.setString(2, id.number.value)
            statement.setString(3, id.round.value)
            statement.executeQuery().use { rows -> rows.next() && rows.getBoolean("observed") }
        }
}

private class NoticeRow(
    val deadlineAt: Instant?,
)

private fun PreparedStatement.bindNotice(id: NoticeId) {
    setString(1, id.number.value)
    setString(2, id.round.value)
}

private fun ResultSet.toCanonicalFacts(): CanonicalOpeningFacts =
    CanonicalOpeningFacts(
        progressLabel = getString("progress_division"),
        figures =
            OpeningResultFigures(
                participantCount = getInt("participant_count").takeUnless { wasNull() },
                actualOpeningAt = getTimestamp("actual_opening_at")?.toInstant(),
                confirmedAward = toConfirmedAward(),
                provisionalRankOne = toProvisionalRankOne(),
                observedAt = getTimestamp("observed_at").toInstant(),
            ),
    )

/**
 * 확정 축 — 최종낙찰금액 네 열에서만 읽는다. 잠정 축 열을 섞지 않는다(우회 ③): 두 축이 다른
 * 열이라 `COALESCE` 로 잇는 변이가 test 에서 붉어진다. provenance 는 저장한 것을 그대로
 * 복원한다(`ProvenanceCodec` — 상수를 씌우지 않는다).
 */
private fun ResultSet.toConfirmedAward(): AwardAmount? {
    val won = getBigDecimal("final_award_amount_won") ?: return null
    val currency = Currency.valueOf(requireNotNull(getString("final_award_amount_currency")))
    val provenance =
        ProvenanceCodec.decode(
            requireNotNull(getString("final_award_amount_provenance")),
            getString("final_award_amount_provenance_detail"),
        )
    return AwardAmount(won.toLong(), currency, provenance)
}

/**
 * 잠정 축 — kind 가 왕복의 판별자다(`OpeningRankOneKind` 의 어휘를 그대로 쓴다, 두 번째 사본을
 * 만들지 않는다). 그 object 의 `read` 를 쓰지 않는 이유는 그것이 복원하는 도메인 타입이
 * **상호를 요구**하기 때문이다 — 이 질의는 그 열을 읽지 않는다(D-7C5-3).
 */
private fun ResultSet.toProvisionalRankOne(): ProvisionalRankOne =
    when (val kind = getString("opening_rank_one_kind")) {
        null -> ProvisionalRankOne.NotObserved
        OpeningRankOneKind.RANK_MISSING -> ProvisionalRankOne.RankMissing(rankOneObservedAt())
        OpeningRankOneKind.RANK_DUPLICATED ->
            ProvisionalRankOne.RankDuplicated(
                count = getInt("opening_rank_one_duplicate_count"),
                observedAt = rankOneObservedAt(),
            )

        OpeningRankOneKind.DETERMINED ->
            ProvisionalRankOne.Determined(
                bidAmount = toProvisionalBidAmount(),
                bidRate = getBigDecimal("opening_rank_one_bid_rate_fraction")?.let(Rate::ofFraction),
                observedAt = rankOneObservedAt(),
            )

        else -> error("알 수 없는 opening_rank_one_kind: $kind")
    }

private fun ResultSet.rankOneObservedAt(): Instant =
    requireNotNull(getTimestamp("opening_rank_one_observed_at")) {
        "관측 시각 없는 개찰 1위 축은 스키마가 거부한다(V5 짝 CHECK)"
    }.toInstant()

private fun ResultSet.toProvisionalBidAmount(): ObservedBidAmount? {
    val won = getBigDecimal("opening_rank_one_bid_amount_won") ?: return null
    val currency = Currency.valueOf(requireNotNull(getString("opening_rank_one_bid_amount_currency")))
    return ObservedBidAmount(won.toLong(), currency)
}
