package bidvector.adapters.persistence

import bidvector.procurement.NoticeId
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningResult
import bidvector.procurement.OpeningResultRepository
import bidvector.procurement.PersistOutcome
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.export
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * [OpeningResultRepository] JDBC 구현 — provenance 점유 가드가 없는 「최신 관측 우선」 축
 * (`derived_base_amount`는 항상 `Provenance.DerivedFromOpening`이라 권위 계층 자체가 없다,
 * V1 migration KDoc). 한 SQL 문(`ON CONFLICT ... WHERE observed_at >= ...`)이 insert/update/
 * no-op을 다 낸다 — [toUpsertOutcome]가 어느 경로였는지 안다(PostgreSQL 관용구).
 */
class JdbcOpeningResultRepository(
    private val dataSource: DataSource,
) : OpeningResultRepository {
    override fun persist(
        result: OpeningResult,
        observationKey: ObservationKey,
    ): PersistOutcome =
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.UPSERT_OPENING_RESULT).use { statement ->
                bindOpeningResult(statement, result, observationKey)
                statement.executeQuery().use { it.toUpsertOutcome() }
            }
        }

    override fun find(id: NoticeId): OpeningResult? =
        dataSource.connection.use { connection ->
            connection.queryNoticeScoped(Sql.SELECT_OPENING_RESULT, id) { rs -> rs.toOpeningResult(id) }
        }
}

private fun bindOpeningResult(
    statement: PreparedStatement,
    result: OpeningResult,
    observationKey: ObservationKey,
) {
    var index = 1
    statement.setString(index++, result.noticeId.number.value)
    statement.setString(index++, result.noticeId.round.value)
    statement.setBigDecimal(index++, result.winningRate?.fraction)
    statement.setBigDecimal(index++, result.derivedBaseAmount?.amount?.let(::exportedWon))
    statement.setString(
        index++,
        result.derivedBaseAmount
            ?.amount
            ?.currency
            ?.name,
    )
    statement.setString(
        index++,
        result.derivedBaseAmount
            ?.amount
            ?.vatTreatment
            ?.name,
    )
    statement.setTimestamp(index++, Timestamp.from(result.observedAt))
    statement.setString(index, observationKey.value)
}

private fun exportedWon(amount: BaseAmount): BigDecimal = BigDecimal.valueOf(amount.export().won)

private fun ResultSet.toOpeningResult(id: NoticeId): OpeningResult {
    val winningFraction = getBigDecimal("winning_rate_fraction")
    val derivedWon = getBigDecimal("derived_base_amount_won")
    val derived = derivedWon?.let { won -> toDerivedBaseAmount(won) }
    return OpeningResult(id, winningFraction?.let(Rate::ofFraction), derived, getTimestamp("observed_at").toInstant())
}

private fun ResultSet.toDerivedBaseAmount(won: BigDecimal): ResolvedBaseAmount.DerivedFromOpeningAmount {
    val currency = Currency.valueOf(requireNotNull(getString("derived_base_amount_currency")))
    val vat = VatTreatment.valueOf(requireNotNull(getString("derived_base_amount_vat")))
    val amount = BaseAmount(won.toLong(), currency, vat, Provenance.DerivedFromOpening)
    return ResolvedBaseAmount.DerivedFromOpeningAmount(amount)
}
