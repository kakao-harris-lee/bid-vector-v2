package bidvector.adapters.persistence

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
import bidvector.procurement.mayOverwrite
import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.math.BigDecimal
import java.sql.Connection
import java.time.Instant

/**
 * S-3 — 파생/비권위 provenance write가 권위 자리를 덮는 mutation이 **DB 층에서** 거부된다
 * (④, D-3D-2 (a)). Kotlin 규칙([JdbcNoticeRepository])을 거치지 않은 **직접 SQL**로 우회를
 * 시도한다 — 애플리케이션 역할(`SET ROLE bidvector_app`)과 superuser 둘 다로 시도해, 트리거가
 * 역할과 무관하게(단, superuser의 트리거 비활성화 같은 DDL 권한 우회는 경계 밖) 거부함을
 * 증명한다.
 *
 * **verifier r1 뒤 개정** — F-1(값만 바뀌고 provenance는 그대로인 write가 통과)·F-4(비권위→
 * 비권위 write가 통과)를 닫는 트리거 개정(V2)에 맞춰 정확한 재현 test를 추가한다.
 */
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-07T00:00:00Z")

class PrecedenceMutationTest : PersistenceTestSupport() {
    private val noticeId = NoticeId(NoticeNumber.of("PMT-20260907-001"), NoticeRound.of("000"))

    /** authoritative(Published) base_amount를 가진 notice 행 하나를 심는다. */
    private fun seedAuthoritativeNotice(): ObservationKey {
        val rawObservation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to noticeId.number.value, RawKey("bidNtceOrd") to noticeId.round.value),
                SourceEndpoint.NOTICE_LIST,
                FIXED_INSTANT,
            )
        val key = appendRawObservation(rawObservation)
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

    /** F-1의 정확한 재현 — provenance는 손대지 않고 값만 바꾼다(observation_key도 그대로). */
    private fun attemptValueOnlyChange(connection: Connection) {
        val sql = "UPDATE notice SET base_amount_won = 1 WHERE notice_number = ? AND notice_round = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, noticeId.number.value)
            statement.setString(2, noticeId.round.value)
            statement.executeUpdate()
        }
    }

    /** N-1의 정확한 재현 — 값은 손대지 않고 provenance만 강등한다(observation_key도 그대로). */
    private fun attemptProvenanceOnlyDowngrade(connection: Connection) {
        val sql = "UPDATE notice SET base_amount_provenance = ? WHERE notice_number = ? AND notice_round = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, ProvenanceKind.UNDECLARED.name)
            statement.setString(2, noticeId.number.value)
            statement.setString(3, noticeId.round.value)
            statement.executeUpdate()
        }
    }

    private fun currentBaseAmountProvenance(): String {
        val sql = "SELECT base_amount_provenance FROM notice WHERE notice_number = ? AND notice_round = ?"
        return dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, noticeId.number.value)
                statement.setString(2, noticeId.round.value)
                statement.executeQuery().use { rs ->
                    rs.next()
                    requireNotNull(rs.getString("base_amount_provenance"))
                }
            }
        }
    }

    @Test
    fun `F-1 재현 — provenance 를 그대로 둔 채 금액만 바꾸는 직접 SQL 은 새 관측이 없어 거부된다`() {
        seedAuthoritativeNotice()
        val before = currentBaseAmountWon()

        appConnection().use { connection ->
            shouldThrow<PSQLException> { attemptValueOnlyChange(connection) }
            connection.rollback()
        }

        currentBaseAmountWon() shouldBe before
    }

    @Test
    fun `N-1 재현(회귀) — 값은 그대로 두고 provenance 만 강등하는 직접 SQL 은 거부된다`() {
        seedAuthoritativeNotice()
        val beforeValue = currentBaseAmountWon()
        val beforeProvenance = currentBaseAmountProvenance()

        appConnection().use { connection ->
            shouldThrow<PSQLException> { attemptProvenanceOnlyDowngrade(connection) }
            connection.rollback()
        }

        currentBaseAmountWon() shouldBe beforeValue
        currentBaseAmountProvenance() shouldBe beforeProvenance
    }

    @Test
    fun `값·provenance 둘 다 불변인 wide UPDATE 는 이 축을 건드리지 않아 통과한다`() {
        seedAuthoritativeNotice()
        val beforeValue = currentBaseAmountWon()
        val beforeProvenance = currentBaseAmountProvenance()

        appConnection().use { connection ->
            connection
                .prepareStatement(
                    "UPDATE notice SET base_amount_won = base_amount_won, " +
                        "base_amount_provenance = base_amount_provenance, business_category_code = 'X' " +
                        "WHERE notice_number = ? AND notice_round = ?",
                ).use { statement ->
                    statement.setString(1, noticeId.number.value)
                    statement.setString(2, noticeId.round.value)
                    statement.executeUpdate()
                }
            connection.commit()
        }

        currentBaseAmountWon() shouldBe beforeValue
        currentBaseAmountProvenance() shouldBe beforeProvenance
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

    private fun seedFilledFromBudgetKeyNotice(): ObservationKey {
        val rawObservation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to noticeId.number.value, RawKey("bidNtceOrd") to noticeId.round.value),
                SourceEndpoint.NOTICE_LIST,
                FIXED_INSTANT,
            )
        val key = appendRawObservation(rawObservation)
        val amount =
            BaseAmount(900L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.FilledFromBudgetKey("asignBdgtAmt"))
        val command =
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount = ResolvedBaseAmount.FallbackFromBudget(RawKey("asignBdgtAmt"), amount),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = rawObservation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return key
    }

    @Test
    fun `F-4 재현 — 비권위 값을 다른 비권위 값으로 직접 SQL 로 덮는 것도 거부된다`() {
        // FILLED_FROM_BUDGET_KEY(비권위)로 처음 채운 뒤, DERIVED_FROM_OPENING(역시 비권위)으로
        // 직접 SQL 덮어쓰기를 시도한다 — Kotlin mayOverwrite도 이 조합을 거부한다(existing !=
        // null이면 오직 incoming이 권위 있을 때만 허용, existing의 권위 여부는 보지 않는다).
        seedFilledFromBudgetKeyNotice()
        val before = currentBaseAmountWon()

        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection
                    .prepareStatement(
                        "UPDATE notice SET base_amount_won = 1, base_amount_provenance = ? " +
                            "WHERE notice_number = ? AND notice_round = ?",
                    ).use { statement ->
                        statement.setString(1, ProvenanceKind.DERIVED_FROM_OPENING.name)
                        statement.setString(2, noticeId.number.value)
                        statement.setString(3, noticeId.round.value)
                        statement.executeUpdate()
                    }
            }
            connection.rollback()
        }

        currentBaseAmountWon() shouldBe before
    }

    @Test
    fun `F-4 재현 — Kotlin mayOverwrite 와 DB 가드는 모든 provenance 쌍에서 같은 결정을 낸다`() {
        // allocated_budget 축은 provenance에 제약이 없어(shared-kernel AllocatedBudget) 6종
        // ProvenanceKind 전부를 구성할 수 있다 — Money 세 축(base/estimated/allocated) 중
        // 이 축만 그 전 범위를 직접 시험할 수 있는 자리다.
        val samples: Map<ProvenanceKind, Provenance> =
            mapOf(
                ProvenanceKind.PUBLISHED to Provenance.Published(noticeId.round),
                ProvenanceKind.OPERATOR_DECLARED to Provenance.OperatorDeclared,
                ProvenanceKind.DERIVED_FROM_OPENING to Provenance.DerivedFromOpening,
                ProvenanceKind.FILLED_FROM_BUDGET_KEY to Provenance.FilledFromBudgetKey("asignBdgtAmt"),
                ProvenanceKind.COPIED_FROM_BASE_AMOUNT to Provenance.CopiedFromBaseAmount,
                ProvenanceKind.UNDECLARED to Provenance.Undeclared,
            )
        val existingKinds = listOf(ProvenanceKind.PUBLISHED, ProvenanceKind.UNDECLARED)

        for (existingKind in existingKinds) {
            for ((incomingKind, incomingProvenance) in samples) {
                seedAllocatedBudget(requireNotNull(samples[existingKind]))
                val kotlinAllows = mayOverwrite(requireNotNull(samples[existingKind]), incomingProvenance)

                val dbAllows =
                    try {
                        overwriteAllocatedBudgetDirectly(incomingKind)
                        true
                    } catch (rejected: PSQLException) {
                        // 기대된 거부(가드 트리거 RAISE) — dbAllows=false로 접는다. 메시지는
                        // assertParity 실패 시 원인 추적에 쓰일 수 있어 버리지 않고 로그에 남긴다.
                        System.err.println(
                            "guard rejected existing=$existingKind incoming=$incomingKind: ${rejected.message}",
                        )
                        false
                    }

                assertParity(existingKind, incomingKind, dbAllows, kotlinAllows)
                truncateAllTables()
            }
        }
    }

    private fun assertParity(
        existingKind: ProvenanceKind,
        incomingKind: ProvenanceKind,
        dbAllows: Boolean,
        kotlinAllows: Boolean,
    ) {
        try {
            dbAllows shouldBe kotlinAllows
        } catch (failure: AssertionError) {
            throw AssertionError("existing=$existingKind incoming=$incomingKind 에서 불일치: ${failure.message}", failure)
        }
    }

    private fun seedAllocatedBudget(existingProvenance: Provenance) {
        val rawObservation = RawNoticeObservation.of(emptyMap(), SourceEndpoint.NOTICE_LIST, FIXED_INSTANT)
        val key = appendRawObservation(rawObservation)
        val command =
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = AllocatedBudget(700L, Currency.KRW, existingProvenance),
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = rawObservation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key)
    }

    private fun overwriteAllocatedBudgetDirectly(incomingKind: ProvenanceKind) {
        val fresh =
            appendRawObservation(
                RawNoticeObservation.of(emptyMap(), SourceEndpoint.NOTICE_LIST, Instant.parse("2026-09-07T09:00:00Z")),
            )
        appConnection().use { connection ->
            connection
                .prepareStatement(
                    "UPDATE notice SET allocated_budget_won = 1, " +
                        "allocated_budget_provenance = ?, observation_key = ? " +
                        "WHERE notice_number = ? AND notice_round = ?",
                ).use { statement ->
                    statement.setString(1, incomingKind.name)
                    statement.setString(2, fresh.value)
                    statement.setString(3, noticeId.number.value)
                    statement.setString(4, noticeId.round.value)
                    statement.executeUpdate()
                }
            connection.commit()
        }
    }

    // r1이 지목한 이름 문제(verifier r2 N-7) — 원래 이름은 이 test가 F-1/N-1의 중심 방어를
    // 보여주는 것처럼 읽혔지만, 실제로는 「빈 자리에 provenance 없이 금액만 채우는」 좁은
    // CHECK 불변식 하나만 잰다(진짜 재현은 위 F-1/N-1 전용 test들이 덮는다) — 이름을 실물에
    // 맞춘다.
    @Test
    fun `CHECK 불변식 — 빈 자리에 provenance 없이 금액만 채우는 직접 SQL 은 거부된다`() {
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
        val key = appendRawObservation(emptyBaseAmountCommand.raw)
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
        val secondKey = appendRawObservation(secondObservation)
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
