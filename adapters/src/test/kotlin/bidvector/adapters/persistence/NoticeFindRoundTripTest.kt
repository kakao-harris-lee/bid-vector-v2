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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
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

    /**
     * verifier r2 MEDIUM-1 — V11 CHECK(공백류 정규식으로 교체됨, V4의 `reserve_price_sequence`
     * 선례와 같은 형태)와 `NoticeTitle.of`가 **같은 입력 집합에 같은 답**을 낸다. 빈 문자열뿐
     * 아니라 공백류(ASCII 공백·탭·개행·NBSP·전각 공백)도 DB 직접 UPDATE 로는 거부되고,
     * `NoticeTitle.of`도 같은 값에 `null`을 낸다 — 저장은 non-null 인데 복원은 부재인
     * 비대칭(verifier r2 실측)을 재현하지 않는다.
     */
    @Test
    fun `notice_title CHECK 와 NoticeTitle of 는 공백 판정이 일치한다`() {
        val id = NoticeId(NoticeNumber.of("TITLE-CHECK-001"), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-19T00:00:00Z"),
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

        val rejected =
            listOf(
                "" to "빈 문자열",
                " " to "ASCII 공백",
                "\t" to "탭",
                "\n" to "개행",
                " " to "NBSP",
                "　" to "전각 공백",
            )
        for ((value, label) in rejected) {
            // DB — 직접 UPDATE 가 거부된다. label 은 실패 시 어떤 입력인지 보여주는 표식이다.
            shouldThrow<PSQLException> { updateNoticeTitleDirect(id, value) }
            // Kotlin — 같은 값은 NoticeTitle.of 에서 null 이다(같은 판정을 공유).
            NoticeTitle.of(value) shouldBe null
        }

        // DB — 공백 하나라도 아닌 문자가 있으면 UPDATE 가 성공한다.
        updateNoticeTitleDirect(id, "정상 공고명")
        // Kotlin — 같은 값으로 타입이 만들어진다(거부되지 않는다).
        NoticeTitle.of("정상 공고명") shouldBe NoticeTitle.of("정상 공고명")
    }

    private fun updateNoticeTitleDirect(
        id: NoticeId,
        value: String,
    ) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("UPDATE notice SET notice_title = ? WHERE notice_number = ? AND notice_round = ?")
                .use { statement ->
                    statement.setString(1, value)
                    statement.setString(2, id.number.value)
                    statement.setString(3, id.round.value)
                    statement.executeUpdate()
                }
        }
    }

    private val mergeGuardId = NoticeId(NoticeNumber.of("MERGE-AGENCY-001"), NoticeRound.of("000"))
    private val mergeGuardTitleId = NoticeId(NoticeNumber.of("MERGE-TITLE-001"), NoticeRound.of("000"))

    private fun mergeGuardObservation(
        id: NoticeId,
        marker: String,
        observedAt: Instant,
    ): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(
                RawKey("bidNtceNo") to id.number.value,
                RawKey("bidNtceOrd") to id.round.value,
                RawKey("marker") to marker,
            ),
            SourceEndpoint.NOTICE_LIST,
            observedAt,
        )

    private fun mergeGuardCommand(
        id: NoticeId,
        raw: RawNoticeObservation,
        demandAgency: Agency? = null,
        noticeAgency: Agency? = null,
        title: NoticeTitle? = null,
    ): NoticeCollected =
        NoticeCollected(
            id = id,
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
            title = title,
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

        val firstObservation = mergeGuardObservation(mergeGuardId, "first", Instant.parse("2026-09-17T00:00:00Z"))
        val firstOutcome =
            repository.persist(
                mergeGuardCommand(mergeGuardId, firstObservation, demandAgency = demand, noticeAgency = notice),
                appendRawObservation(firstObservation),
            )
        firstOutcome shouldBe PersistOutcome.Inserted

        val secondObservation = mergeGuardObservation(mergeGuardId, "second", Instant.parse("2026-09-17T01:00:00Z"))
        val secondOutcome =
            repository.persist(
                mergeGuardCommand(mergeGuardId, secondObservation),
                appendRawObservation(secondObservation),
            )
        secondOutcome shouldBe PersistOutcome.Unchanged
        val afterSecond = requireNotNull(repository.find(mergeGuardId))
        afterSecond.demandAgency shouldBe demand
        afterSecond.noticeAgency shouldBe notice

        val newDemand = Agency(AgencyCode.of("3333333"), AgencyName.of("수요기관B"))
        val thirdObservation = mergeGuardObservation(mergeGuardId, "third", Instant.parse("2026-09-17T02:00:00Z"))
        val thirdOutcome =
            repository.persist(
                mergeGuardCommand(mergeGuardId, thirdObservation, demandAgency = newDemand),
                appendRawObservation(thirdObservation),
            )
        thirdOutcome shouldBe PersistOutcome.Updated(2L)
        val afterThird = requireNotNull(repository.find(mergeGuardId))
        afterThird.demandAgency shouldBe newDemand
        afterThird.noticeAgency shouldBe notice
    }

    /**
     * verifier r2 MEDIUM-2 — `NoticeRowMerge`의 공고명 존재 가드(`title = incomingRow.title
     * ?: existing.title`)가 발주기관 축과 같은 형태이지만 그 대응 test가 없었다. 같은 3단계
     * 형태로 잠근다: ① 공고명이 실린 insert ② 결측 재관측 — 존재 가드가 기존 값을 지켜
     * `Unchanged`(가드가 없으면 title 이 null 로 덮여 merged != existing 이 되어 Updated로
     * 갈린다 — 이 단언 자체가 가드의 증거) ③ 새 공고명이 실린 재관측 — 값이 교체된다. verifier
     * 가 실측한 변이(`title = existing.title`로 존재 가드를 지워도 `:adapters:test` 전건이
     * 초록이던 것)는 이 test 가 있으면 ②·③ 단계에서 붉어진다.
     */
    @Test
    fun `공고명은 결측 재관측에 지워지지 않고 값 있는 재관측에만 교체된다`() {
        val repository = JdbcNoticeRepository(dataSource())
        val firstTitle = requireNotNull(NoticeTitle.of("정보시스템 유지보수 용역"))

        val firstObservation =
            mergeGuardObservation(mergeGuardTitleId, "first", Instant.parse("2026-09-19T00:00:00Z"))
        val firstOutcome =
            repository.persist(
                mergeGuardCommand(mergeGuardTitleId, firstObservation, title = firstTitle),
                appendRawObservation(firstObservation),
            )
        firstOutcome shouldBe PersistOutcome.Inserted

        val secondObservation =
            mergeGuardObservation(mergeGuardTitleId, "second", Instant.parse("2026-09-19T01:00:00Z"))
        val secondOutcome =
            repository.persist(
                mergeGuardCommand(mergeGuardTitleId, secondObservation),
                appendRawObservation(secondObservation),
            )
        secondOutcome shouldBe PersistOutcome.Unchanged
        requireNotNull(repository.find(mergeGuardTitleId)).title shouldBe firstTitle

        val newTitle = requireNotNull(NoticeTitle.of("정보시스템 유지보수 용역(정정)"))
        val thirdObservation =
            mergeGuardObservation(mergeGuardTitleId, "third", Instant.parse("2026-09-19T02:00:00Z"))
        val thirdOutcome =
            repository.persist(
                mergeGuardCommand(mergeGuardTitleId, thirdObservation, title = newTitle),
                appendRawObservation(thirdObservation),
            )
        thirdOutcome shouldBe PersistOutcome.Updated(2L)
        requireNotNull(repository.find(mergeGuardTitleId)).title shouldBe newTitle
    }
}
