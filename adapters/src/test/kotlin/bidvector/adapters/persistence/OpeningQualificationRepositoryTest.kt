package bidvector.adapters.persistence

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningResult
import bidvector.procurement.PersistOutcome
import bidvector.procurement.QualificationText
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.math.BigDecimal
import java.time.Instant

/** [OpeningResultRepository]·[QualificationTextRepository]의 「최신 관측 우선」 upsert(observed_at, OPEN-DIC-09 잠정 ⓐ). */
class OpeningQualificationRepositoryTest : PersistenceTestSupport() {
    private val id = NoticeId(NoticeNumber.of("OPEN-20260907-001"), NoticeRound.of("000"))

    private fun appendRaw(observedAt: Instant): ObservationKey {
        val observation = RawNoticeObservation.of(emptyMap(), SourceEndpoint.OPENING_RESULT, observedAt)
        return appendRawObservation(observation)
    }

    @Test
    fun `OpeningResult 최초 저장은 Inserted, 더 늦은 관측 재저장은 Updated, 더 이른 관측은 Unchanged`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val first = OpeningResult(id, Rate.ofFraction(BigDecimal("0.87")), null, Instant.parse("2026-09-07T00:00:00Z"))
        repository.persist(first, appendRaw(first.observedAt)) shouldBe PersistOutcome.Inserted

        val derivedAmount = BaseAmount(1_500_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.DerivedFromOpening)
        val later =
            OpeningResult(
                id,
                Rate.ofFraction(BigDecimal("0.90")),
                ResolvedBaseAmount.DerivedFromOpeningAmount(derivedAmount),
                Instant.parse("2026-09-07T01:00:00Z"),
            )
        repository.persist(later, appendRaw(later.observedAt)) shouldBe PersistOutcome.Updated(2L)

        val stale = OpeningResult(id, Rate.ofFraction(BigDecimal("0.10")), null, Instant.parse("2026-09-06T00:00:00Z"))
        repository.persist(stale, appendRaw(stale.observedAt)) shouldBe PersistOutcome.Unchanged

        val found = repository.find(id)
        found?.winningRate shouldBe Rate.ofFraction(BigDecimal("0.90"))
    }

    @Test
    fun `QualificationText 도 같은 최신 관측 우선 upsert 를 따른다`() {
        val repository = JdbcQualificationTextRepository(dataSource())
        val first = QualificationText(id, "면허 A 필요", Instant.parse("2026-09-07T00:00:00Z"))
        repository.persist(first, appendRaw(first.observedAt)) shouldBe PersistOutcome.Inserted

        val later = QualificationText(id, "면허 A, B 필요", Instant.parse("2026-09-07T01:00:00Z"))
        repository.persist(later, appendRaw(later.observedAt)) shouldBe PersistOutcome.Updated(2L)

        repository.find(id)?.rawText shouldBe "면허 A, B 필요"
    }

    /**
     * M-c(verifier r2) 재현 — [OpeningResult]의 `winningRate`·`derivedBaseAmount`는 둘 다
     * nullable이라 부분 관측(하나만 실린 더 늦은 관측)이 정상이다(설계 검토 ④ 「비었으면
     * 지우지 않는다」). COALESCE 이전 판은 이 부분 관측이 오면 이미 있던
     * `derivedBaseAmount`를 NULL로 지워 존재 가드가 항목 전체를 실패시켰다.
     */
    @Test
    fun `M-c 재현 — winningRate 만 실은 더 늦은 부분 관측은 derivedBaseAmount 를 지우지 않고 항목은 성공한다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val derivedAmount = BaseAmount(1_500_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.DerivedFromOpening)
        val seeded =
            OpeningResult(
                id,
                Rate.ofFraction(BigDecimal("0.80")),
                ResolvedBaseAmount.DerivedFromOpeningAmount(derivedAmount),
                Instant.parse("2026-09-07T00:00:00Z"),
            )
        repository.persist(seeded, appendRaw(seeded.observedAt)) shouldBe PersistOutcome.Inserted

        val partial =
            OpeningResult(id, Rate.ofFraction(BigDecimal("0.95")), null, Instant.parse("2026-09-07T02:00:00Z"))
        repository.persist(partial, appendRaw(partial.observedAt)) shouldBe PersistOutcome.Updated(2L)

        val found = requireNotNull(repository.find(id))
        found.winningRate shouldBe Rate.ofFraction(BigDecimal("0.95"))
        found.derivedBaseAmount shouldBe ResolvedBaseAmount.DerivedFromOpeningAmount(derivedAmount)
    }

    @Test
    fun `F-5 재현 — 애플리케이션 역할의 직접 SQL 로 derived_base_amount_won 을 슬쩍 바꾸면 거부된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val derivedAmount = BaseAmount(1_500_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.DerivedFromOpening)
        val seeded =
            OpeningResult(
                id,
                Rate.ofFraction(BigDecimal("0.90")),
                ResolvedBaseAmount.DerivedFromOpeningAmount(derivedAmount),
                Instant.parse("2026-09-07T00:00:00Z"),
            )
        repository.persist(seeded, appendRaw(seeded.observedAt)) shouldBe PersistOutcome.Inserted

        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection
                    .prepareStatement(
                        "UPDATE opening_result SET derived_base_amount_won = 1 " +
                            "WHERE notice_number = ? AND notice_round = ?",
                    ).use { statement ->
                        statement.setString(1, id.number.value)
                        statement.setString(2, id.round.value)
                        statement.executeUpdate()
                    }
            }
            connection.rollback()
        }

        repository.find(id)?.derivedBaseAmount shouldBe ResolvedBaseAmount.DerivedFromOpeningAmount(derivedAmount)
    }

    @Test
    fun `F-5 재현 — derived_base_amount_won 을 NULL 로 지우는 직접 SQL 도 존재 가드가 거부한다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val derivedAmount = BaseAmount(1_500_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.DerivedFromOpening)
        val seeded =
            OpeningResult(
                id,
                null,
                ResolvedBaseAmount.DerivedFromOpeningAmount(derivedAmount),
                Instant.parse("2026-09-07T00:00:00Z"),
            )
        repository.persist(seeded, appendRaw(seeded.observedAt)) shouldBe PersistOutcome.Inserted

        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection
                    .prepareStatement(
                        "UPDATE opening_result SET derived_base_amount_won = NULL, " +
                            "derived_base_amount_currency = NULL, derived_base_amount_vat = NULL " +
                            "WHERE notice_number = ? AND notice_round = ?",
                    ).use { statement ->
                        statement.setString(1, id.number.value)
                        statement.setString(2, id.round.value)
                        statement.executeUpdate()
                    }
            }
            connection.rollback()
        }
    }

    @Test
    fun `F-5 재현 — qualification_text 를 같은 observation_key 로 위조하는 직접 SQL 은 거부된다`() {
        val repository = JdbcQualificationTextRepository(dataSource())
        val seeded = QualificationText(id, "면허 A 필요", Instant.parse("2026-09-07T00:00:00Z"))
        repository.persist(seeded, appendRaw(seeded.observedAt)) shouldBe PersistOutcome.Inserted

        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection
                    .prepareStatement(
                        "UPDATE qualification_text SET raw_text = 'forged' " +
                            "WHERE notice_number = ? AND notice_round = ?",
                    ).use { statement ->
                        statement.setString(1, id.number.value)
                        statement.setString(2, id.round.value)
                        statement.executeUpdate()
                    }
            }
            connection.rollback()
        }

        repository.find(id)?.rawText shouldBe "면허 A 필요"
    }
}
