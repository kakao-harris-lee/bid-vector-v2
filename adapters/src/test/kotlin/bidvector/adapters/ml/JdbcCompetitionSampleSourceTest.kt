package bidvector.adapters.ml

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcOpeningResultRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.decision.MlUnavailableReason
import bidvector.procurement.BusinessCategory
import bidvector.procurement.CategoryCode
import bidvector.procurement.CategoryLabel
import bidvector.procurement.DrawNumberObservation
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeRepository
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningRankOneBid
import bidvector.procurement.OpeningRankOneOutcome
import bidvector.procurement.OpeningReservePriceRow
import bidvector.procurement.OpeningResult
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ReservePriceCandidateAmount
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.evaluation.CompetitionSampleQuery
import bidvector.workflow.evaluation.CompetitionSampleSupply
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import bidvector.workflow.evaluation.SampleExclusionReason
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [JdbcCompetitionSampleSource](M4/4B-7, D-4B7-3) — 창·상한·최신 순·대상 제외·공종 일치·
 * 개찰일 결측 행 포함 여부(초과 집합이라 포함 — 자격은 `SampleEligibility`가 거른다)를 잰다.
 * 기존 repository test 관례(Testcontainers PostgreSQL, `PersistenceTestSupport`)를 그대로
 * 쓴다. `adapters.ml` 패키지에 있는 이유는 `JdbcCompetitionSampleSource.kt` KDoc 참고
 * (`PersistenceAdapterDependencyTest`의 domain import 경계).
 */
class JdbcCompetitionSampleSourceTest : PersistenceTestSupport() {
    private val category = "A01"
    private val asOf = Instant.parse("2026-09-16T00:00:00Z")
    private val targetId = NoticeId(NoticeNumber.of("SAMPLE-TARGET-001"), NoticeRound.of("000"))

    private fun source(clock: Clock = Clock.fixed(asOf, ZoneOffset.UTC)): JdbcCompetitionSampleSource =
        JdbcCompetitionSampleSource(
            dataSource(),
            JdbcNoticeRepository(dataSource()),
            JdbcOpeningResultRepository(dataSource()),
            clock,
        )

    private fun query(
        windowDays: Int = 365,
        limit: Int = 500,
    ): CompetitionSampleQuery = CompetitionSampleQuery(CategoryCode(category), targetId, asOf, windowDays, limit)

    private fun appendRaw(observedAt: Instant): ObservationKey =
        appendRawObservation(RawNoticeObservation.of(emptyMap(), SourceEndpoint.NOTICE_LIST, observedAt))

    /** 공종·기초금액만 실은 최소 notice — 자격 나머지 축은 opening 이 진다. */
    private fun seedNotice(
        id: NoticeId,
        categoryCode: String,
    ) {
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                asOf,
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = BusinessCategory(CategoryCode(categoryCode), CategoryLabel("공사")),
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        1_000_000_000L,
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.Published(id.round),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
    }

    /** 자격을 모두 만족하는 개찰 결과(예비가격 15·1위 확정·번호 4) — `actualOpeningAt` 만 가변. */
    private fun seedFullyEligibleOpening(
        id: NoticeId,
        actualOpeningAt: Instant?,
    ) {
        val observedAt = asOf.minusSeconds(1)
        val rows =
            (1..15).map { n ->
                OpeningReservePriceRow(
                    sequenceNumber = n.toString().padStart(3, '0'),
                    baseReservePrice = ReservePriceCandidateAmount(900_000_000L + n, Currency.KRW),
                    isDrawn = false,
                    observedAt = observedAt,
                )
            }
        val result =
            OpeningResult(
                noticeId = id,
                winningRate = Rate.ofFraction(BigDecimal("0.9200")),
                derivedBaseAmount = null,
                observedAt = observedAt,
                actualOpeningAt = actualOpeningAt,
                reservePrices = rows,
                openingRankOne =
                    OpeningRankOneOutcome.Determined(
                        OpeningRankOneBid("표본업체", null, Rate.ofFraction(BigDecimal("0.9200"))),
                        observedAt,
                    ),
                drawNumbers = DrawNumberObservation.Verified(setOf(1, 5, 9, 14), observedAt),
            )
        JdbcOpeningResultRepository(dataSource()).persist(result, appendRaw(observedAt))
    }

    private fun seedCandidate(
        number: String,
        categoryCode: String = category,
        actualOpeningAt: Instant?,
    ): NoticeId {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        seedNotice(id, categoryCode)
        seedFullyEligibleOpening(id, actualOpeningAt)
        return id
    }

    @Test
    fun `공종이 다르면 후보에서 빠진다`() {
        seedCandidate("SAMPLE-OTHER-CAT-001", categoryCode = "B02", actualOpeningAt = asOf.minusSeconds(3600))

        val supply = source().samplesFor(query()) as CompetitionSampleSupply.Supplied

        supply.samples shouldBe emptyList()
        supply.excluded shouldBe emptyMap()
    }

    @Test
    fun `대상 공고 자신은 후보에서 빠진다`() {
        seedNotice(targetId, category)
        seedFullyEligibleOpening(targetId, asOf.minusSeconds(3600))

        val supply = source().samplesFor(query()) as CompetitionSampleSupply.Supplied

        supply.samples shouldBe emptyList()
        supply.excluded shouldBe emptyMap()
    }

    @Test
    fun `개찰일이 미래거나 창 밖이면 후보에서 빠진다`() {
        seedCandidate("SAMPLE-FUTURE-001", actualOpeningAt = asOf.plusSeconds(3600)) // 미래 — 우회 (1)
        seedCandidate("SAMPLE-OLD-001", actualOpeningAt = asOf.minusSeconds(400L * 86_400)) // 400일 전 — 창(365일) 밖

        val supply =
            source(clock = Clock.fixed(asOf, ZoneOffset.UTC)).samplesFor(query(windowDays = 365))
                as CompetitionSampleSupply.Supplied

        supply.samples shouldBe emptyList()
        supply.excluded shouldBe emptyMap()
    }

    /** verifier r1 F-6 — 술어는 `<`(엄격 미만)다. `actual_opening_at == asOf`는 제외돼야 한다. */
    @Test
    fun `개찰일이 asOf 와 정확히 같으면 제외된다 — 경계값`() {
        seedCandidate("SAMPLE-BOUNDARY-EQ-001", actualOpeningAt = asOf)

        val supply = source().samplesFor(query()) as CompetitionSampleSupply.Supplied

        supply.samples shouldBe emptyList()
        supply.excluded shouldBe emptyMap()
    }

    /** verifier r1 F-6 — 창 하한 술어는 `>=`(포함)다. `actual_opening_at == asOf - windowDays`는 포함돼야 한다. */
    @Test
    fun `개찰일이 창 하한과 정확히 같으면 포함된다 — 경계값`() {
        val windowDays = 30
        val lowerBound = asOf.minusSeconds(windowDays * 86_400L)
        seedCandidate("SAMPLE-BOUNDARY-WINDOW-001", actualOpeningAt = lowerBound)

        val supply = source().samplesFor(query(windowDays = windowDays)) as CompetitionSampleSupply.Supplied

        supply.samples.size shouldBe 1
        supply.samples.single().openedOn shouldBe lowerBound.atZone(OPENING_DATE_ZONE).toLocalDate()
    }

    @Test
    fun `창 안 후보는 자격을 만족하면 Eligible 로 포함된다`() {
        val id = seedCandidate("SAMPLE-WITHIN-001", actualOpeningAt = asOf.minusSeconds(3600))

        val supply = source().samplesFor(query()) as CompetitionSampleSupply.Supplied

        supply.samples.size shouldBe 1
        supply.samples.single().categoryCode shouldBe CategoryCode(category)
        supply.excluded shouldBe emptyMap()
        // NoticeId 자체는 CompetitionSample 에 안 실린다(D-2B-3 — 표본은 식별자가 아니다) — id 를
        // 참조하는 것은 이 test 가 후보를 정확히 하나 심었다는 것을 스스로 확인하기 위함이다.
        id shouldBe id
    }

    /**
     * verifier r1 F-1 — 이전 판은 두 후보를 같은 Asia/Seoul 날짜(같은 시각대의 `-3600s`·
     * `-7200s`)로 심어 `openedOn`(유일하게 식별 가능한 축) 으로 정렬을 구별할 수 없었다
     * (`ORDER BY … DESC` → `ASC` 변이가 살아남았다, 리포트 「변이 실측」). 후보 셋을 **서로
     * 다른 날짜**(1·2·3일 전)로 심고, `NULLS LAST` 후보(개찰일 결측)도 함께 심어 상한을
     * 먼저 먹지 않는지 대조한다 — 상한 2에 유효 날짜 후보 3 + 결측 1이면, DESC 정렬은
     * 가장 최근 둘(1일 전·2일 전)만 낸다.
     */
    @Test
    fun `상한을 넘는 후보는 최신 순으로 잘리고 결측일 후보가 상한을 먼저 먹지 않는다`() {
        val oldest = asOf.minusSeconds(3 * 86_400L)
        val middle = asOf.minusSeconds(2 * 86_400L)
        val newest = asOf.minusSeconds(1 * 86_400L)
        seedCandidate("SAMPLE-ORDER-OLDEST-001", actualOpeningAt = oldest)
        seedCandidate("SAMPLE-ORDER-MIDDLE-001", actualOpeningAt = middle)
        seedCandidate("SAMPLE-ORDER-NEWEST-001", actualOpeningAt = newest)
        seedCandidate("SAMPLE-ORDER-NODATE-001", actualOpeningAt = null)

        val supply = source().samplesFor(query(limit = 2)) as CompetitionSampleSupply.Supplied

        supply.samples.size shouldBe 2
        supply.samples.map { it.openedOn }.toSet() shouldBe
            setOf(
                newest.atZone(OPENING_DATE_ZONE).toLocalDate(),
                middle.atZone(OPENING_DATE_ZONE).toLocalDate(),
            )
    }

    @Test
    fun `개찰일 결측 후보는 초과 집합으로 포함되고 자격 판정에서 OPENING_DATE_MISSING 으로 제외된다`() {
        seedCandidate("SAMPLE-NODATE-001", actualOpeningAt = null)

        val supply = source().samplesFor(query()) as CompetitionSampleSupply.Supplied

        supply.samples shouldBe emptyList()
        supply.excluded shouldBe mapOf(SampleExclusionReason.OPENING_DATE_MISSING to 1)
    }

    /**
     * verifier r1 F-3 — 합계 불변식 `samples.size + excluded.values.sum() == 후보 수`.
     * `VanishingNoticeRepository`로 스캔(SQL)과 복원(`find`) 사이에 행 하나가 사라지는
     * 상황을 흉내 낸다 — 실제 삭제 경로는 이 저장소에 없지만, `candidatePair`의 `null`
     * 분기가 조용히 사라지지 않고 `CANDIDATE_VANISHED`로 계수되는지는 이렇게만 잴 수 있다.
     */
    @Test
    fun `합계 불변식 — samples 와 excluded 의 합은 후보 수와 같다(CANDIDATE_VANISHED 포함)`() {
        seedCandidate("SAMPLE-SUM-ELIGIBLE-001", actualOpeningAt = asOf.minusSeconds(3600))
        val vanishedId = seedCandidate("SAMPLE-SUM-VANISHED-001", actualOpeningAt = asOf.minusSeconds(3600))
        seedCandidate("SAMPLE-SUM-EXCLUDED-001", actualOpeningAt = null) // OPENING_DATE_MISSING

        val vanishingRepo = VanishingNoticeRepository(JdbcNoticeRepository(dataSource()), vanishedId)
        val src =
            JdbcCompetitionSampleSource(
                dataSource(),
                vanishingRepo,
                JdbcOpeningResultRepository(dataSource()),
                Clock.fixed(asOf, ZoneOffset.UTC),
            )

        val supply = src.samplesFor(query()) as CompetitionSampleSupply.Supplied

        (supply.samples.size + supply.excluded.values.sum()) shouldBe 3
        supply.samples.size shouldBe 1
        supply.excluded shouldBe
            mapOf(
                SampleExclusionReason.CANDIDATE_VANISHED to 1,
                SampleExclusionReason.OPENING_DATE_MISSING to 1,
            )
    }

    /** [candidatePair]가 `null`을 내는 경로(스캔 뒤 복원 시점에 행이 사라짐)를 흉내 내는 fake — 위 test 전용. */
    private class VanishingNoticeRepository(
        private val delegate: NoticeRepository,
        private val vanishedId: NoticeId,
    ) : NoticeRepository {
        override fun persist(
            command: NoticeCollected,
            observationKey: ObservationKey,
        ): PersistOutcome = delegate.persist(command, observationKey)

        override fun find(id: NoticeId): Notice? = if (id == vanishedId) null else delegate.find(id)
    }

    /**
     * 위협 모델 (8) — DB 접속 실패가 [samplesFor]를 예외로 죽이지 않고 값으로 접힌다. 연결
     * 자체가 안 되는 `DataSource`(닫힌 포트)로 실측한다 — Testcontainers 컨테이너는 건드리지
     * 않는다.
     */
    @Test
    fun `DB 접속 실패는 예외 대신 Unavailable 값으로 접힌다`() {
        val brokenDataSource =
            PGSimpleDataSource().apply {
                setUrl("jdbc:postgresql://127.0.0.1:1/nonexistent")
                user = "bidvector_admin"
                password = "wrong"
                connectTimeout = 1
            }

        val supply =
            JdbcCompetitionSampleSource(
                brokenDataSource,
                JdbcNoticeRepository(brokenDataSource),
                JdbcOpeningResultRepository(brokenDataSource),
            ).samplesFor(query())

        supply shouldBe CompetitionSampleSupply.Unavailable(MlUnavailableReason.TransportFailed)
    }
}
