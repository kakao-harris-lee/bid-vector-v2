package bidvector.adapters.opening

import bidvector.adapters.notice.ReadOnlyWitness
import bidvector.adapters.notice.beginReadOnlySearchTransaction
import bidvector.adapters.notice.witnessedDataSource
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcOpeningResultRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.NoticeCollected
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
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.export
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.ProvisionalRankOne
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Instant

private val T1: Instant = Instant.parse("2026-10-22T00:00:00Z")
private val T2: Instant = Instant.parse("2026-10-22T03:00:00Z")
private val DEADLINE: Instant = Instant.parse("2026-10-21T05:00:00Z")

/** 표지 상호 — 응답 어디에도 이 글자가 나오면 안 된다(D-7C5-3, 값 수준 단언). */
private const val SENTINEL_COMPANY = "SENTINEL-BIDDER-DO-NOT-EXPORT"

/**
 * [JdbcOpeningResultView] — 실제 PostgreSQL 위에서 **읽은 사실**을 잰다(상태 판정은
 * `OpeningResultViewTest` 가 DB 없이 전수로 잰다).
 *
 * 여기서 실측되는 우회: ② 상호가 응답 경로에 오르지 않는다(읽은 열 집합 + 표지 글자) ·
 * ③ 잠정 관측이 확정 칸을 바꾸지 않는다 · ⑤ 조회가 쓰기를 쥐지 않는다(읽기 전용 트랜잭션 +
 * 세 표 무쓰기 지문) · ⑩ 개찰 축 아닌 표를 끌어오지 않는다 · ⑬ 다른 공고·다른 축의 원문이
 * 이 공고의 상태를 바꾸지 않는다.
 */
class JdbcOpeningResultViewTest : PersistenceTestSupport() {
    private val id = NoticeId(NoticeNumber.of("OPENVIEW-20261022-001"), NoticeRound.of("001"))
    private val other = NoticeId(NoticeNumber.of("OPENVIEW-20261022-999"), NoticeRound.of("001"))

    private fun view() = JdbcOpeningResultView(dataSource())

    private fun seedNotice(target: NoticeId = id) {
        val observation = observationOf(SourceEndpoint.NOTICE_LIST, T1, target)
        val command =
            NoticeCollected(
                id = target,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = DEADLINE,
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, appendRawObservation(observation)) shouldBe
            PersistOutcome.Inserted
    }

    /** 원문 한 줄 — 공고 식별자 둘은 계약 등재 키라 투영에 실린다(`NOTICE_IDENTIFIER_PRESENT_IN`). */
    private fun observationOf(
        endpoint: SourceEndpoint,
        observedAt: Instant,
        target: NoticeId = id,
    ): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(
                NOTICE_NUMBER_RAW_KEY to target.number.value,
                NOTICE_ROUND_RAW_KEY to target.round.value,
            ),
            endpoint,
            observedAt,
        )

    private fun rawKey(
        endpoint: SourceEndpoint,
        observedAt: Instant,
        target: NoticeId = id,
    ): ObservationKey = appendRawObservation(observationOf(endpoint, observedAt, target))

    private fun seedCanonical(
        result: OpeningResult,
        observedAt: Instant = T1,
    ) {
        JdbcOpeningResultRepository(dataSource()).persist(result, rawKey(SourceEndpoint.OPENING_RESULT_LIST, observedAt))
    }

    private fun canonicalOf(
        progressDivision: String? = "개찰완료",
        participantCount: Int? = 5,
        finalAwardAmount: AwardAmount? = null,
        openingRankOne: OpeningRankOneOutcome = OpeningRankOneOutcome.NotObserved,
        observedAt: Instant = T1,
    ) = OpeningResult(
        noticeId = id,
        winningRate = null,
        derivedBaseAmount = null,
        observedAt = observedAt,
        finalAwardAmount = finalAwardAmount,
        participantCount = participantCount,
        progressDivision = progressDivision,
        actualOpeningAt = Instant.parse("2026-10-21T06:00:00Z"),
        openingRankOne = openingRankOne,
    )

    private fun present(facts: NoticeOpeningFacts) = facts.shouldBeInstanceOf<NoticeOpeningFacts.Present>()

    @Test
    fun `공고 행이 없으면 공고 부재다 — 개찰 결과 질의도 돌지 않는다`() {
        view().lookup(id) shouldBe NoticeOpeningFacts.NoticeAbsent
    }

    @Test
    fun `canonical 행이 있으면 진행구분 라벨 원문과 관측값을 그대로 낸다`() {
        seedNotice()
        seedCanonical(canonicalOf(progressDivision = "유찰", participantCount = 0))

        val facts = present(view().lookup(id))
        val canonical = requireNotNull(facts.canonical)
        canonical.progressLabel shouldBe "유찰"
        canonical.figures.participantCount shouldBe 0
        canonical.figures.actualOpeningAt shouldBe Instant.parse("2026-10-21T06:00:00Z")
        canonical.figures.observedAt shouldBe T1
        facts.deadlineAt shouldBe DEADLINE
    }

    @Test
    fun `우회 ③ — 잠정 1위만 있으면 확정 칸은 비어 있다`() {
        seedNotice()
        seedCanonical(
            canonicalOf(
                finalAwardAmount = null,
                openingRankOne =
                    OpeningRankOneOutcome.Determined(
                        OpeningRankOneBid(
                            bidderName = SENTINEL_COMPANY,
                            bidAmount = ObservedBidAmount(912_000_000L, Currency.KRW),
                            bidRate = Rate.ofPercent(BigDecimal("87.995")),
                        ),
                        T1,
                    ),
            ),
        )

        val canonical = requireNotNull(present(view().lookup(id)).canonical)
        canonical.figures.confirmedAward shouldBe null
        val determined = canonical.figures.provisionalRankOne.shouldBeInstanceOf<ProvisionalRankOne.Determined>()
        determined.bidAmount shouldBe ObservedBidAmount(912_000_000L, Currency.KRW)
        determined.bidRate shouldBe Rate.ofPercent(BigDecimal("87.995"))
        determined.observedAt shouldBe T1
    }

    @Test
    fun `우회 ② — 잠정 1위 상호는 어느 칸에도 실리지 않는다`() {
        seedNotice()
        seedCanonical(
            canonicalOf(
                finalAwardAmount = AwardAmount(900_000_000L, Currency.KRW, Provenance.Published(id.round)),
                openingRankOne =
                    OpeningRankOneOutcome.Determined(OpeningRankOneBid(bidderName = SENTINEL_COMPANY), T1),
            ),
        )

        val facts = present(view().lookup(id))
        withClue("읽은 사실 전문에 표지 상호가 없다") {
            facts.toString() shouldNotContain SENTINEL_COMPANY
        }
    }

    @Test
    fun `확정 낙찰금액은 통화·provenance 와 함께 왕복된다`() {
        seedNotice()
        val provenance = Provenance.Published(NoticeRound.of("001"))
        seedCanonical(canonicalOf(finalAwardAmount = AwardAmount(880_000_000L, Currency.KRW, provenance)))

        val award = requireNotNull(requireNotNull(present(view().lookup(id)).canonical).figures.confirmedAward)
        val exported = award.export()
        exported.won shouldBe 880_000_000L
        exported.currency shouldBe Currency.KRW
        exported.provenance shouldBe provenance
    }

    @Test
    fun `순위 부재·중복은 그대로 낸다 — 투찰금액으로 순위를 재계산하지 않는다`() {
        seedNotice()
        seedCanonical(canonicalOf(openingRankOne = OpeningRankOneOutcome.RankMissing(T1)))
        requireNotNull(present(view().lookup(id)).canonical).figures.provisionalRankOne shouldBe
            ProvisionalRankOne.RankMissing(T1)

        seedCanonical(canonicalOf(openingRankOne = OpeningRankOneOutcome.RankDuplicated(3, T2), observedAt = T2), T2)
        requireNotNull(present(view().lookup(id)).canonical).figures.provisionalRankOne shouldBe
            ProvisionalRankOne.RankDuplicated(3, T2)
    }

    /** SET-02 acceptance — 잠정 1위 수집이 확정 낙찰 필드를 **변경하지 않는다**(V5 축 단위 UPSERT 의 귀결). */
    @Test
    fun `우회 ③ — 잠정 관측이 뒤에 와도 확정 낙찰금액은 그대로다`() {
        seedNotice()
        val award = AwardAmount(870_000_000L, Currency.KRW, Provenance.Published(NoticeRound.of("001")))
        seedCanonical(canonicalOf(finalAwardAmount = award))
        val before = requireNotNull(present(view().lookup(id)).canonical).figures.confirmedAward

        seedCanonical(
            canonicalOf(
                finalAwardAmount = null,
                openingRankOne =
                    OpeningRankOneOutcome.Determined(OpeningRankOneBid(bidderName = SENTINEL_COMPANY), T2),
                observedAt = T2,
            ),
            T2,
        )

        val after = requireNotNull(present(view().lookup(id)).canonical)
        after.figures.confirmedAward shouldBe before
        after.figures.provisionalRankOne.shouldBeInstanceOf<ProvisionalRankOne.Determined>()
    }

    @Test
    fun `canonical 이 없고 개찰 결과 축 원문이 있으면 관측됐다고 답한다`() {
        seedNotice()
        rawKey(SourceEndpoint.OPENING_COMPLETE, T1)

        val facts = present(view().lookup(id))
        facts.canonical shouldBe null
        facts.openingAxisObserved shouldBe true
    }

    @Test
    fun `우회 ⑩ — 예비가격·기초금액 축 원문만 있으면 개찰 결과 관측이 아니다`() {
        seedNotice()
        rawKey(SourceEndpoint.RESERVE_PRICE_DETAIL, T1)
        rawKey(SourceEndpoint.BASE_AMOUNT_DETAIL, T2)

        present(view().lookup(id)).openingAxisObserved shouldBe false
    }

    @Test
    fun `우회 ⑬ — 다른 공고의 개찰 원문은 이 공고의 답을 바꾸지 않는다`() {
        seedNotice()
        seedNotice(other)
        rawKey(SourceEndpoint.OPENING_RESULT_LIST, T1, other)

        present(view().lookup(id)).openingAxisObserved shouldBe false
        present(view().lookup(other)).openingAxisObserved shouldBe true
    }

    @Test
    fun `차수가 다른 개찰 원문은 이 공고의 답을 바꾸지 않는다`() {
        seedNotice()
        val otherRound = NoticeId(id.number, NoticeRound.of("002"))
        rawKey(SourceEndpoint.OPENING_RESULT_LIST, T1, otherRound)

        present(view().lookup(id)).openingAxisObserved shouldBe false
    }

    @Test
    fun `canonical 이 있으면 원문 존재 질의는 돌지 않는다`() {
        seedNotice()
        seedCanonical(canonicalOf())

        val witness = ReadOnlyWitness()
        JdbcOpeningResultView(witnessedDataSource(dataSource(), witness)).lookup(id)

        witness.queries().map { it.sql }.none { it.contains("raw_observation") } shouldBe true
    }

    @Test
    fun `우회 ⑤ — lookup 의 모든 문장이 읽기 전용 트랜잭션 안에서 돈다`() {
        seedNotice()
        rawKey(SourceEndpoint.OPENING_COMPLETE, T1)

        val witness = ReadOnlyWitness()
        JdbcOpeningResultView(witnessedDataSource(dataSource(), witness)).lookup(id)

        witness.queries().shouldNotBeEmpty()
        witness.statementsOutsideReadOnly() shouldBe emptyList()
    }

    @Test
    fun `우회 ⑤ — 조회가 세 표에 쓰기 흔적을 남기지 않는다`() {
        seedNotice()
        seedCanonical(canonicalOf())
        val before = writeFingerprint()

        view().lookup(id)
        view().lookup(other)

        writeFingerprint() shouldContainExactly before
    }

    /** 음성 대조 — 같은 지문이 쓰기를 **실제로 본다**(항진 단언이 아니다). */
    @Test
    fun `지문은 쓰기를 잡는다 — 읽기 전용이 아닌 연결의 갱신이 지문을 바꾼다`() {
        seedNotice()
        seedCanonical(canonicalOf())
        val before = writeFingerprint()

        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE opening_result SET participant_count = participant_count")
            }
        }

        (writeFingerprint() == before) shouldBe false
    }

    @Test
    fun `읽기 전용 트랜잭션에서는 DB 가 쓰기를 거부한다`() {
        seedNotice()
        var rejected = false
        dataSource().connection.use { connection ->
            connection.beginReadOnlySearchTransaction()
            try {
                connection.createStatement().use { statement ->
                    statement.executeUpdate("DELETE FROM opening_result")
                }
            } catch (failure: SQLException) {
                rejected = failure.message?.contains("read-only") == true
            } finally {
                connection.rollback()
            }
        }
        rejected shouldBe true
    }

    @Test
    fun `질의가 실제로 읽는 열 집합에 업체 상호가 없다`() {
        seedNotice()
        seedCanonical(canonicalOf())

        val read =
            dataSource().connection.use { connection ->
                connection.prepareStatement(OpeningResultViewSql.SELECT_RESULT).use { statement ->
                    statement.setString(1, id.number.value)
                    statement.setString(2, id.round.value)
                    statement.executeQuery().use { rows ->
                        (1..rows.metaData.columnCount).map { rows.metaData.getColumnName(it) }
                    }
                }
            }

        read shouldContainExactly OpeningResultViewSql.RESULT_COLUMNS
        withClue("상호 두 열은 읽는 자리에서 먼저 막힌다") {
            read.none { it.contains("company_name") || it.contains("bidder_name") } shouldBe true
        }
    }

    /** 조회가 닿는 세 표의 쓰기 흔적 — 행 집합과 `xmin`·`ctid`(7C① R3-L-1 처방을 표 셋으로 넓혔다). */
    private fun writeFingerprint(): List<String> =
        listOf("notice", "opening_result", "raw_observation").flatMap { table -> fingerprintOf(table) }

    private fun fingerprintOf(table: String): List<String> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery("SELECT xmin::text AS xmin, ctid::text AS ctid FROM $table ORDER BY ctid")
                    .use { rows ->
                        generateSequence {
                            if (rows.next()) "$table@${rows.getString("xmin")}:${rows.getString("ctid")}" else null
                        }.toList()
                    }
            }
        }
}
