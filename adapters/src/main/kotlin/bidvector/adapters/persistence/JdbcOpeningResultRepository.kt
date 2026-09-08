package bidvector.adapters.persistence

import bidvector.procurement.NoticeId
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningReservePriceRow
import bidvector.procurement.OpeningResult
import bidvector.procurement.OpeningResultRepository
import bidvector.procurement.PersistOutcome
import bidvector.procurement.ReservePriceCandidateAmount
import bidvector.procurement.ResolvedBaseAmount
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.YegaAmount
import bidvector.sharedkernel.export
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * [OpeningResultRepository] JDBC 구현 — provenance 점유 가드가 없는 「최신 관측 우선」 축
 * (`derived_base_amount`는 항상 `Provenance.DerivedFromOpening`이라 권위 계층 자체가 없다,
 * V1 migration KDoc). 한 SQL 문(`ON CONFLICT ... WHERE observed_at >= ...`)이 insert/update/
 * no-op을 다 낸다 — [toUpsertOutcome]가 어느 경로였는지 안다(PostgreSQL 관용구).
 *
 * **M3/3E ⑤⑥ — 부모+자식 한 항목 트랜잭션**(D-3E-2 (a), scope.md 「이 slice가 하는 일」⑤).
 * `persist`가 `opening_result`(부모) upsert 하나와 `reservePrices`(자식) upsert 반복을 같은
 * 연결(`autoCommit = false`, `commit()`은 둘 다 성공한 뒤)에서 묶는다 — 자식 쓰기가 실패하면
 * 예외가 `commit()` 전에 던져지고, `connection.use`가 커밋 없이 close하므로 JDBC 기본 동작이
 * 그 트랜잭션을 롤백한다(`ItemAtomicityTest`가 `JdbcNoticeRepository`로 이미 실측한 것과
 * 같은 경계 — 별도 `try`/`rollback`이 필요 없다).
 * **D-3E-3 (a)** — 이 메서드는 자식 행을 지우지 않는다. `reservePrices`에 없는 기존 자식 행은
 * 손대지 않고 그대로 남는다(15→12 재수집이 사라진 3행을 지우지 않는 것이 이 부재 자체다).
 */
class JdbcOpeningResultRepository(
    private val dataSource: DataSource,
) : OpeningResultRepository {
    override fun persist(
        result: OpeningResult,
        observationKey: ObservationKey,
    ): PersistOutcome =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            val outcome =
                connection.prepareStatement(Sql.UPSERT_OPENING_RESULT).use { statement ->
                    bindOpeningResult(statement, result, observationKey)
                    statement.executeQuery().use { it.toUpsertOutcome() }
                }
            persistReservePrices(connection, result, observationKey)
            connection.commit()
            outcome
        }

    override fun find(id: NoticeId): OpeningResult? =
        dataSource.connection.use { connection ->
            val parent = connection.queryNoticeScoped(Sql.SELECT_OPENING_RESULT, id) { rs -> rs.toOpeningResult(id) }
            parent?.copy(reservePrices = connection.selectReservePrices(id))
        }

    private fun persistReservePrices(
        connection: Connection,
        result: OpeningResult,
        observationKey: ObservationKey,
    ) {
        if (result.reservePrices.isEmpty()) return
        connection.prepareStatement(Sql.UPSERT_OPENING_RESERVE_PRICE).use { statement ->
            for (row in result.reservePrices) {
                bindReservePriceRow(statement, result.noticeId, row, result.observedAt, observationKey)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun Connection.selectReservePrices(id: NoticeId): List<OpeningReservePriceRow> =
        prepareStatement(Sql.SELECT_OPENING_RESERVE_PRICES).use { statement ->
            statement.setString(1, id.number.value)
            statement.setString(2, id.round.value)
            statement.executeQuery().use { rs ->
                val rows = mutableListOf<OpeningReservePriceRow>()
                while (rs.next()) rows += rs.toReservePriceRow()
                rows
            }
        }
}

@Suppress("LongParameterList")
private fun bindReservePriceRow(
    statement: PreparedStatement,
    noticeId: NoticeId,
    row: OpeningReservePriceRow,
    observedAt: Instant,
    observationKey: ObservationKey,
) {
    var index = 1
    statement.setString(index++, noticeId.number.value)
    statement.setString(index++, noticeId.round.value)
    statement.setString(index++, row.sequenceNumber)
    statement.setBigDecimal(index++, row.baseReservePrice?.won?.let(BigDecimal::valueOf))
    statement.setString(index++, row.baseReservePrice?.currency?.name)
    statement.setNullableBoolean(index++, row.isDrawn)
    statement.setNullableInt(index++, row.drawCount)
    statement.setTimestamp(index++, Timestamp.from(observedAt))
    statement.setString(index, observationKey.value)
}

private fun ResultSet.toReservePriceRow(): OpeningReservePriceRow {
    val won = getBigDecimal("base_reserve_price_won")
    val currencyName = getString("base_reserve_price_currency")
    val baseReservePrice =
        if (won != null && currencyName != null) {
            ReservePriceCandidateAmount(won.toLong(), Currency.valueOf(currencyName))
        } else {
            null
        }
    return OpeningReservePriceRow(
        sequenceNumber = getString("reserve_price_sequence"),
        baseReservePrice = baseReservePrice,
        isDrawn = getBoolean("is_drawn").takeUnless { wasNull() },
        drawCount = getInt("draw_count").takeUnless { wasNull() },
    )
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
    statement.setBigDecimal(index++, result.derivedBaseAmount?.amount?.wonOf())
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
    statement.setBigDecimal(index++, result.finalAwardAmount?.wonOf())
    statement.setString(index++, result.finalAwardAmount?.currency?.name)
    statement.setString(index++, result.finalAwardCompanyName)
    statement.setNullableInt(index++, result.participantCount)
    statement.setString(index++, result.progressDivision)
    statement.setBigDecimal(index++, result.plannedPrice?.wonOf())
    statement.setString(index++, result.plannedPrice?.currency?.name)
    statement.setBigDecimal(index++, result.baseAmount?.wonOf())
    statement.setString(index++, result.baseAmount?.currency?.name)
    statement.setString(index++, result.baseAmount?.vatTreatment?.name)
    statement.setNullableInt(index++, result.totalReservePriceCandidateCount)
    statement.setNullableTimestamp(index++, result.actualOpeningAt)
    statement.setTimestamp(index++, Timestamp.from(result.observedAt))
    statement.setString(index, observationKey.value)
}

/** `Money.export().won`을 `BigDecimal`로 — 세 Money 구현(`AwardAmount`·`YegaAmount`·`BaseAmount`)이 공유. */
private fun Money.wonOf(): BigDecimal = BigDecimal.valueOf(export().won)

private fun ResultSet.toOpeningResult(id: NoticeId): OpeningResult {
    val winningFraction = getBigDecimal("winning_rate_fraction")
    val derivedWon = getBigDecimal("derived_base_amount_won")
    val derived = derivedWon?.let { won -> toDerivedBaseAmount(won) }
    // ①③ — 최종낙찰금액·예정가격·기초금액은 파생이 아니라 KONEPS 공식 응답에서 직접 온 값이다
    // (D-3A-1 (a) `Notice`의 공고 목록 축과 같은 성격) — `Provenance.Published(id.round)`가
    // 그 사실을 정확히 진술한다. 지어낸 값이 아니라 `id.round`에서 실제로 유도된다.
    val published = Provenance.Published(id.round)
    return OpeningResult(
        noticeId = id,
        winningRate = winningFraction?.let(Rate::ofFraction),
        derivedBaseAmount = derived,
        observedAt = getTimestamp("observed_at").toInstant(),
        finalAwardAmount =
            readWonCurrency("final_award_amount_won", "final_award_amount_currency")
                ?.let { (won, currency) -> AwardAmount(won, currency, published) },
        finalAwardCompanyName = getString("final_award_company_name"),
        participantCount = getInt("participant_count").takeUnless { wasNull() },
        progressDivision = getString("progress_division"),
        plannedPrice =
            readWonCurrency("planned_price_won", "planned_price_currency")
                ?.let { (won, currency) -> YegaAmount(won, currency, published) },
        baseAmount = toOpeningBaseAmount(published),
        totalReservePriceCandidateCount = getInt("total_reserve_price_candidate_count").takeUnless { wasNull() },
        actualOpeningAt = getTimestamp("actual_opening_at")?.toInstant(),
    )
}

private fun ResultSet.toDerivedBaseAmount(won: BigDecimal): ResolvedBaseAmount.DerivedFromOpeningAmount {
    val currency = Currency.valueOf(requireNotNull(getString("derived_base_amount_currency")))
    val vat = VatTreatment.valueOf(requireNotNull(getString("derived_base_amount_vat")))
    val amount = BaseAmount(won.toLong(), currency, vat, Provenance.DerivedFromOpening)
    return ResolvedBaseAmount.DerivedFromOpeningAmount(amount)
}

/** `won`+`currency` 컬럼 짝을 읽는 공통 형태 — `AwardAmount`·`YegaAmount` 복원이 공유(중복 제거). */
private fun ResultSet.readWonCurrency(
    wonColumn: String,
    currencyColumn: String,
): Pair<Long, Currency>? {
    val won = getBigDecimal(wonColumn) ?: return null
    return won.toLong() to Currency.valueOf(requireNotNull(getString(currencyColumn)))
}

/** `BaseAmount`(bssamt, 예비가격 상세 축 자신의 관측)로 복원한다 — vat 축까지 있어 [readWonCurrency]를 못 쓴다. */
private fun ResultSet.toOpeningBaseAmount(provenance: Provenance): BaseAmount? {
    val won = getBigDecimal("opening_base_amount_won") ?: return null
    val currency = Currency.valueOf(requireNotNull(getString("opening_base_amount_currency")))
    val vat = VatTreatment.valueOf(requireNotNull(getString("opening_base_amount_vat")))
    return BaseAmount(won.toLong(), currency, vat, provenance)
}
