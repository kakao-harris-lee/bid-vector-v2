package bidvector.adapters.persistence

import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.ResolvedEstimatedAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
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

/**
 * P-1(verifier r3, medium) 재현 — 가드가 값·provenance kind·통화·과세(N-2)까지는 물지만
 * provenance의 **detail 절반**(`*_provenance_detail`)·`estimated_amount_source_key`·
 * `floor_rate_origin_kind`/`_detail`은 같은 observation_key로 자유롭게 위조할 수 있었다.
 * `PrecedenceMutationTest`에서 분리한 파일(sizeGate 500줄, v2-지침서.md §5).
 *
 * `Provenance.Published`를 쓰는 이유 — provenance kind 중 detail이 non-null인 것은
 * `Published`(노출된 revision)와 `FilledFromBudgetKey`(예산 키)뿐인데, 후자는 비권위라
 * 축을 조금이라도 건드리면 점유 가드가 먼저 막는다(이 test가 노리는 「detail만 위조」를
 * 격리해서 재지 못한다). `Published`는 권위 있으면서 detail도 있어 이 격리가 가능하다.
 */
private val FIXED_INSTANT: Instant = Instant.parse("2026-09-07T00:00:00Z")

class PrecedenceLabelColumnTest : PersistenceTestSupport() {
    private val noticeId = NoticeId(NoticeNumber.of("PLC-20260907-001"), NoticeRound.of("000"))

    // observedAt이 ObservationKey 유도 재료다(ObservationKeyDerivation) — 등재 안 된
    // raw key("marker" 등)만 다르면 canonicalPayload가 안 바뀌어 같은 키로 접힌다.
    // 재-fold를 재현하려면 observedAt 자체를 다르게 줘야 한다.
    private fun rawObservation(observedAt: Instant = FIXED_INSTANT): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(
                RawKey("bidNtceNo") to noticeId.number.value,
                RawKey("bidNtceOrd") to noticeId.round.value,
            ),
            SourceEndpoint.NOTICE_LIST,
            observedAt,
        )

    private fun seed(command: (RawNoticeObservation) -> NoticeCollected): ObservationKey {
        val observation = rawObservation()
        val key = appendRawObservation(observation)
        JdbcNoticeRepository(dataSource()).persist(command(observation), key) shouldBe PersistOutcome.Inserted
        return key
    }

    private fun forgeColumn(
        column: String,
        newValue: String,
    ) {
        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection
                    .prepareStatement("UPDATE notice SET $column = ? WHERE notice_number = ? AND notice_round = ?")
                    .use { statement ->
                        statement.setString(1, newValue)
                        statement.setString(2, noticeId.number.value)
                        statement.setString(3, noticeId.round.value)
                        statement.executeUpdate()
                    }
            }
            connection.rollback()
        }
    }

    @Test
    fun `P-1 재현 — base_amount_provenance_detail 만 위조하면 거부된다`() {
        seed { observation ->
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        1_000_000L,
                        Currency.KRW,
                        VatTreatment.UNKNOWN,
                        Provenance.Published(noticeId.round),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )
        }

        forgeColumn("base_amount_provenance_detail", "999")
    }

    @Test
    fun `P-1 재현 — estimated_amount_provenance_detail 과 source_key 를 위조하면 거부된다`() {
        seed { observation ->
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount =
                    ResolvedEstimatedAmount(
                        RawKey("presmptPrce"),
                        EstimatedAmount(
                            2_000_000L,
                            Currency.KRW,
                            VatTreatment.UNKNOWN,
                            Provenance.Published(noticeId.round),
                        ),
                    ),
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )
        }

        forgeColumn("estimated_amount_provenance_detail", "999")
        forgeColumn("estimated_amount_source_key", "forgedKey")
    }

    @Test
    fun `P-1 재현 — allocated_budget_provenance_detail 만 위조하면 거부된다`() {
        seed { observation ->
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = AllocatedBudget(700L, Currency.KRW, Provenance.Published(noticeId.round)),
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )
        }

        forgeColumn("allocated_budget_provenance_detail", "999")
    }

    @Test
    fun `P-1 재현 — floor_rate_origin_kind 와 _detail 을 위조하면 거부된다`() {
        seed { observation ->
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate =
                    FloorRate(Rate.ofFraction(BigDecimal("0.876")), FloorRateOrigin.NoticeValue(noticeId.round)),
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )
        }

        forgeColumn("floor_rate_origin_kind", "STATUTORY_TABLE")
        forgeColumn("floor_rate_origin_detail", "INITIAL")
    }

    @Test
    fun `정상 fold 에서 라벨(provenance detail) 변경은 새 관측과 함께라면 통과한다`() {
        seed { observation ->
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        1_000_000L,
                        Currency.KRW,
                        VatTreatment.UNKNOWN,
                        Provenance.Published(NoticeRound.of("000")),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )
        }

        // 값은 그대로, provenance detail(noticeRevision)만 "000"→"001"로 바뀐 재관측 — 새
        // observation_key(다른 observedAt)를 동반하면 통과해야 한다(권위는 그대로 PUBLISHED).
        val secondObservation = rawObservation(Instant.parse("2026-09-07T01:00:00Z"))
        val secondKey = appendRawObservation(secondObservation)
        val refolded =
            NoticeCollected(
                id = noticeId,
                businessCategory = null,
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        1_000_000L,
                        Currency.KRW,
                        VatTreatment.UNKNOWN,
                        Provenance.Published(NoticeRound.of("001")),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = secondObservation,
            )

        JdbcNoticeRepository(dataSource()).persist(refolded, secondKey) shouldBe PersistOutcome.Updated(2L)
    }
}
