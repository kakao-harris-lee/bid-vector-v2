package bidvector.adapters.bidding

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.connectionSourceOf
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
import javax.sql.DataSource

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

    /**
     * 하한율은 값과 출처가 **짝**이다(V1 의 CHECK 와 같은 축) — 한쪽만 있으면 `null` 이다.
     * 출처 어휘는 좁게 받는다(모르는 어휘는 `null`).
     */
    private fun ResultSet.floorRate(round: NoticeRound): FloorRate? {
        val fraction = getBigDecimal("floor_rate_fraction")
        val origin =
            when (getString("floor_rate_origin_kind")) {
                "NOTICE_VALUE" -> FloorRateOrigin.NoticeValue(round)
                else -> null
            }
        return if (fraction == null || origin == null) null else FloorRate(Rate.ofFraction(fraction), origin)
    }

    /**
     * 기초금액 — 네 성분이 **모두** 있을 때만 값을 낸다. provenance 어휘도 좁게 복원하고,
     * 모르는 어휘면 금액 자체를 비운다 — basis · 출처 없는 금액을 스냅샷에 싣느니 「없음」이 낫다.
     */
    private fun ResultSet.baseAmount(round: NoticeRound): BaseAmount? {
        val won = getBigDecimal("base_amount_won")
        val currencyName = getString("base_amount_currency")
        val vatName = getString("base_amount_vat")
        val provenance = provenanceOf(getString("base_amount_provenance"), round)
        // 네 성분 중 하나라도 비면 금액을 내지 않는다 — 「전부 있는가」를 한 자리에서 센다.
        val parts = listOf(won, currencyName, vatName, provenance)
        if (parts.any { it == null }) return null
        return BaseAmount(
            won = requireNotNull(won).longValueExact(),
            currency = Currency.valueOf(requireNotNull(currencyName)),
            vatTreatment = VatTreatment.valueOf(requireNotNull(vatName)),
            provenance = requireNotNull(provenance),
        )
    }

    /** 어휘 → provenance 룩업. 모르는 값은 `null` 이다(지어내지 않는다). */
    private fun provenanceOf(
        token: String?,
        round: NoticeRound,
    ): Provenance? =
        when (token) {
            "Published" -> Provenance.Published(round)
            "DerivedFromOpening" -> Provenance.DerivedFromOpening
            "CopiedFromBaseAmount" -> Provenance.CopiedFromBaseAmount
            "OperatorDeclared" -> Provenance.OperatorDeclared
            "Undeclared" -> Provenance.Undeclared
            else -> null
        }

    private companion object {
        const val SELECT_SNAPSHOT =
            "SELECT revision, deadline_at, floor_rate_fraction, floor_rate_origin_kind, " +
                "base_amount_won, base_amount_currency, base_amount_vat, base_amount_provenance " +
                "FROM notice WHERE notice_number = ? AND notice_round = ?"
    }
}
