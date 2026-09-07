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
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/** [OpeningResultRepository]·[QualificationTextRepository]의 「최신 관측 우선」 upsert(observed_at, OPEN-DIC-09 잠정 ⓐ). */
class OpeningQualificationRepositoryTest : PersistenceTestSupport() {
    private val id = NoticeId(NoticeNumber.of("OPEN-20260907-001"), NoticeRound.of("000"))

    private fun appendRaw(observedAt: Instant): ObservationKey {
        val observation = RawNoticeObservation.of(emptyMap(), SourceEndpoint.OPENING_RESULT, observedAt)
        val key = ObservationKey.of(observation)
        dataSource().connection.use { connection ->
            connection.prepareStatement(Sql.INSERT_RAW_OBSERVATION).use { statement ->
                statement.setString(1, key.value)
                statement.setString(2, observation.sourceEndpoint.name)
                statement.setString(3, "{}")
                statement.setTimestamp(4, java.sql.Timestamp.from(observedAt))
                statement.setString(5, "test-release")
                statement.executeUpdate()
            }
        }
        return key
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
}
