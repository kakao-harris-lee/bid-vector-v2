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

private val T1: Instant = Instant.parse("2026-09-09T00:00:00Z")
private val T2: Instant = Instant.parse("2026-09-09T01:00:00Z")

/**
 * M3/3F D-3F-4 — 개찰완료 축 부모 슬롯(`opening_rank_one_*`·`draw_numbers_*`, V5) 왕복 통합
 * test. 3E `OpeningReservePriceRepositoryTest` 옆의 신규 파일이다(3B·3B-2·3C·3D·3E 기존 test
 * 는 편집하지 않는다) — bypass #6(사업자등록번호·대표자명 컬럼 부재)은 그 파일의 기존 test
 * 가 이 표 전체 컬럼을 스캔해 이미 겸한다.
 *
 * **verifier r1 F-1·F-2·F-3 뒤** — 전이 test 여섯(F-1 회귀 방지, 재검증 명령 그대로) +
 * 낡음 신호 test 하나(F-2) + 저장 시점 거부 test 하나(F-3)를 더한다.
 */
class OpeningCompleteAxisRepositoryTest : PersistenceTestSupport() {
    private val id = NoticeId(NoticeNumber.of("OPENG-20260909-001"), NoticeRound.of("000"))

    private fun appendRaw(observedAt: Instant): ObservationKey {
        val observation = RawNoticeObservation.of(emptyMap(), SourceEndpoint.OPENING_RESULT, observedAt)
        return appendRawObservation(observation)
    }

    private fun bid(name: String) = OpeningRankOneBid(bidderName = name)

    private fun persistRankOne(
        repository: JdbcOpeningResultRepository,
        outcome: OpeningRankOneOutcome,
        observedAt: Instant,
    ): PersistOutcome {
        val result = OpeningResult(id, null, null, observedAt, openingRankOne = outcome)
        return repository.persist(result, appendRaw(observedAt))
    }

    @Test
    fun `기본값(NotObserved 둘)은 그대로 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val result = OpeningResult(id, null, null, T1)

        repository.persist(result, appendRaw(T1)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.openingRankOne shouldBe OpeningRankOneOutcome.NotObserved
        found.drawNumbers shouldBe DrawNumberObservation.NotObserved
    }

    @Test
    fun `Determined 개찰 1위 축(투찰금액 투찰율)이 그대로 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val theBid =
            OpeningRankOneBid(
                bidderName = "SYN-WINNER",
                bidAmount = ObservedBidAmount(950_000_000L, Currency.KRW),
                bidRate = Rate.ofPercent(BigDecimal("87.995")),
            )

        persistRankOne(repository, OpeningRankOneOutcome.Determined(theBid, T1), T1) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.openingRankOne shouldBe OpeningRankOneOutcome.Determined(theBid, T1)
    }

    /** 협상 계약 — 투찰금액·투찰율이 없어도 상호는 값으로 남는다(scope.md ⑦). */
    @Test
    fun `협상 계약 Determined 는 투찰금액 투찰율 없이도 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val theBid = OpeningRankOneBid(bidderName = "SYN-NEGOTIATED", bidAmount = null, bidRate = null)

        persistRankOne(repository, OpeningRankOneOutcome.Determined(theBid, T1), T1) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        val determined = found.openingRankOne.shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
        determined.bid.bidderName shouldBe "SYN-NEGOTIATED"
        determined.bid.bidAmount shouldBe null
        determined.bid.bidRate shouldBe null
    }

    @Test
    fun `RankMissing 은 순위 1 부재로 왕복된다 — 투찰금액으로 재계산하지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())

        persistRankOne(repository, OpeningRankOneOutcome.RankMissing(T1), T1) shouldBe PersistOutcome.Inserted

        requireNotNull(repository.find(id)).openingRankOne shouldBe OpeningRankOneOutcome.RankMissing(T1)
    }

    @Test
    fun `RankDuplicated 는 건수와 함께 왕복된다 — 동값이 실재한다`() {
        val repository = JdbcOpeningResultRepository(dataSource())

        persistRankOne(repository, OpeningRankOneOutcome.RankDuplicated(3, T1), T1) shouldBe PersistOutcome.Inserted

        requireNotNull(repository.find(id)).openingRankOne shouldBe OpeningRankOneOutcome.RankDuplicated(3, T1)
    }

    @Test
    fun `Verified 추첨번호 집합이 그대로 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val result =
            OpeningResult(
                id,
                null,
                null,
                T1,
                totalReservePriceCandidateCount = 15,
                drawNumbers = DrawNumberObservation.Verified(setOf(3, 7), T1),
            )

        repository.persist(result, appendRaw(T1)) shouldBe PersistOutcome.Inserted

        requireNotNull(repository.find(id)).drawNumbers shouldBe DrawNumberObservation.Verified(setOf(3, 7), T1)
    }

    /**
     * 범위 밖 — DB 는 번호 원소만 싣고 `validRange`는 이미 부모에 있는
     * `totalReservePriceCandidateCount`(3E 슬롯)에서 재구성한다(중복 저장 금지).
     */
    @Test
    fun `OutOfRange 는 총예가건수에서 validRange 를 재구성해 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val result =
            OpeningResult(
                id,
                null,
                null,
                T1,
                totalReservePriceCandidateCount = 15,
                drawNumbers = DrawNumberObservation.OutOfRange(setOf(3, 16), 1..15, T1),
            )

        repository.persist(result, appendRaw(T1)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.drawNumbers shouldBe DrawNumberObservation.OutOfRange(setOf(3, 16), 1..15, T1)
    }

    @Test
    fun `RangeCheckUnavailable 은 총예가건수 없이도 번호와 함께 왕복된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val result =
            OpeningResult(
                id,
                null,
                null,
                T1,
                totalReservePriceCandidateCount = null,
                drawNumbers = DrawNumberObservation.RangeCheckUnavailable(setOf(3, 7), T1),
            )

        repository.persist(result, appendRaw(T1)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.drawNumbers shouldBe DrawNumberObservation.RangeCheckUnavailable(setOf(3, 7), T1)
        found.totalReservePriceCandidateCount shouldBe null
    }

    /**
     * 축 단위 갱신(설계 검토 ④ + verifier r1 F-1) — 뒤 관측이 개찰 1위 축만 실으면 앞서
     * 저장된 추첨번호 축을 지우지 않는다(부분 관측이 정상, 3E M-c 와 같은 관례를 이 축도 잇는다).
     */
    @Test
    fun `한 축만 싣는 뒤 관측은 다른 축을 지우지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val first =
            OpeningResult(
                id,
                null,
                null,
                T1,
                totalReservePriceCandidateCount = 15,
                drawNumbers = DrawNumberObservation.Verified(setOf(3, 7), T1),
            )
        repository.persist(first, appendRaw(T1)) shouldBe PersistOutcome.Inserted

        val theBid = OpeningRankOneBid(bidderName = "SYN-WINNER")
        val second = OpeningResult(id, null, null, T2, openingRankOne = OpeningRankOneOutcome.Determined(theBid, T2))
        repository.persist(second, appendRaw(T2)) shouldBe PersistOutcome.Updated(2L)

        val found = requireNotNull(repository.find(id))
        found.openingRankOne shouldBe OpeningRankOneOutcome.Determined(theBid, T2)
        found.drawNumbers shouldBe DrawNumberObservation.Verified(setOf(3, 7), T1)
    }

    /** guard_opening_result_opening_rank_one — 같은 observation_key 로 값만 바꾸는 직접 SQL은 거부된다. */
    @Test
    fun `opening_rank_one_kind 를 같은 observation_key 로 바꾸는 직접 SQL은 거부된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val theBid = OpeningRankOneBid(bidderName = "SYN-WINNER")
        persistRankOne(repository, OpeningRankOneOutcome.Determined(theBid, T1), T1) shouldBe PersistOutcome.Inserted

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

    // =========================================================================
    // verifier r1 F-1 — 전이 여섯(회귀 방지, 재검증 명령 그대로). 컬럼별 COALESCE 는 kind 만
    // 새 값으로 덮고 동반 컬럼을 옛 값으로 남겨 V5 페어 CHECK 를 위반했다(정상 persist 에서
    // PSQLException). 축 단위 CASE(Sql.kt)로 고친 뒤 여섯 전이 모두 정상 저장돼야 한다.
    // =========================================================================

    @Test
    fun `전이 — Determined 에서 RankMissing 으로`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        persistRankOne(repository, OpeningRankOneOutcome.Determined(bid("SYN-A"), T1), T1)

        persistRankOne(repository, OpeningRankOneOutcome.RankMissing(T2), T2) shouldBe PersistOutcome.Updated(2L)

        requireNotNull(repository.find(id)).openingRankOne shouldBe OpeningRankOneOutcome.RankMissing(T2)
    }

    @Test
    fun `전이 — Determined 에서 RankDuplicated 로`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        persistRankOne(repository, OpeningRankOneOutcome.Determined(bid("SYN-A"), T1), T1)

        persistRankOne(repository, OpeningRankOneOutcome.RankDuplicated(3, T2), T2) shouldBe PersistOutcome.Updated(2L)

        requireNotNull(repository.find(id)).openingRankOne shouldBe OpeningRankOneOutcome.RankDuplicated(3, T2)
    }

    @Test
    fun `전이 — RankDuplicated 에서 Determined 로`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        persistRankOne(repository, OpeningRankOneOutcome.RankDuplicated(3, T1), T1)

        val theBid = bid("SYN-WINNER")
        persistRankOne(repository, OpeningRankOneOutcome.Determined(theBid, T2), T2) shouldBe PersistOutcome.Updated(2L)

        requireNotNull(repository.find(id)).openingRankOne shouldBe OpeningRankOneOutcome.Determined(theBid, T2)
    }

    @Test
    fun `전이 — RankDuplicated 에서 RankMissing 으로`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        persistRankOne(repository, OpeningRankOneOutcome.RankDuplicated(4, T1), T1)

        persistRankOne(repository, OpeningRankOneOutcome.RankMissing(T2), T2) shouldBe PersistOutcome.Updated(2L)

        requireNotNull(repository.find(id)).openingRankOne shouldBe OpeningRankOneOutcome.RankMissing(T2)
    }

    @Test
    fun `전이 — Verified 에서 RangeCheckUnavailable 로`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val first =
            OpeningResult(
                id,
                null,
                null,
                T1,
                totalReservePriceCandidateCount = 15,
                drawNumbers = DrawNumberObservation.Verified(setOf(3, 7), T1),
            )
        repository.persist(first, appendRaw(T1)) shouldBe PersistOutcome.Inserted

        val secondDrawNumbers = DrawNumberObservation.RangeCheckUnavailable(setOf(3, 7), T2)
        val second = OpeningResult(id, null, null, T2, drawNumbers = secondDrawNumbers)
        repository.persist(second, appendRaw(T2)) shouldBe PersistOutcome.Updated(2L)

        requireNotNull(repository.find(id)).drawNumbers shouldBe
            DrawNumberObservation.RangeCheckUnavailable(setOf(3, 7), T2)
    }

    /**
     * 전이 — Verified 에서 NotObserved 로(재수집이 이 축을 안 실음) · **F-2 낡음 신호 test
     * 겸함**. 값은 지워지지 않고(D-3E-3 (a) 와 같은 규율) `observedAt` 도 t1 그대로 남아야
     * 한다 — 그래야 소비자가 부모의 최신 `observed_at`(t2)과 비교해 이 축이 낡았음을 스스로
     * 판정할 수 있다.
     */
    @Test
    fun `전이 — Verified 에서 NotObserved 로(재수집 미관측)는 값과 관측 시각을 보존한다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val first =
            OpeningResult(
                id,
                null,
                null,
                T1,
                totalReservePriceCandidateCount = 15,
                drawNumbers = DrawNumberObservation.Verified(setOf(3, 7), T1),
            )
        repository.persist(first, appendRaw(T1)) shouldBe PersistOutcome.Inserted

        val second = OpeningResult(id, null, null, T2)
        repository.persist(second, appendRaw(T2)) shouldBe PersistOutcome.Updated(2L)

        val found = requireNotNull(repository.find(id))
        found.observedAt shouldBe T2
        val verified = found.drawNumbers.shouldBeInstanceOf<DrawNumberObservation.Verified>()
        verified.numbers shouldBe setOf(3, 7)
        verified.observedAt shouldBe T1
    }

    /**
     * F-3 — `OutOfRange` 는 `validRange` 를 총예가건수에서 재구성한다. 그 값이 없는 채로
     * 저장되면 읽기가 `IllegalArgumentException` 을 던졌다(구멍). V5 의
     * `opening_result_draw_numbers_out_of_range_requires_total` CHECK 가 이제 저장
     * 시점에서 그 조합을 거부한다 — 저장할 수 있는 상태를 읽을 수 없는 경우를 아예 없앤다.
     */
    @Test
    fun `F-3 — 총예가건수 없이 OutOfRange 를 저장하려 하면 거부된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val illegal =
            OpeningResult(
                id,
                null,
                null,
                T1,
                totalReservePriceCandidateCount = null,
                drawNumbers = DrawNumberObservation.OutOfRange(setOf(20), 1..15, T1),
            )

        shouldThrow<PSQLException> { repository.persist(illegal, appendRaw(T1)) }
    }
}
