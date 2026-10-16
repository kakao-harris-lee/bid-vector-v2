package bidvector.adapters.notice

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.Sql
import bidvector.procurement.Agency
import bidvector.procurement.AgencyCode
import bidvector.procurement.AgencyName
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
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EstimatedAmount
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.notice.NoticeSearchParameters
import bidvector.workflow.notice.NoticeSearchRequest
import bidvector.workflow.notice.NoticeSearchState
import bidvector.workflow.notice.parseNoticeSearchParameters
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant

/**
 * [JdbcNoticeSearch] — 실제 PostgreSQL 위에서 **거동**을 잰다(문면은
 * `NoticeSearchStatementTest` 가 구조로 잰다).
 *
 * 설계 검토 (2) 의 우회 ②·⑤가 여기서 **실측**된다: `q=%` 가 전수 조회가 아니라 리터럴
 * `%` 검색이고, `'; DROP TABLE notice; --` 가 질의가 아니라 값이라 표가 그대로 남는다.
 * 금액 축은 기초금액만 보고(추정가격이 아니다) 비교 성립 전제를 지킨다 — capability map
 * STR-16 의 「금액 범위 검색의 basis 일치」가 이 자리에서 선다.
 */
class JdbcNoticeSearchTest : PersistenceTestSupport() {
    private val collectedAt = Instant.parse("2026-10-16T00:00:00Z")

    private fun search(parameters: NoticeSearchParameters) =
        JdbcNoticeSearch(dataSource()).search(
            parseNoticeSearchParameters(parameters).shouldBeInstanceOf<NoticeSearchRequest.Accepted>().criteria,
        )

    private fun labels(parameters: NoticeSearchParameters) =
        search(parameters).notices.map { notice -> notice.id.number.value }

    /**
     * production 경로([JdbcNoticeRepository])로 심는다 — 손으로 짠 INSERT 사본을 두지 않는다.
     * `observedAt` 을 공고번호마다 미뤄 관측 키가 겹치지 않게 한다.
     */
    @Suppress("LongParameterList")
    private fun insert(
        number: String,
        title: String? = null,
        demandAgency: String? = null,
        noticeAgency: String? = null,
        baseAmountWon: Long? = null,
        baseAmountVat: VatTreatment = VatTreatment.INCLUSIVE,
        estimatedAmountWon: Long? = null,
        deadline: Instant? = null,
        offsetSeconds: Long = 0,
    ): NoticeId {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        val fields = mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value)
        val observedAt = collectedAt.plusSeconds(offsetSeconds)
        val observation = RawNoticeObservation.of(fields, SourceEndpoint.NOTICE_LIST, observedAt)
        val key = appendRawObservation(observation)
        val published = Provenance.Published(NoticeRound.of("000"))
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount =
                    baseAmountWon?.let {
                        ResolvedBaseAmount.Direct.of(it, Currency.KRW, baseAmountVat, published)
                    },
                estimatedAmount =
                    estimatedAmountWon?.let {
                        ResolvedEstimatedAmount(
                            RawKey("presmptPrce"),
                            EstimatedAmount(it, Currency.KRW, VatTreatment.INCLUSIVE, published),
                        )
                    },
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = deadline,
                openingScheduledAt = null,
                raw = observation,
                title = title?.let(NoticeTitle::of),
                demandAgency = demandAgency?.let { agencyOf(it) },
                noticeAgency = noticeAgency?.let { agencyOf(it) },
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    private fun agencyOf(name: String) = Agency(code = AgencyCode.of(name), name = AgencyName.of(name))

    @Test
    fun `조건이 없으면 수집된 공고가 마감 임박 순으로 나온다 — 마감 없는 공고는 뒤다`() {
        insert("N-2", deadline = collectedAt.plusSeconds(200), offsetSeconds = 1)
        insert("N-1", deadline = collectedAt.plusSeconds(100), offsetSeconds = 2)
        insert("N-3", deadline = null, offsetSeconds = 3)

        val page = search(NoticeSearchParameters())

        page.notices.map { it.id.number.value } shouldContainExactly listOf("N-1", "N-2", "N-3")
        page.totalCount shouldBe 3L
        page.state shouldBe NoticeSearchState.MATCHED
    }

    @Test
    fun `우회 ② — 와일드카드 한 글자 질의는 전수 조회가 아니라 리터럴 검색이다`() {
        insert("N-1", title = "100% 국산 자재", offsetSeconds = 1)
        insert("N-2", title = "일반 자재 구매", offsetSeconds = 2)
        insert("N-3", title = "밑줄_포함 공고", offsetSeconds = 3)

        labels(NoticeSearchParameters(text = "%")) shouldContainExactly listOf("N-1")
        labels(NoticeSearchParameters(text = "_")) shouldContainExactly listOf("N-3")
        search(NoticeSearchParameters(text = "%")).totalCount shouldBe 1L
    }

    /**
     * 이스케이프 문자 자신(`!`)과 역슬래시가 **리터럴로** 검색된다(verifier r1 L-4 처방 뒤).
     * 역슬래시는 더 이상 이스케이프 문자가 아니라 보통 글자다.
     */
    @Test
    fun `느낌표와 역슬래시는 리터럴로 찾는다`() {
        insert("N-1", title = "긴급! 도로 포장", offsetSeconds = 1)
        insert("N-2", title = "경로 C:\\공사\\자료", offsetSeconds = 2)
        insert("N-3", title = "보통 공고", offsetSeconds = 3)

        labels(NoticeSearchParameters(text = "!")) shouldContainExactly listOf("N-1")
        labels(NoticeSearchParameters(text = "\\")) shouldContainExactly listOf("N-2")
        labels(NoticeSearchParameters(text = "!%")) shouldHaveSize 0
    }

    /**
     * **이스케이프가 세션 설정에 기대지 않는다**(L-4 의 실제 축). `standard_conforming_strings`
     * 를 데이터베이스 수준에서 꺼서 **새 연결이 그 설정을 물려받게** 한 뒤 같은 검색을 돌린다.
     * 역슬래시 이스케이프였다면 `ESCAPE '\'` 의 문자열이 닫히지 않아 질의가 예외로 깨졌다
     * (측정). 느낌표는 SQL 문자열에서 아무 뜻이 없어 그대로 돈다.
     */
    @Test
    fun `standard_conforming_strings 가 꺼져도 검색이 돈다`() {
        insert("N-1", title = "도로 포장", offsetSeconds = 1)
        val database = dataSource().connection.use { it.catalog }

        setDatabaseStandardConformingStrings(database, "off")
        try {
            labels(NoticeSearchParameters(text = "포장")) shouldContainExactly listOf("N-1")
            labels(NoticeSearchParameters(text = "100%")) shouldHaveSize 0
        } finally {
            setDatabaseStandardConformingStrings(database, "on")
        }
    }

    private fun setDatabaseStandardConformingStrings(
        database: String,
        value: String,
    ) {
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("ALTER DATABASE \"$database\" SET standard_conforming_strings = $value")
            }
        }
    }

    @Test
    fun `우회 ⑤ — 주입 문자열은 값이라 표가 그대로 남는다`() {
        insert("N-1", title = "정상 공고", offsetSeconds = 1)

        val page = search(NoticeSearchParameters(text = "'; DROP TABLE notice; --"))

        page.notices shouldHaveSize 0
        page.totalCount shouldBe 0L
        page.state shouldBe NoticeSearchState.NO_MATCH
        // 표가 남아 있다 — 다음 질의가 여전히 그 행을 낸다.
        labels(NoticeSearchParameters()) shouldContainExactly listOf("N-1")
    }

    @Test
    fun `q 는 공고명과 공고번호를 함께 보고 대소문자를 가리지 않는다`() {
        insert("ABC-1", title = "도로 포장 공사", offsetSeconds = 1)
        insert("XYZ-2", title = "조경 설계 용역", offsetSeconds = 2)

        labels(NoticeSearchParameters(text = "abc")) shouldContainExactly listOf("ABC-1")
        labels(NoticeSearchParameters(text = "조경")) shouldContainExactly listOf("XYZ-2")
    }

    @Test
    fun `agency 는 수요기관과 공고기관 이름을 함께 본다`() {
        insert("N-1", demandAgency = "서울특별시교육청", offsetSeconds = 1)
        insert("N-2", noticeAgency = "한국도로공사", offsetSeconds = 2)
        insert("N-3", demandAgency = "부산광역시", offsetSeconds = 3)

        labels(NoticeSearchParameters(agencyText = "교육청")) shouldContainExactly listOf("N-1")
        labels(NoticeSearchParameters(agencyText = "도로공사")) shouldContainExactly listOf("N-2")
    }

    /** capability map STR-16 — 금액 필터는 **기초금액**과 비교한다. 추정가격은 보지 않는다. */
    @Test
    fun `금액 범위는 기초금액으로 거른다 — 추정가격이 범위 안이어도 들어오지 않는다`() {
        insert("BASE-IN", baseAmountWon = 1_500L, estimatedAmountWon = 9_000L, offsetSeconds = 1)
        insert("BASE-OUT", baseAmountWon = 9_000L, estimatedAmountWon = 1_500L, offsetSeconds = 2)

        labels(NoticeSearchParameters(amountMinWon = "1000", amountMaxWon = "2000")) shouldContainExactly
            listOf("BASE-IN")
    }

    @Test
    fun `금액 한계는 양끝을 포함한다`() {
        insert("LOW", baseAmountWon = 1_000L, offsetSeconds = 1)
        insert("HIGH", baseAmountWon = 2_000L, offsetSeconds = 2)
        insert("OVER", baseAmountWon = 2_001L, offsetSeconds = 3)

        labels(NoticeSearchParameters(amountMinWon = "1000", amountMaxWon = "2000")).toSet() shouldBe
            setOf("LOW", "HIGH")
    }

    /**
     * 비교가 성립하지 않는 금액(과세 불일치·부재)은 금액 조건이 있을 때 결과에서 빠진다 —
     * 0 으로 간주하지 않는다(계약 선언). 감시 경로가 같은 쌍에서 `Undeterminable` 을 내는
     * 것과 같은 자리다.
     */
    @Test
    fun `금액 조건이 있으면 비교 불가·부재 금액은 빠진다`() {
        insert("COMPARABLE", baseAmountWon = 1_500L, offsetSeconds = 1)
        insert("UNKNOWN-VAT", baseAmountWon = 1_500L, baseAmountVat = VatTreatment.UNKNOWN, offsetSeconds = 2)
        insert("NO-AMOUNT", offsetSeconds = 3)

        labels(NoticeSearchParameters(amountMinWon = "1000", amountMaxWon = "2000")) shouldContainExactly
            listOf("COMPARABLE")
        // 금액 조건이 없으면 셋 다 나온다 — 조건이 없는 질의가 금액 축으로 좁아지지 않는다.
        labels(NoticeSearchParameters()).toSet() shouldBe setOf("COMPARABLE", "UNKNOWN-VAT", "NO-AMOUNT")
    }

    @Test
    fun `마감 범위는 양끝을 포함하고 마감 없는 공고는 그 조건으로 걸러진다`() {
        val from = collectedAt.plusSeconds(100)
        val to = collectedAt.plusSeconds(300)
        insert("ON-FROM", deadline = from, offsetSeconds = 1)
        insert("ON-TO", deadline = to, offsetSeconds = 2)
        insert("AFTER", deadline = to.plusSeconds(1), offsetSeconds = 3)
        insert("NO-DEADLINE", deadline = null, offsetSeconds = 4)

        labels(NoticeSearchParameters(deadlineFrom = from.toString(), deadlineTo = to.toString())) shouldContainExactly
            listOf("ON-FROM", "ON-TO")
    }

    @Test
    fun `전체 건수는 페이지 길이와 다르고 마지막 페이지 뒤는 빈 페이지 + 맞음이다`() {
        (1..5).forEach { index ->
            val at = index.toLong()
            insert("N-$index", deadline = collectedAt.plusSeconds(at), offsetSeconds = at)
        }

        val first = search(NoticeSearchParameters(size = "2", page = "0"))
        val second = search(NoticeSearchParameters(size = "2", page = "1"))
        val beyond = search(NoticeSearchParameters(size = "2", page = "50"))

        first.notices.map { it.id.number.value } shouldContainExactly listOf("N-1", "N-2")
        second.notices.map { it.id.number.value } shouldContainExactly listOf("N-3", "N-4")
        first.totalCount shouldBe 5L
        beyond.notices shouldHaveSize 0
        beyond.totalCount shouldBe 5L
        beyond.state shouldBe NoticeSearchState.MATCHED
    }

    @Test
    fun `빈 결과의 두 뜻이 갈린다 — 수집 전 vs 조건이 좁다`() {
        val beforeCollection = search(NoticeSearchParameters())

        beforeCollection.totalCount shouldBe 0L
        beforeCollection.state shouldBe NoticeSearchState.NOTHING_COLLECTED

        insert("N-1", title = "도로 포장", offsetSeconds = 1)
        val tooNarrow = search(NoticeSearchParameters(text = "존재하지않는낱말"))

        tooNarrow.totalCount shouldBe 0L
        tooNarrow.state shouldBe NoticeSearchState.NO_MATCH
    }

    /**
     * **쓰기 부재의 구조 잠금**(verifier r1 F-1) — 검색이 여는 트랜잭션에서는 DML 셋이 전부
     * **DB 에 막힌다.** 이름·문면 게이트가 아니라 연결 설정이 지므로, 어댑터에 쓰기를 넣는
     * 변이는 이름을 무엇으로 바꾸든 이 test 에서 실패한다.
     */
    @Test
    fun `검색이 여는 트랜잭션에서는 INSERT·UPDATE·DELETE 가 DB 에 막힌다`() {
        val writes =
            listOf(
                "INSERT INTO notice (notice_number, notice_round, status, observation_key) " +
                    "VALUES ('X-1', '000', 'Open', 'k')",
                "UPDATE notice SET status = 'Closed'",
                "DELETE FROM notice",
            )

        writes.forEach { sql ->
            val failure =
                shouldThrow<SQLException> {
                    dataSource().connection.use { connection ->
                        connection.beginReadOnlySearchTransaction()
                        connection.createStatement().use { statement -> statement.executeUpdate(sql) }
                    }
                }
            (failure.message ?: "") shouldContain "read-only transaction"
        }
    }

    /**
     * 음성 대조 — 같은 DML 이 **읽기 전용을 걸지 않은** 연결에서는 통과한다. 위 test 가
     * 공허하게 참이 되는 회귀(예: DML 자체가 문법 오류라 늘 던지는 경우)를 막는다.
     */
    @Test
    fun `같은 DELETE 는 읽기 전용이 아닌 연결에서는 통과한다 — 음성 대조`() {
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement -> statement.executeUpdate("DELETE FROM notice") }
        }
    }

    /**
     * 투영 축 — 질의가 실제로 읽는 열 집합을 **메타데이터로 실측**해 `notice` 열과 등식으로
     * 잠근다. 원문 관측(`payload`)·감사 표의 열이 join 으로 들어오면 이 집합이 달라져 깨진다
     * (문면 grep 이 아니라 질의가 낸 결과를 본다).
     */
    @Test
    fun `질의가 읽는 열은 notice 의 선언된 열뿐이다 — 원문·감사 열이 없다`() {
        val expected =
            ("notice_number, notice_round, " + Sql.NOTICE_COLUMNS)
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

        val actual =
            dataSource().connection.use { connection ->
                connection.prepareStatement(NoticeSearchSql.selectPage("TRUE")).use { prepared ->
                    prepared.setLong(1, 1L)
                    prepared.setLong(2, 0L)
                    prepared.executeQuery().use { rs ->
                        (1..rs.metaData.columnCount).map { rs.metaData.getColumnLabel(it) }.toSet()
                    }
                }
            }

        actual shouldBe expected
    }
}
