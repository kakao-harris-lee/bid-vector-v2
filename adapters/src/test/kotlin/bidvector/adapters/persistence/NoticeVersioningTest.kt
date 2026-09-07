package bidvector.adapters.persistence

import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * ③ 멱등 upsert/versioning — 「retry 후에도 동일 공고의 canonical effect 하나」와 「같은 공고
 * 재수집은 canonical 행 하나에 revision 증가 + audit 행」을 함께 증명한다. `OPEN-DIC-06`
 * (거부하지 않고 권위 없음으로 저장 — 빈 자리만 채움)도 여기서 실측한다.
 */
class NoticeVersioningTest : PersistenceTestSupport() {
    private val id = NoticeId(NoticeNumber.of("VER-20260907-001"), NoticeRound.of("000"))

    private fun appendRaw(observation: RawNoticeObservation): ObservationKey = appendRawObservation(observation)

    private fun command(
        baseAmountWon: Long?,
        marker: String,
        observedAt: Instant,
    ): NoticeCollected {
        val fields =
            mapOf(
                RawKey("bidNtceNo") to id.number.value,
                RawKey("bidNtceOrd") to id.round.value,
                RawKey("marker") to marker,
            )
        val raw = RawNoticeObservation.of(fields, SourceEndpoint.NOTICE_LIST, observedAt)
        return NoticeCollected(
            id = id,
            businessCategory = null,
            baseAmount =
                baseAmountWon?.let {
                    ResolvedBaseAmount.Direct.of(it, Currency.KRW, VatTreatment.UNKNOWN, Provenance.Published(id.round))
                },
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = null,
            openingScheduledAt = null,
            raw = raw,
        )
    }

    private fun auditRowCount(): Long {
        val sql = "SELECT count(*) FROM notice_audit WHERE notice_number = ? AND notice_round = ?"
        return dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, id.number.value)
                statement.setString(2, id.round.value)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }
    }

    @Test
    fun `첫 수집은 Inserted, 같은 raw 를 재시도로 다시 append 해도 canonical 행은 하나뿐이다`() {
        val repository = JdbcNoticeRepository(dataSource())
        val firstCommand = command(1_000_000L, "first", Instant.parse("2026-09-07T00:00:00Z"))
        val key = appendRaw(firstCommand.raw)

        repository.persist(firstCommand, key) shouldBe PersistOutcome.Inserted

        // 재시도(같은 raw 내용) — RawObservationStore.append이 같은 ObservationKey를 내고
        // ON CONFLICT DO NOTHING으로 raw 행이 늘지 않는다(③). canonical persist도 같은 값의
        // 재시도이므로 Unchanged다.
        val retriedKey = appendRaw(firstCommand.raw)
        retriedKey shouldBe key
        repository.persist(firstCommand, retriedKey) shouldBe PersistOutcome.Unchanged

        rawObservationRowCount() shouldBe 1L
        noticeRowCount() shouldBe 1L
    }

    @Test
    fun `값이 바뀐 재수집은 revision 을 증가시키고 audit 행을 남긴다`() {
        val repository = JdbcNoticeRepository(dataSource())
        val firstCommand = command(1_000_000L, "first", Instant.parse("2026-09-07T00:00:00Z"))
        repository.persist(firstCommand, appendRaw(firstCommand.raw))
        auditRowCount() shouldBe 0L

        // 두 번째 관측 — 값이 다르고, 같은 권위 계층(둘 다 Published)이라 갱신이 허용된다.
        val secondCommand = command(2_000_000L, "second", Instant.parse("2026-09-07T01:00:00Z"))
        val outcome = repository.persist(secondCommand, appendRaw(secondCommand.raw))

        outcome shouldBe PersistOutcome.Updated(2L)
        auditRowCount() shouldBe 1L
        currentRevision() shouldBe 2L
    }

    @Test
    fun `Undeclared 는 거부되지 않고 권위 없음으로 저장된다 — 빈 자리만 채운다(OPEN-DIC-06)`() {
        // ResolvedBaseAmount의 세 variant(Direct·FallbackFromBudget·DerivedFromOpeningAmount)는
        // 각각 자기 provenance만 받는 sealed 폐쇄 타입이라 Undeclared를 나를 수 없다
        // (ResolvedBaseAmount.kt init) — Undeclared가 실제로 오는 자리는 `AllocatedBudget`처럼
        // provenance 축에 제약이 없는 타입이다(shared-kernel `AllocatedBudget(won, currency,
        // provenance)`).
        val repository = JdbcNoticeRepository(dataSource())
        val withoutBudget = command(null, "first", Instant.parse("2026-09-07T00:00:00Z"))
        repository.persist(withoutBudget, appendRaw(withoutBudget.raw)) shouldBe PersistOutcome.Inserted

        val undeclaredRaw =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-07T02:00:00Z"),
            )
        val undeclaredKey = appendRaw(undeclaredRaw)
        val undeclaredCommand =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = AllocatedBudget(500_000L, Currency.KRW, Provenance.Undeclared),
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = undeclaredRaw,
            )

        val outcome = repository.persist(undeclaredCommand, undeclaredKey)

        outcome shouldBe PersistOutcome.Updated(2L)
    }

    private fun rawObservationRowCount(): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM raw_observation").use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    private fun noticeRowCount(): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM notice").use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    private fun currentRevision(): Long {
        val sql = "SELECT revision FROM notice WHERE notice_number = ? AND notice_round = ?"
        return dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, id.number.value)
                statement.setString(2, id.round.value)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong("revision")
                }
            }
        }
    }
}
