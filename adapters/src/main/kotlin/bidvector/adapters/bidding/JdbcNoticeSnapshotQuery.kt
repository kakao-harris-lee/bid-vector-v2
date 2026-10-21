package bidvector.adapters.bidding

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.connectionSourceOf
import javax.sql.DataSource
import bidvector.bidding.NoticeKey
import bidvector.bidding.NoticeSnapshot
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.bidding.NoticeSnapshotQueryPort
import java.sql.ResultSet
import java.time.Instant

/**
 * 초안이 얼릴 공고 스냅샷을 **서버가 뜬다** — 요청이 실어 오지 않는다(위협 ③ 차단면).
 *
 * 읽는 것은 `notice` 의 **지금 값**이고, 그 값을 `bid_draft` 에 **사본으로** 적는다. 그래서
 * 이후 공고가 정정돼도 초안은 그대로다 — 정정됐다는 사실은 `snapshot_notice_revision` 과
 * 현재 `notice.revision` 의 차이로 드러난다.
 *
 * 공고가 없으면(또는 6B-3 파기로 사라졌으면) `null` 이다. 지어내지 않는다.
 */
class JdbcNoticeSnapshotQuery(
    private val connections: ConnectionSource,
) : NoticeSnapshotQueryPort {
    /** 조립이 쓰는 생성자 — `DataSource` 하나를 받는다(`ConnectionSource` 는 adapters 안의 타입이다). */
    constructor(dataSource: DataSource) : this(connectionSourceOf(dataSource))

    override fun snapshot(
        notice: NoticeKey,
        takenAt: Instant,
    ): NoticeSnapshot? =
        connections.withConnection { connection ->
            connection.prepareStatement(SELECT_SNAPSHOT).use { statement ->
                statement.setString(1, notice.noticeNumber)
                statement.setString(2, notice.noticeRound.value)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toSnapshot(notice.noticeRound, takenAt) else null
                }
            }
        }

    private fun ResultSet.toSnapshot(
        round: NoticeRound,
        takenAt: Instant,
    ): NoticeSnapshot =
        NoticeSnapshot(
            noticeRevisionNumber = getLong("revision"),
            takenAt = takenAt,
            deadlineAt = instantOrNull("deadline_at"),
            floorRate = floorRate(round),
            baseAmount = baseAmount(round),
        )

    /** 하한율은 값과 출처가 **짝**이다(V1 의 CHECK 와 같은 축) — 한쪽만 있으면 `null` 이다. */
    private fun ResultSet.floorRate(round: NoticeRound): FloorRate? {
        val fraction = getBigDecimal("floor_rate_fraction") ?: return null
        val kind = getString("floor_rate_origin_kind") ?: return null
        val origin =
            when (kind) {
                "NOTICE_VALUE" -> FloorRateOrigin.NoticeValue(round)
                else -> return null
            }
        return FloorRate(Rate.ofFraction(fraction), origin)
    }

    /**
     * 기초금액 — provenance 어휘를 **좁게** 복원한다. 모르는 어휘는 `null` 이다(스냅샷에
     * 「출처 모름」 금액을 싣느니 금액을 비운다 — basis 없는 금액을 만들지 않는다).
     */
    private fun ResultSet.baseAmount(round: NoticeRound): BaseAmount? {
        val won = getBigDecimal("base_amount_won") ?: return null
        val currencyName = getString("base_amount_currency") ?: return null
        val vatName = getString("base_amount_vat") ?: return null
        val provenance =
            when (getString("base_amount_provenance")) {
                "Published" -> Provenance.Published(round)
                "DerivedFromOpening" -> Provenance.DerivedFromOpening
                "CopiedFromBaseAmount" -> Provenance.CopiedFromBaseAmount
                "OperatorDeclared" -> Provenance.OperatorDeclared
                "Undeclared" -> Provenance.Undeclared
                else -> return null
            }
        return BaseAmount(
            won = won.longValueExact(),
            currency = Currency.valueOf(currencyName),
            vatTreatment = VatTreatment.valueOf(vatName),
            provenance = provenance,
        )
    }

    private companion object {
        const val SELECT_SNAPSHOT =
            "SELECT revision, deadline_at, floor_rate_fraction, floor_rate_origin_kind, " +
                "base_amount_won, base_amount_currency, base_amount_vat, base_amount_provenance " +
                "FROM notice WHERE notice_number = ? AND notice_round = ?"
    }
}
