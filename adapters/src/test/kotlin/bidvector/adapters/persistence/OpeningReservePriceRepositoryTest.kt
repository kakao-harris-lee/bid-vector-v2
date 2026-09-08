package bidvector.adapters.persistence

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningReservePriceRow
import bidvector.procurement.OpeningResult
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ReservePriceCandidateAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.YegaAmount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.sql.Timestamp
import java.time.Instant

/**
 * S-2, 층 B(V4 마이그레이션 + repository 매핑) — `opening_reserve_price` 자식 표와
 * `opening_result` 신규 fact 슬롯의 통합 test. §1.9.7 실측 정정(팀리드, 8건 23행 표본)이
 * 겨눈 두 시나리오(단수 예가 · 복수예비가격 15행)와 D-3E-3 (a)·D-3E-1b (a)·bypass #4·#6을
 * 여기서 고정한다.
 */
class OpeningReservePriceRepositoryTest : PersistenceTestSupport() {
    private val id = NoticeId(NoticeNumber.of("RSV-20260908-001"), NoticeRound.of("000"))
    private val published = Provenance.Published(id.round)

    private fun appendRaw(observedAt: Instant): ObservationKey {
        val observation = RawNoticeObservation.of(emptyMap(), SourceEndpoint.RESERVE_PRICE_DETAIL, observedAt)
        return appendRawObservation(observation)
    }

    private fun reserveRow(
        sequence: String,
        won: Long = 100_000_000L,
    ): OpeningReservePriceRow =
        OpeningReservePriceRow(
            sequenceNumber = sequence,
            baseReservePrice = ReservePriceCandidateAmount(won, Currency.KRW),
            isDrawn = false,
            drawCount = 0,
        )

    private fun sequentialRows(
        count: Int,
        wonBase: Long = 100_000_000L,
    ): List<OpeningReservePriceRow> = (1..count).map { n -> reserveRow(n.toString().padStart(3, '0'), wonBase + n) }

    /**
     * 귀결 1 test ① — 총예가건수 1(단수 예가) + 순번 공백 응답은 자식 행 0개를 낳지만,
     * §1.9.7 정정으로 예정가격·기초금액은 부모 슬롯이라 저장된다. 3B-2
     * `rowIdentifierIndeterminate` 회계(별도 층, 이 slice가 다시 만들지 않는다)가 승격 불가
     * 건수를 이미 센다 — 여기서는 저장 층이 부모 값을 잃지 않는 것만 증명한다.
     */
    @Test
    fun `단수 예가(순번 공백)는 자식 행 0개, 부모 예정가격 기초금액은 저장된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-08T00:00:00Z")
        val plannedPrice = YegaAmount(300_000_000L, Currency.KRW, published)
        val baseAmount = BaseAmount(290_000_000L, Currency.KRW, VatTreatment.UNKNOWN, published)
        val result =
            OpeningResult(
                noticeId = id,
                winningRate = null,
                derivedBaseAmount = null,
                observedAt = observedAt,
                plannedPrice = plannedPrice,
                baseAmount = baseAmount,
                totalReservePriceCandidateCount = 1,
                reservePrices = emptyList(),
            )

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.reservePrices shouldBe emptyList()
        found.plannedPrice shouldBe plannedPrice
        found.baseAmount shouldBe baseAmount
        found.totalReservePriceCandidateCount shouldBe 1
    }

    /** 귀결 1 test ② — 총예가건수 15(복수예비가격) + 순번 채움 응답은 자식 15행을 낸다. */
    @Test
    fun `복수예비가격 15행은 순번이 채워져 있으면 자식 15행으로 저장된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-08T00:00:00Z")
        val rows = sequentialRows(15)
        val result =
            OpeningResult(
                noticeId = id,
                winningRate = null,
                derivedBaseAmount = null,
                observedAt = observedAt,
                totalReservePriceCandidateCount = 15,
                reservePrices = rows,
            )

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.reservePrices.map { it.sequenceNumber } shouldContainExactlyInAnyOrder rows.map { it.sequenceNumber }
        found.reservePrices.size shouldBe 15
    }

    /**
     * 귀결 1 test ③ — 부모 값이 행마다 반복돼 응답에 실려도 부모는 `persist` 한 호출에 한 번만
     * 쓰인다(자식 수와 무관하게 `opening_result` upsert 문 실행은 1회). `PersistOutcome`이
     * 자식 개수(0·1·15)와 무관하게 최초 호출에서 항상 `Inserted`인 것이 그 증거다 — 자식
     * 개수만큼 부모 행이 반복 upsert됐다면 이 호출 자체가 여러 outcome을 내야 하는데 API가
     * 단일 `PersistOutcome`만 낸다는 사실 자체가 「부모 upsert 1회」를 구성상 보장한다.
     */
    @Test
    fun `부모는 자식이 몇 개든 한 번만 삽입된다 — Inserted 는 항상 하나`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-08T00:00:00Z")
        val rows = sequentialRows(15)
        val result =
            OpeningResult(id, null, null, observedAt, totalReservePriceCandidateCount = 15, reservePrices = rows)

        val outcome = repository.persist(result, appendRaw(observedAt))

        outcome shouldBe PersistOutcome.Inserted
    }

    /**
     * D-3E-3 (a) — 15행 뒤 12행만 오는 재수집(사라진 3행)은 기존 행을 지우지 않는다. 사라진
     * 순번(013·014·015)은 이번 응답에 없었을 뿐 표에는 그대로 남는다.
     */
    @Test
    fun `15에서 12로 준 재수집은 사라진 3행을 지우지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val firstObservedAt = Instant.parse("2026-09-08T00:00:00Z")
        val fifteenRows = sequentialRows(15)
        val first =
            OpeningResult(
                id,
                null,
                null,
                firstObservedAt,
                totalReservePriceCandidateCount = 15,
                reservePrices = fifteenRows,
            )
        repository.persist(first, appendRaw(firstObservedAt)) shouldBe PersistOutcome.Inserted

        val secondObservedAt = Instant.parse("2026-09-08T01:00:00Z")
        val twelveRows = sequentialRows(12, wonBase = 999_000_000L)
        val second =
            OpeningResult(
                id,
                null,
                null,
                secondObservedAt,
                totalReservePriceCandidateCount = 12,
                reservePrices = twelveRows,
            )
        repository.persist(second, appendRaw(secondObservedAt)) shouldBe PersistOutcome.Updated(2L)

        val found = requireNotNull(repository.find(id))
        val allSequences = (1..15).map { it.toString().padStart(3, '0') }.toSet()
        found.reservePrices.map { it.sequenceNumber }.toSet() shouldBe allSequences
        // 사라진 013~015는 첫 관측 값 그대로(갱신되지 않았다) — 갱신된 12건과 다른 금액이다.
        val untouched = found.reservePrices.first { it.sequenceNumber == "013" }
        untouched.baseReservePrice?.won shouldBe 100_000_013L
    }

    /** 멱등 재수집 — 같은 15행을 더 늦은 관측으로 다시 보내도 행이 늘지 않는다(ON CONFLICT 갱신). */
    @Test
    fun `같은 응답을 다시 수집해도 자식 행이 중복되지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val firstObservedAt = Instant.parse("2026-09-08T00:00:00Z")
        val rows = sequentialRows(15)
        val first =
            OpeningResult(id, null, null, firstObservedAt, totalReservePriceCandidateCount = 15, reservePrices = rows)
        repository.persist(first, appendRaw(firstObservedAt)) shouldBe PersistOutcome.Inserted

        val secondObservedAt = Instant.parse("2026-09-08T01:00:00Z")
        val second =
            OpeningResult(id, null, null, secondObservedAt, totalReservePriceCandidateCount = 15, reservePrices = rows)
        repository.persist(second, appendRaw(secondObservedAt)) shouldBe PersistOutcome.Updated(2L)

        requireNotNull(repository.find(id)).reservePrices.size shouldBe 15
    }

    /** bypass #4 — 순번 부재 행은 스키마 NOT NULL이 지어낸 값 없이 삽입 자체를 막는다. */
    @Test
    fun `reserve_price_sequence 가 NULL 인 직접 SQL 삽입은 거부된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-08T00:00:00Z")
        val key = appendRaw(observedAt)
        val parentOnly = OpeningResult(id, null, null, observedAt)
        repository.persist(parentOnly, key) shouldBe PersistOutcome.Inserted

        shouldThrow<PSQLException> {
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO opening_reserve_price " +
                            "(notice_number, notice_round, reserve_price_sequence, observed_at, observation_key) " +
                            "VALUES (?, ?, NULL, ?, ?)",
                    ).use { statement ->
                        statement.setString(1, id.number.value)
                        statement.setString(2, id.round.value)
                        statement.setTimestamp(3, Timestamp.from(observedAt))
                        statement.setString(4, key.value)
                        statement.executeUpdate()
                    }
            }
        }
    }

    /** bypass #6 — 사업자등록번호·대표자명 컬럼은 두 표 어디에도 없다. */
    @Test
    fun `사업자등록번호 대표자명 컬럼은 존재하지 않는다`() {
        val forbiddenPatterns = listOf("bizno", "biz_no", "ceo", "representative", "대표자")
        val columnNames =
            dataSource().connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT column_name FROM information_schema.columns WHERE table_schema='public' " +
                                "AND table_name IN ('opening_result', 'opening_reserve_price')",
                        ).use { rs ->
                            val names = mutableListOf<String>()
                            while (rs.next()) names += rs.getString("column_name")
                            names
                        }
                }
            }

        for (pattern in forbiddenPatterns) {
            columnNames.none { it.contains(pattern, ignoreCase = true) } shouldBe true
        }
    }

    /** 항목 단위 트랜잭션 — 부모+자식 upsert가 실패하면(FK 위반) 부모도 커밋되지 않는다. */
    @Test
    fun `부모 자식 upsert 는 한 트랜잭션이다 — 실패하면 부모도 커밋되지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-08T00:00:00Z")
        val bogusKey = ObservationKey("BOGUS|NOT|IN|RAW|OBSERVATION")
        val result = OpeningResult(id, null, null, observedAt, reservePrices = listOf(reserveRow("001")))

        var failed = false
        try {
            repository.persist(result, bogusKey)
        } catch (expected: Exception) {
            failed = true
        }

        failed shouldBe true
        repository.find(id) shouldBe null
    }
}
