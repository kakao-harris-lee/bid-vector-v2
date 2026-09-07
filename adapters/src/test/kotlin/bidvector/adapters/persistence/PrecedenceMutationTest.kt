package bidvector.adapters.persistence

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.PersistOutcome
import bidvector.procurement.ProvenanceKind
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * S-3 — 파생/비권위 provenance write가 권위 자리를 덮는 mutation이 **DB 층에서** 거부된다
 * (④, D-3D-2 (a)). Kotlin 규칙([JdbcNoticeRepository])을 거치지 않은 **직접 SQL**로 우회를
 * 시도한다 — 애플리케이션 역할(`SET ROLE bidvector_app`)과 superuser 둘 다로 시도해, 트리거가
 * 역할과 무관하게(단, superuser의 트리거 비활성화 같은 DDL 권한 우회는 경계 밖) 거부함을
 * 증명한다.
 */
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-07T00:00:00Z")

class PrecedenceMutationTest : PersistenceTestSupport() {
    private val noticeId = NoticeId(NoticeNumber.of("PMT-20260907-001"), NoticeRound.of("000"))
    private val referenceDate = CollectionReferenceDate(LocalDate.of(2026, 9, 7))

    private fun fieldContracts(): KonepsFieldContractRegistry {
        val resolution = KONEPS_COLLECTION_POLICY.resolve(referenceDate.date)
        return (resolution as Resolution.Resolved).value.fieldContracts
    }

    /** authoritative(Published) base_amount를 가진 notice 행 하나를 심는다. */
    private fun seedAuthoritativeNotice(): ObservationKey {
        val rawObservation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to noticeId.number.value, RawKey("bidNtceOrd") to noticeId.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-07T00:00:00Z"),
            )
        val rawStore = JdbcRawObservationStore(dataSource(), fieldContracts(), "test-release")
        val key = rawStore.append(rawObservation)
        val command =
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        won = 1_000_000_000L,
                        currency = Currency.KRW,
                        vatTreatment = VatTreatment.UNKNOWN,
                        provenance = Provenance.Published(noticeId.round),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = rawObservation,
            )
        val outcome = JdbcNoticeRepository(dataSource()).persist(command, key)
        outcome shouldBe PersistOutcome.Inserted
        return key
    }

    private fun currentBaseAmountWon(): BigDecimal {
        val sql = "SELECT base_amount_won FROM notice WHERE notice_number = ? AND notice_round = ?"
        return dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, noticeId.number.value)
                statement.setString(2, noticeId.round.value)
                statement.executeQuery().use { rs ->
                    rs.next()
                    requireNotNull(rs.getBigDecimal("base_amount_won"))
                }
            }
        }
    }

    private fun attemptDowngrade(connection: Connection) {
        val sql =
            "UPDATE notice SET base_amount_won = 1, base_amount_provenance = ? " +
                "WHERE notice_number = ? AND notice_round = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, ProvenanceKind.FILLED_FROM_BUDGET_KEY.name)
            statement.setString(2, noticeId.number.value)
            statement.setString(3, noticeId.round.value)
            statement.executeUpdate()
        }
    }

    @Test
    fun `애플리케이션 역할의 직접 SQL 이 권위 있는 base_amount 를 비권위 provenance 로 덮으면 거부된다`() {
        seedAuthoritativeNotice()
        val before = currentBaseAmountWon()

        appConnection().use { connection ->
            shouldThrow<PSQLException> { attemptDowngrade(connection) }
            connection.rollback()
        }

        currentBaseAmountWon() shouldBe before
    }

    @Test
    fun `superuser 직접 SQL 도 같은 트리거를 지난다 — DDL 로 끄지 않는 한 거부된다`() {
        seedAuthoritativeNotice()
        val before = currentBaseAmountWon()

        dataSource().connection.use { connection ->
            connection.autoCommit = false
            shouldThrow<PSQLException> { attemptDowngrade(connection) }
            connection.rollback()
        }

        currentBaseAmountWon() shouldBe before
    }

    @Test
    fun `이미 값이 있는 base_amount 를 NULL 로 비우면 존재 가드가 거부한다`() {
        seedAuthoritativeNotice()
        val before = currentBaseAmountWon()

        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection
                    .prepareStatement(
                        "UPDATE notice SET base_amount_won = NULL, base_amount_currency = NULL, " +
                            "base_amount_vat = NULL, base_amount_provenance = NULL, " +
                            "base_amount_provenance_detail = NULL " +
                            "WHERE notice_number = ? AND notice_round = ?",
                    ).use { statement ->
                        statement.setString(1, noticeId.number.value)
                        statement.setString(2, noticeId.round.value)
                        statement.executeUpdate()
                    }
            }
            connection.rollback()
        }

        currentBaseAmountWon() shouldBe before
    }

    @Test
    fun `provenance 없이 금액만 갱신하면 CHECK 제약이 거부한다 — 금액과 provenance 는 함께만 갱신 가능`() {
        // base_amount가 아직 없는(둘 다 NULL) 행을 심는다 — 그래야 「금액만 채우고 provenance는
        // 비워 둔」 위반이 실제로 만들어진다(이미 둘 다 값이 있는 행에 won만 다시 쓰면 provenance
        // 열은 손대지 않은 채 그대로 남아 CHECK를 어기지 않는다).
        val emptyBaseAmountCommand =
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = RawNoticeObservation.of(emptyMap(), SourceEndpoint.NOTICE_LIST, FIXED_INSTANT),
            )
        val key = ObservationKey.of(emptyBaseAmountCommand.raw)
        dataSource().connection.use { connection ->
            connection.prepareStatement(Sql.INSERT_RAW_OBSERVATION).use { statement ->
                statement.setString(1, key.value)
                statement.setString(2, SourceEndpoint.NOTICE_LIST.name)
                statement.setString(3, "{}")
                statement.setTimestamp(4, Timestamp.from(FIXED_INSTANT))
                statement.setString(5, "test-release")
                statement.executeUpdate()
            }
        }
        JdbcNoticeRepository(dataSource()).persist(emptyBaseAmountCommand, key) shouldBe PersistOutcome.Inserted

        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection
                    .prepareStatement(
                        "UPDATE notice SET base_amount_won = 999 WHERE notice_number = ? AND notice_round = ?",
                    ).use { statement ->
                        statement.setString(1, noticeId.number.value)
                        statement.setString(2, noticeId.round.value)
                        statement.executeUpdate()
                    }
            }
            connection.rollback()
        }
    }

    @Test
    fun `Kotlin write 규칙은 같은 데이터를 읽어 미리 거른다 — persist 경로는 애초에 UPDATE 를 시도하지 않는다`() {
        seedAuthoritativeNotice()
        val before = currentBaseAmountWon()
        val rawStore = JdbcRawObservationStore(dataSource(), fieldContracts(), "test-release")
        val secondObservation =
            RawNoticeObservation.of(
                mapOf(
                    RawKey("bidNtceNo") to noticeId.number.value,
                    RawKey("bidNtceOrd") to noticeId.round.value,
                    RawKey("marker") to "second",
                ),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-07T01:00:00Z"),
            )
        val secondKey = rawStore.append(secondObservation)
        val downgradedCommand =
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount =
                    ResolvedBaseAmount.FallbackFromBudget(
                        RawKey("asignBdgtAmt"),
                        BaseAmount(
                            1L,
                            Currency.KRW,
                            VatTreatment.UNKNOWN,
                            Provenance.FilledFromBudgetKey("asignBdgtAmt"),
                        ),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = secondObservation,
            )

        val outcome = JdbcNoticeRepository(dataSource()).persist(downgradedCommand, secondKey)

        outcome shouldBe PersistOutcome.Unchanged
        currentBaseAmountWon() shouldBe before
    }
}
