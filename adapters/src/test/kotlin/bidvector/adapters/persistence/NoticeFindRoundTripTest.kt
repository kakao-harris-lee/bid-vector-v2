package bidvector.adapters.persistence

import bidvector.procurement.Agency
import bidvector.procurement.AgencyCode
import bidvector.procurement.AgencyName
import bidvector.procurement.BusinessCategory
import bidvector.procurement.CategoryCode
import bidvector.procurement.CategoryLabel
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeTitle
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
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * verifier r1 F-8 — 금액·범주·마감이 실린 notice의 `find()` 왕복을 단언하는 test가 없었다.
 * 저장한 값과 `find()`로 다시 읽은 값이 필드별로 정확히 같은지(재구성이 손실·왜곡 없이
 * 되는지)를 여기서 고정한다.
 */
class NoticeFindRoundTripTest : PersistenceTestSupport() {
    @Test
    fun `금액·범주·마감이 모두 실린 notice 를 저장하고 find 하면 그대로 복원된다`() {
        val id = NoticeId(NoticeNumber.of("FIND-ROUNDTRIP-001"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-07T00:00:00Z"),
            )
        val key = appendRawObservation(observation)
        val deadline = Instant.parse("2026-10-01T09:00:00Z")
        val command =
            NoticeCollected(
                id = id,
                businessCategory = BusinessCategory(CategoryCode.of("0411"), CategoryLabel("기술용역")),
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        1_234_000L,
                        Currency.KRW,
                        VatTreatment.EXCLUSIVE,
                        Provenance.Published(id.round),
                    ),
                estimatedAmount =
                    ResolvedEstimatedAmount(
                        RawKey("presmptPrce"),
                        EstimatedAmount(
                            2_345_000L,
                            Currency.KRW,
                            VatTreatment.EXCLUSIVE,
                            Provenance.Published(id.round),
                        ),
                    ),
                allocatedBudget = AllocatedBudget(3_456_000L, Currency.KRW, Provenance.Published(id.round)),
                floorRate = FloorRate(Rate.ofPercent(BigDecimal("87.995")), FloorRateOrigin.NoticeValue(id.round)),
                deadlineAt = deadline,
                openingScheduledAt = null,
                raw = observation,
            )

        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(JdbcNoticeRepository(dataSource()).find(id))

        found.id shouldBe id
        found.businessCategory shouldBe BusinessCategory(CategoryCode.of("0411"), CategoryLabel("기술용역"))
        found.baseAmount shouldBe command.baseAmount
        found.estimatedAmount shouldBe command.estimatedAmount
        found.allocatedBudget shouldBe command.allocatedBudget
        found.floorRate shouldBe command.floorRate
        found.deadlineAt shouldBe deadline
    }

    /** D-3H-4 — 발주기관 넷 저장·복원, 원문 이름 보존(우회 (7)). */
    @Test
    fun `수요기관·공고기관이 실린 notice 를 저장하고 find 하면 그대로 복원된다`() {
        val id = NoticeId(NoticeNumber.of("FIND-ROUNDTRIP-AGENCY-001"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-16T00:00:00Z"),
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
                demandAgency = Agency(AgencyCode.of("1234567"), AgencyName.of("  수요 기관  ")),
                noticeAgency = Agency(AgencyCode.of("7654321"), AgencyName.of("공고기관")),
            )

        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(JdbcNoticeRepository(dataSource()).find(id))

        found.demandAgency shouldBe Agency(AgencyCode.of("1234567"), AgencyName.of("수요 기관"))
        found.noticeAgency shouldBe Agency(AgencyCode.of("7654321"), AgencyName.of("공고기관"))
    }

    /** D-3H-4 — 발주기관 키가 결측이면 null 이 왕복된다(지어내지 않는다). */
    @Test
    fun `발주기관이 없는 notice 는 null 이 그대로 왕복된다`() {
        val id = NoticeId(NoticeNumber.of("FIND-ROUNDTRIP-AGENCY-002"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-16T00:00:00Z"),
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )

        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(JdbcNoticeRepository(dataSource()).find(id))

        found.demandAgency shouldBe null
        found.noticeAgency shouldBe null
    }

    /** D-6F4-9 — 공고명 저장·복원, 원문 흔들림 보존(trim만, [NoticeTitle]과 같은 관례). */
    @Test
    fun `공고명이 실린 notice 를 저장하고 find 하면 그대로 복원된다`() {
        val id = NoticeId(NoticeNumber.of("FIND-ROUNDTRIP-TITLE-001"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-18T00:00:00Z"),
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
                title = NoticeTitle.of("  2026년 정보시스템 유지보수 용역  "),
            )

        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(JdbcNoticeRepository(dataSource()).find(id))

        found.title shouldBe NoticeTitle.of("2026년 정보시스템 유지보수 용역")
    }

    /** D-6F4-9·8 — 공고명이 없으면 null 이 그대로 왕복된다(지어내지 않는다). */
    @Test
    fun `공고명이 없는 notice 는 null 이 그대로 왕복된다`() {
        val id = NoticeId(NoticeNumber.of("FIND-ROUNDTRIP-TITLE-002"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-18T00:00:00Z"),
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )

        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(JdbcNoticeRepository(dataSource()).find(id))

        found.title shouldBe null
    }

    private val mergeGuardId = NoticeId(NoticeNumber.of("MERGE-AGENCY-001"), NoticeRound.of("000"))

    private fun mergeGuardObservation(
        marker: String,
        observedAt: Instant,
    ): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(
                RawKey("bidNtceNo") to mergeGuardId.number.value,
                RawKey("bidNtceOrd") to mergeGuardId.round.value,
                RawKey("marker") to marker,
            ),
            SourceEndpoint.NOTICE_LIST,
            observedAt,
        )

    private fun mergeGuardCommand(
        raw: RawNoticeObservation,
        demandAgency: Agency?,
        noticeAgency: Agency?,
    ): NoticeCollected =
        NoticeCollected(
            id = mergeGuardId,
            businessCategory = null,
            baseAmount = null,
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = null,
            openingScheduledAt = null,
            raw = raw,
            demandAgency = demandAgency,
            noticeAgency = noticeAgency,
        )

    /**
     * verifier r1 F-1 — `NoticeRowMerge`의 발주기관 존재 가드(「유입이 없으면 기존을 지킨다」,
     * `business_category_*`와 같은 형태)가 실제 재수집 흐름에서 성립하는지 3단계로 잠근다.
     * ① 기관 넷이 실린 insert ② 기관이 결측인 재관측 — 존재 가드가 기존 값을 지켜 다른
     * 필드도 안 바뀌므로 `Unchanged`(SQL write 자체가 없다 — 가드가 없으면 컬럼이 null 로
     * 덮여 merged != existing 이 되어 Updated 로 갈린다, 이 단언 자체가 가드의 증거다) ③
     * 수요기관만 새 값이 실린 재관측 — 수요기관만 교체되고 공고기관은 그대로다(역할 간
     * 누출 0, 우회 (3)과 같은 결의 persistence 층 대응).
     */
    @Test
    fun `발주기관은 결측 재관측에 지워지지 않고 값 있는 재관측에만 교체된다 — 역할 간 누출 0`() {
        val repository = JdbcNoticeRepository(dataSource())
        val demand = Agency(AgencyCode.of("1111111"), AgencyName.of("수요기관"))
        val notice = Agency(AgencyCode.of("2222222"), AgencyName.of("공고기관"))

        val firstObservation = mergeGuardObservation("first", Instant.parse("2026-09-17T00:00:00Z"))
        val firstOutcome =
            repository.persist(
                mergeGuardCommand(firstObservation, demand, notice),
                appendRawObservation(firstObservation),
            )
        firstOutcome shouldBe PersistOutcome.Inserted

        val secondObservation = mergeGuardObservation("second", Instant.parse("2026-09-17T01:00:00Z"))
        val secondOutcome =
            repository.persist(
                mergeGuardCommand(secondObservation, null, null),
                appendRawObservation(secondObservation),
            )
        secondOutcome shouldBe PersistOutcome.Unchanged
        val afterSecond = requireNotNull(repository.find(mergeGuardId))
        afterSecond.demandAgency shouldBe demand
        afterSecond.noticeAgency shouldBe notice

        val newDemand = Agency(AgencyCode.of("3333333"), AgencyName.of("수요기관B"))
        val thirdObservation = mergeGuardObservation("third", Instant.parse("2026-09-17T02:00:00Z"))
        val thirdOutcome =
            repository.persist(
                mergeGuardCommand(thirdObservation, newDemand, null),
                appendRawObservation(thirdObservation),
            )
        thirdOutcome shouldBe PersistOutcome.Updated(2L)
        val afterThird = requireNotNull(repository.find(mergeGuardId))
        afterThird.demandAgency shouldBe newDemand
        afterThird.noticeAgency shouldBe notice
    }
}
