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
import bidvector.sharedkernel.AwardAmount
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
 * S-2, 층 B(V4~V6 마이그레이션 + repository 매핑) — `opening_reserve_price` 자식 표와
 * `opening_result` 신규 fact 슬롯의 통합 test. §1.9.7 실측 정정(팀리드, 8건 23행 표본)이
 * 겨눈 두 시나리오(단수 예가 · 복수예비가격 15행)와 D-3E-3 (a)·D-3E-1b (a)·bypass #4·#6,
 * verifier r1 H-1·H-2·M-1을 여기서 고정한다.
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
        observedAt: Instant,
        won: Long = 100_000_000L,
    ): OpeningReservePriceRow =
        OpeningReservePriceRow(
            sequenceNumber = sequence,
            baseReservePrice = ReservePriceCandidateAmount(won, Currency.KRW),
            isDrawn = false,
            observedAt = observedAt,
            drawCount = 0,
        )

    private fun sequentialRows(
        count: Int,
        observedAt: Instant,
        wonBase: Long = 100_000_000L,
    ): List<OpeningReservePriceRow> =
        (1..count).map { n -> reserveRow(n.toString().padStart(3, '0'), observedAt, wonBase + n) }

    private fun parentRowCount(): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM opening_result").use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

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
        val rows = sequentialRows(15, observedAt)
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
     * 귀결 1 test ③(verifier r1 L-7 뒤 정정) — 부모 값이 행마다 반복돼 응답에 실려도 부모
     * 행은 정확히 하나다. **이전 판은 `PersistOutcome.Inserted`만 쟀는데, 그것은 이름이
     * 주장하는 「부모 upsert 실행 1회」를 재지 않는다**(outcome은 upsert 결과 종류일 뿐 실행
     * 횟수의 증거가 아니다). 지금은 `opening_result` 행 수를 직접 세어 정확히 1임을 잰다 —
     * 자식 15개를 반복 삽입해도 부모 표에 중복 행이 생기지 않는다는 것을 실제로 증명한다.
     */
    @Test
    fun `부모는 자식이 몇 개든 opening_result 에 정확히 한 행만 남는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-08T00:00:00Z")
        val rows = sequentialRows(15, observedAt)
        val result =
            OpeningResult(id, null, null, observedAt, totalReservePriceCandidateCount = 15, reservePrices = rows)

        val outcome = repository.persist(result, appendRaw(observedAt))

        outcome shouldBe PersistOutcome.Inserted
        parentRowCount() shouldBe 1L
    }

    /**
     * D-3E-3 (a) — 15행 뒤 12행만 오는 재수집(사라진 3행)은 기존 행을 지우지 않는다. 사라진
     * 순번(013·014·015)은 이번 응답에 없었을 뿐 표에는 그대로 남는다. **verifier r1 H-2 뒤
     * 추가** — 남은 행이 「조용히 낡지」 않는다는 것도 함께 잰다: 사라진 3행의 `observedAt`은
     * 첫 관측 시각 그대로이고, 갱신된 12행의 `observedAt`보다 이르다 — 소비자가 그 시각
     * 비교만으로 「이번 관측에 없었다」를 판정할 수 있다(파생 플래그를 저장하지 않는다).
     */
    @Test
    fun `15에서 12로 준 재수집은 사라진 3행을 지우지 않고, 그 행의 관측 시각이 낡음을 드러낸다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val firstObservedAt = Instant.parse("2026-09-08T00:00:00Z")
        val fifteenRows = sequentialRows(15, firstObservedAt)
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
        val twelveRows = sequentialRows(12, secondObservedAt, wonBase = 999_000_000L)
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
        // H-2 — 낡음은 observedAt 비교로 소비자가 스스로 판정한다(저장된 파생 플래그 없음).
        untouched.observedAt shouldBe firstObservedAt
        val refreshed = found.reservePrices.first { it.sequenceNumber == "001" }
        refreshed.observedAt shouldBe secondObservedAt
        (untouched.observedAt < refreshed.observedAt) shouldBe true
    }

    /** 멱등 재수집 — 같은 15행을 더 늦은 관측으로 다시 보내도 행이 늘지 않는다(ON CONFLICT 갱신). */
    @Test
    fun `같은 응답을 다시 수집해도 자식 행이 중복되지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val firstObservedAt = Instant.parse("2026-09-08T00:00:00Z")
        val rows = sequentialRows(15, firstObservedAt)
        val first =
            OpeningResult(id, null, null, firstObservedAt, totalReservePriceCandidateCount = 15, reservePrices = rows)
        repository.persist(first, appendRaw(firstObservedAt)) shouldBe PersistOutcome.Inserted

        val secondObservedAt = Instant.parse("2026-09-08T01:00:00Z")
        val rowsAgain = sequentialRows(15, secondObservedAt)
        val second =
            OpeningResult(
                id,
                null,
                null,
                secondObservedAt,
                totalReservePriceCandidateCount = 15,
                reservePrices = rowsAgain,
            )
        repository.persist(second, appendRaw(secondObservedAt)) shouldBe PersistOutcome.Updated(2L)

        requireNotNull(repository.find(id)).reservePrices.size shouldBe 15
    }

    /** bypass #4 — 순번 부재 행은 스키마가 지어낸 값 없이 삽입 자체를 막는다(NULL·빈 문자열·공백 셋 다). */
    @Test
    fun `reserve_price_sequence 가 NULL 이거나 공백뿐이면 직접 SQL 삽입도 거부된다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-08T00:00:00Z")
        val key = appendRaw(observedAt)
        val parentOnly = OpeningResult(id, null, null, observedAt)
        repository.persist(parentOnly, key) shouldBe PersistOutcome.Inserted

        shouldThrow<PSQLException> { insertReservePriceSequence(null, observedAt, key) }
        shouldThrow<PSQLException> { insertReservePriceSequence("", observedAt, key) }
        // verifier r1 M-1 — V6 이전에는 공백 한 칸이 CHECK 를 통과했다(결함 재현 + 수정 확인).
        shouldThrow<PSQLException> { insertReservePriceSequence(" ", observedAt, key) }
    }

    private fun insertReservePriceSequence(
        sequence: String?,
        observedAt: Instant,
        key: ObservationKey,
    ) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO opening_reserve_price " +
                        "(notice_number, notice_round, reserve_price_sequence, observed_at, observation_key) " +
                        "VALUES (?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, id.number.value)
                    statement.setString(2, id.round.value)
                    statement.setString(3, sequence)
                    statement.setTimestamp(4, Timestamp.from(observedAt))
                    statement.setString(5, key.value)
                    statement.executeUpdate()
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
        val row = reserveRow("001", observedAt)
        val result = OpeningResult(id, null, null, observedAt, reservePrices = listOf(row))

        var failed = false
        try {
            repository.persist(result, bogusKey)
        } catch (expected: Exception) {
            failed = true
        }

        failed shouldBe true
        repository.find(id) shouldBe null
    }

    /**
     * verifier r1 H-1 — canonical 왕복이 provenance 를 지어내지 않는다. `Undeclared`(비권위)로
     * 저장한 값이 `Published`(권위)로 복원되면 권위가 조용히 오른다 — 셋(최종낙찰금액·
     * 예정가격·기초금액) 전부를 확인한다.
     */
    @Test
    fun `provenance 는 저장한 그대로 복원된다 — 왕복으로 권위가 오르지 않는다`() {
        val repository = JdbcOpeningResultRepository(dataSource())
        val observedAt = Instant.parse("2026-09-08T00:00:00Z")
        val finalAward = AwardAmount(1_000_000L, Currency.KRW, Provenance.Undeclared)
        val plannedPrice = YegaAmount(2_000_000L, Currency.KRW, Provenance.OperatorDeclared)
        val baseAmount = BaseAmount(3_000_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.Undeclared)
        val result =
            OpeningResult(
                id,
                null,
                null,
                observedAt,
                finalAwardAmount = finalAward,
                plannedPrice = plannedPrice,
                baseAmount = baseAmount,
            )

        repository.persist(result, appendRaw(observedAt)) shouldBe PersistOutcome.Inserted

        val found = requireNotNull(repository.find(id))
        found.finalAwardAmount shouldBe finalAward
        found.finalAwardAmount?.provenance shouldBe Provenance.Undeclared
        found.plannedPrice shouldBe plannedPrice
        found.plannedPrice?.provenance shouldBe Provenance.OperatorDeclared
        found.baseAmount shouldBe baseAmount
        found.baseAmount?.provenance shouldBe Provenance.Undeclared
    }
}
