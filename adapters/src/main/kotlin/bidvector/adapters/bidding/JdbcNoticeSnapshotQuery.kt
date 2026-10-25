package bidvector.adapters.bidding

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.DataSourceConnectionSource
import bidvector.adapters.persistence.FloorRateOriginCodec
import bidvector.adapters.persistence.ProvenanceCodec
import bidvector.bidding.NoticeKey
import bidvector.bidding.NoticeSnapshot
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.FloorRate
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
    constructor(dataSource: DataSource) : this(DataSourceConnectionSource(dataSource))

    override fun snapshot(
        notice: NoticeKey,
        takenAt: Instant,
    ): NoticeSnapshot? =
        connections.withConnection { connection ->
            connection.prepareStatement(SELECT_SNAPSHOT).use { statement ->
                statement.setString(1, notice.noticeNumber)
                statement.setString(2, notice.noticeRound.value)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toSnapshot(takenAt) else null
                }
            }
        }

    private fun ResultSet.toSnapshot(takenAt: Instant): NoticeSnapshot =
        NoticeSnapshot(
            noticeRevisionNumber = getLong("revision"),
            takenAt = takenAt,
            deadlineAt = instantOrNull("deadline_at"),
            floorRate = floorRate(),
            baseAmount = baseAmount(),
        )

    /**
     * 하한율은 값과 출처가 **짝**이다(V1 의 CHECK 와 같은 축). 출처는 `notice` 를 쓴 코덱
     * ([FloorRateOriginCodec])이 그대로 푼다 — 공고 판(`NoticeValue`)과 법정표(`StatutoryTable`) 둘 다다.
     */
    private fun ResultSet.floorRate(): FloorRate? {
        val fraction = getBigDecimal("floor_rate_fraction")
        val kind = getString("floor_rate_origin_kind")
        val detail = getString("floor_rate_origin_detail")
        return if (fraction == null || kind == null || detail == null) {
            null
        } else {
            FloorRate(Rate.ofFraction(fraction), FloorRateOriginCodec.decode(kind, detail))
        }
    }

    /**
     * 기초금액 — 네 성분이 **모두** 있을 때만 값을 낸다(V1 의 짝 CHECK 가 그것을 보장한다). 출처는
     * `notice` 를 쓴 코덱([ProvenanceCodec])이 detail 과 함께 푼다(계약 D-7B-3, 검토 B-1).
     */
    private fun ResultSet.baseAmount(): BaseAmount? {
        val won = getBigDecimal("base_amount_won")
        val currencyName = getString("base_amount_currency")
        val vatName = getString("base_amount_vat")
        val provenance = getString("base_amount_provenance")
        // 네 성분 중 하나라도 비면 금액을 내지 않는다 — 「전부 있는가」를 한 자리에서 센다.
        if (listOf(won, currencyName, vatName, provenance).any { it == null }) return null
        return BaseAmount(
            won = requireNotNull(won).longValueExact(),
            currency = Currency.valueOf(requireNotNull(currencyName)),
            vatTreatment = VatTreatment.valueOf(requireNotNull(vatName)),
            provenance = ProvenanceCodec.decode(requireNotNull(provenance), getString("base_amount_provenance_detail")),
        )
    }

    private companion object {
        const val SELECT_SNAPSHOT =
            "SELECT revision, deadline_at, floor_rate_fraction, floor_rate_origin_kind, floor_rate_origin_detail, " +
                "base_amount_won, base_amount_currency, base_amount_vat, base_amount_provenance, " +
                "base_amount_provenance_detail FROM notice WHERE notice_number = ? AND notice_round = ?"
    }
}
