package bidvector.adapters.persistence

import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ProvenanceKind
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.mayOverwrite
import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.time.Instant

/**
 * F-4 재현의 12쌍 Kotlin/DB parity test — `PrecedenceMutationTest`(sizeGate 500줄 한도,
 * v2-지침서.md §5)에서 분리한 파일. `allocated_budget` 축은 provenance에 제약이 없어
 * (shared-kernel `AllocatedBudget`) 6종 `ProvenanceKind` 전부를 구성할 수 있다 — Money 세
 * 축(base/estimated/allocated) 중 이 축만 그 전 범위를 직접 시험할 수 있는 자리다.
 */
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-07T00:00:00Z")

class PrecedenceParityTest : PersistenceTestSupport() {
    private val noticeId = NoticeId(NoticeNumber.of("PPT-20260907-001"), NoticeRound.of("000"))

    @Test
    fun `F-4 재현 — Kotlin mayOverwrite 와 DB 가드는 모든 provenance 쌍에서 같은 결정을 낸다`() {
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
}
