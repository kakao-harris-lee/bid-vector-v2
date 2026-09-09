package bidvector.adapters.persistence

import bidvector.procurement.DrawNumberObservation
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.ObservedBidAmount
import bidvector.procurement.OpeningRankOneBid
import bidvector.procurement.OpeningRankOneOutcome
import bidvector.procurement.OpeningResult
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Rate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.math.BigDecimal
import java.time.Instant

/**
 * M3/3F D-3F-4 — 개찰완료 축 부모 슬롯(`opening_rank_one_*`·`draw_numbers_*`, V5) 왕복 통합
 * test. 3E `OpeningReservePriceRepositoryTest` 옆의 신규 파일이다(3B·3B-2·3C·3D·3E 기존 test
 * 는 편집하지 않는다) — bypass #6(사업자등록번호·대표자명 컬럼 부재)은 그 파일의 기존 test
 * 가 이 표 전체 컬럼을 스캔해 이미 겸한다.
 */
class OpeningCompleteAxisRepositoryTest : PersistenceTestSupport() {
    private val id = NoticeId(NoticeNumber.of("OPENG-20260909-001"), NoticeRound.of("000"))

    private fun appendRaw(observedAt: Instant): ObservationKey {
        val observation = RawNoticeObservation.of(emptyMap(), SourceEndpoint.OPENING_RESULT, observedAt)
        return appendRawObservation(observation)
    }

    @Test
    fun `기본값(NotObserved 둘)은 그대로 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val result = OpeningResult(id, null, null, observedAt)

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.openingRankOne shouldBe OpeningRankOneOutcome.NotObserved
        found.drawNumbers shouldBe DrawNumberObservation.NotObserved
    }

    @Test
    fun `Determined 개찰 1위 축(투찰금액 투찰율)이 그대로 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val bid =
            OpeningRankOneBid(
                bidderName = "SYN-WINNER",
                bidAmount = ObservedBidAmount(950_000_000L, Currency.KRW),
                bidRate = Rate.ofPercent(BigDecimal("87.995")),
            )
        val result = OpeningResult(id, null, null, observedAt, openingRankOne = OpeningRankOneOutcome.Determined(bid))

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.openingRankOne shouldBe OpeningRankOneOutcome.Determined(bid)
    }

    /** 협상 계약 — 투찰금액·투찰율이 없어도 상호·평가점수는 값으로 남는다(scope.md ⑦). */
    @Test
    fun `협상 계약 Determined 는 투찰금액 투찰율 없이도 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val bid = OpeningRankOneBid(bidderName = "SYN-NEGOTIATED", bidAmount = null, bidRate = null)
        val result = OpeningResult(id, null, null, observedAt, openingRankOne = OpeningRankOneOutcome.Determined(bid))

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        val determined = found.openingRankOne.shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
        determined.bid.bidderName shouldBe "SYN-NEGOTIATED"
        determined.bid.bidAmount shouldBe null
        determined.bid.bidRate shouldBe null
    }

    @Test
    fun `RankMissing 은 순위 1 부재로 왕복된다 — 투찰금액으로 재계산하지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val result = OpeningResult(id, null, null, observedAt, openingRankOne = OpeningRankOneOutcome.RankMissing)

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        requireNotNull(repository.find(id)).openingRankOne shouldBe OpeningRankOneOutcome.RankMissing
    }

    @Test
    fun `RankDuplicated 는 건수와 함께 왕복된다 — 동값이 실재한다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val result =
            OpeningResult(id, null, null, observedAt, openingRankOne = OpeningRankOneOutcome.RankDuplicated(3))

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        requireNotNull(repository.find(id)).openingRankOne shouldBe OpeningRankOneOutcome.RankDuplicated(3)
    }

    @Test
    fun `Verified 추첨번호 집합이 그대로 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val result =
            OpeningResult(
                id,
                null,
                null,
                observedAt,
                totalReservePriceCandidateCount = 15,
                drawNumbers = DrawNumberObservation.Verified(setOf(3, 7)),
            )

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        requireNotNull(repository.find(id)).drawNumbers shouldBe DrawNumberObservation.Verified(setOf(3, 7))
    }

    /**
     * 범위 밖 — DB 는 번호 원소만 싣고 `validRange`는 이미 부모에 있는
     * `totalReservePriceCandidateCount`(3E 슬롯)에서 재구성한다(중복 저장 금지).
     */
    @Test
    fun `OutOfRange 는 총예가건수에서 validRange 를 재구성해 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val result =
            OpeningResult(
                id,
                null,
                null,
                observedAt,
                totalReservePriceCandidateCount = 15,
                drawNumbers = DrawNumberObservation.OutOfRange(setOf(3, 16), 1..15),
            )

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.drawNumbers shouldBe DrawNumberObservation.OutOfRange(setOf(3, 16), 1..15)
    }

    @Test
    fun `RangeCheckUnavailable 은 총예가건수 없이도 번호와 함께 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val result =
            OpeningResult(
                id,
                null,
                null,
                observedAt,
                totalReservePriceCandidateCount = null,
                drawNumbers = DrawNumberObservation.RangeCheckUnavailable(setOf(3, 7)),
            )

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.drawNumbers shouldBe DrawNumberObservation.RangeCheckUnavailable(setOf(3, 7))
        found.totalReservePriceCandidateCount shouldBe null
    }

    /**
     * COALESCE 관례(설계 검토 ④) — 뒤 관측이 개찰 1위 축만 실으면 앞서 저장된 추첨번호
     * 축을 지우지 않는다(부분 관측이 정상, 3E M-c 와 같은 관례를 이 축도 잇는다).
     */
    @Test
    fun `한 축만 싣는 뒤 관측은 다른 축을 지우지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val firstObservedAt = Instant.parse("2026-09-09T00:00:00Z")
        val first =
            OpeningResult(
                id,
                null,
                null,
                firstObservedAt,
                totalReservePriceCandidateCount = 15,
                drawNumbers = DrawNumberObservation.Verified(setOf(3, 7)),
            )
        repository.persist(first, appendRaw(firstObservedAt)) shouldBe PersistOutcome.Inserted

        val secondObservedAt = Instant.parse("2026-09-09T01:00:00Z")
        val bid = OpeningRankOneBid(bidderName = "SYN-WINNER")
        val second =
            OpeningResult(
                id,
                null,
                null,
                secondObservedAt,
                openingRankOne = OpeningRankOneOutcome.Determined(bid),
            )
        repository.persist(second, appendRaw(secondObservedAt)) shouldBe PersistOutcome.Updated(2L)

        val found = requireNotNull(repository.find(id))
        found.openingRankOne shouldBe OpeningRankOneOutcome.Determined(bid)
        found.drawNumbers shouldBe DrawNumberObservation.Verified(setOf(3, 7))
    }

    /** guard_opening_result_opening_rank_one — 같은 observation_key 로 값만 바꾸는 직접 SQL은 거부된다. */
    @Test
    fun `opening_rank_one_kind 를 같은 observation_key 로 바꾸는 직접 SQL은 거부된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-09T00:00:00Z")
        val bid = OpeningRankOneBid(bidderName = "SYN-WINNER")
        val result =
            OpeningResult(id, null, null, observedAt, openingRankOne = OpeningRankOneOutcome.Determined(bid))
        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        shouldThrow<PSQLException> {
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "UPDATE opening_result SET opening_rank_one_kind = 'RANK_MISSING', " +
                            "opening_rank_one_bidder_name = NULL WHERE notice_number = ? AND notice_round = ?",
                    ).use { statement ->
                        statement.setString(1, id.number.value)
                        statement.setString(2, id.round.value)
                        statement.executeUpdate()
                    }
            }
        }
    }
}
