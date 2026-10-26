package bidvector.app.http

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcOpeningResultRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.adminDataSource
import bidvector.procurement.KONEPS_COLLECTION_POLICY
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
import bidvector.sharedkernel.Resolution
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpStatus
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** 저장소에만 있고 응답에는 절대 없어야 하는 표지 상호(D-7C5-3) — 값 수준 단언의 대상이다. */
private const val SENTINEL_COMPANY = "SENTINEL-WINNER-DO-NOT-EXPORT"

private val OBSERVED_AT: Instant = Instant.parse("2026-10-22T00:00:00Z")

/**
 * 상류가 보낸 **원문 표기** — canonical 공고번호(`E2E-OPEN-1`)는 이 글자에서 파생된다
 * (`NoticeNumber.of`: ASCII 대문자화 · 공백 → `-`). 둘이 다른 상태가 기본이어야 원문 표를
 * 보는 질의가 정규화를 건너뛴 채 초록이 되지 않는다.
 */
private const val RAW_NUMBER = "e2e open 1"
private const val RAW_ROUND = "001"

/**
 * 개찰 결과 조회를 **출하 조립 + 실 PostgreSQL** 로 끝까지 돈다(`NoticeSearchProductionE2ETest`
 * 와 같은 기법) — 컨트롤러·조회기·읽기 전용 어댑터·DB 가 한 줄로 이어진 상태를 본다.
 *
 * 이 자리에서만 성립하는 측정이 하나 있다: **저장된 업체 상호가 응답 본문 전문에 없다**는
 * 값 수준 단언이다. HTTP 층 fake 위에서는 상호를 담을 타입조차 없어 공허하게 참이고, 실 DB
 * 에 그 값이 실제로 있는 상태에서만 「읽지 않는다」가 측정된다.
 */
class OpeningResultProductionE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "opening-result-e2e-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_opening_result_e2e_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var context: ConfigurableApplicationContext
        private var port: Int = 0

        @JvmStatic
        @BeforeAll
        fun boot() {
            context =
                SpringApplicationBuilder(BidVectorApplication::class.java)
                    .properties(
                        PRODUCTION_DISPATCH_PROPERTIES +
                            mapOf(
                                "server.port" to "0",
                                "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                                "bidvector.persistence.username" to postgres.username,
                                "bidvector.persistence.credential" to postgres.password,
                                "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                                "bidvector.evaluation.candidate-cap" to "100",
                            ),
                    ).run()
            port = (context as ServletWebServerApplicationContext).webServer?.port ?: error("web server가 뜨지 않았다")
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            context.close()
            postgres.stop()
        }
    }

    private val id = NoticeId(NoticeNumber.of(RAW_NUMBER), NoticeRound.of(RAW_ROUND))

    private fun dataSource(): DataSource = context.getBean(DataSource::class.java)

    @BeforeEach
    fun resetTables() {
        adminDataSource(postgres).connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "TRUNCATE TABLE notice, notice_audit, opening_result, raw_observation, api_request_audit CASCADE",
                )
            }
        }
    }

    /**
     * 원문은 **상류 표기 그대로** 심는다 — canonical 값([id])을 되심으면 「원문 = canonical」인
     * 경우만 돌아, 정규화를 건너뛴 대조가 끝까지 초록으로 보인다(cr r1 HIGH 가 난 자리).
     */
    private fun rawOf(endpoint: SourceEndpoint): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(
                NOTICE_NUMBER_RAW_KEY to RAW_NUMBER,
                NOTICE_ROUND_RAW_KEY to RAW_ROUND,
            ),
            endpoint,
            OBSERVED_AT,
        )

    private fun appendRaw(observation: RawNoticeObservation): ObservationKey {
        val contracts =
            (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 10, 22)) as Resolution.Resolved).value.fieldContracts
        return JdbcRawObservationStore(dataSource(), contracts, "e2e-release").append(observation)
    }

    private fun seedNotice() {
        val observation = rawOf(SourceEndpoint.NOTICE_LIST)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = OBSERVED_AT.minusSeconds(86_400),
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, appendRaw(observation)) shouldBe PersistOutcome.Inserted
    }

    /** 확정 낙찰업체명과 잠정 1위 상호 **둘 다** 표지로 심는다 — 응답은 둘 중 어느 것도 싣지 않아야 한다. */
    private fun seedCanonicalWithSentinelNames() {
        val result =
            OpeningResult(
                noticeId = id,
                winningRate = null,
                derivedBaseAmount = null,
                observedAt = OBSERVED_AT,
                finalAwardAmount = AwardAmount(880_000_000L, Currency.KRW, Provenance.Published(id.round)),
                finalAwardCompanyName = SENTINEL_COMPANY,
                participantCount = 6,
                progressDivision = "개찰완료",
                actualOpeningAt = OBSERVED_AT.minusSeconds(3_600),
                openingRankOne =
                    OpeningRankOneOutcome.Determined(
                        OpeningRankOneBid(
                            bidderName = SENTINEL_COMPANY,
                            bidAmount = ObservedBidAmount(871_000_000L, Currency.KRW),
                            bidRate = Rate.ofFraction(BigDecimal("0.87995")),
                        ),
                        OBSERVED_AT,
                    ),
            )
        JdbcOpeningResultRepository(dataSource())
            .persist(result, appendRaw(rawOf(SourceEndpoint.OPENING_RESULT_LIST)))
    }

    /** 공용 소켓 GET 을 쓴다 — `URI.create` 는 `%G1` 을 서버에 닿기 전에 거부한다(공용 헬퍼 KDoc). */
    private fun rawBody(query: String): RawHttpResponse =
        rawGet(port, "/api/opening-results$query", TEST_CREDENTIAL_VALUE)

    @Test
    fun `출하 조립에서 개찰 결과가 끝까지 나온다 — 저장된 업체 상호는 본문에 없다`() {
        seedNotice()
        seedCanonicalWithSentinelNames()

        val response = rawBody("?noticeNumber=E2E-OPEN-1&noticeRound=001")

        response.status shouldBe HttpStatus.OK.value()
        withClue("응답 본문 전문에 표지 상호가 없다 — 질의가 그 열을 읽지 않는다") {
            response.body shouldNotContain SENTINEL_COMPANY
        }
        withClue("같은 본문이 그 행의 값을 실제로 나른다 — 위 단언이 공허하지 않다") {
            response.body shouldContainState "OPENED"
            response.body shouldContain "880000000"
            response.body shouldContain "0.87995"
        }
    }

    @Test
    fun `승격 전과 수집 전이 출하 조립에서 다른 상태로 나온다`() {
        seedNotice()

        val beforeRaw = rawBody("?noticeNumber=E2E-OPEN-1&noticeRound=001")
        beforeRaw.body shouldContainState "NOT_COLLECTED"

        appendRaw(rawOf(SourceEndpoint.OPENING_COMPLETE))

        rawBody("?noticeNumber=E2E-OPEN-1&noticeRound=001").body shouldContainState "OBSERVED_NOT_PROMOTED"
    }

    @Test
    fun `공고를 모르면 공고 부재 상태로 나온다 — 500 이 아니다`() {
        rawBody("?noticeNumber=E2E-UNKNOWN&noticeRound=001").let { response ->
            response.status shouldBe HttpStatus.OK.value()
            response.body shouldContainState "NOTICE_ABSENT"
        }
    }

    @Test
    fun `자격증명 없이 부르면 401 이다`() {
        rawGet(port, "/api/opening-results?noticeNumber=E2E-OPEN-1&noticeRound=001", null).status shouldBe
            HttpStatus.UNAUTHORIZED.value()
    }

    @Test
    fun `제어 문자·차수 형식 위반은 출하 조립에서도 400 이다`() {
        rawBody("?noticeNumber=E2E%00OPEN&noticeRound=001").status shouldBe HttpStatus.BAD_REQUEST.value()
        rawBody("?noticeNumber=E2E-OPEN-1&noticeRound=1").status shouldBe HttpStatus.BAD_REQUEST.value()
        rawBody("?noticeNumber=%G1&noticeRound=001").status shouldBe HttpStatus.BAD_REQUEST.value()
    }

    @Test
    fun `조회는 쓰기를 남기지 않는다 — 개찰 결과 표의 행 버전이 그대로다`() {
        seedNotice()
        seedCanonicalWithSentinelNames()
        val before = openingResultFingerprint()

        rawBody("?noticeNumber=E2E-OPEN-1&noticeRound=001")
        rawBody("?noticeNumber=E2E-UNKNOWN&noticeRound=001")

        openingResultFingerprint() shouldBe before
    }

    private fun openingResultFingerprint(): List<String> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                val fingerprintSql = "SELECT xmin::text AS xmin, ctid::text AS ctid FROM opening_result ORDER BY ctid"
                statement
                    .executeQuery(fingerprintSql)
                    .use { rows ->
                        generateSequence {
                            if (rows.next()) "${rows.getString("xmin")}:${rows.getString("ctid")}" else null
                        }.toList()
                    }
            }
        }

    private infix fun String.shouldContainState(state: String) {
        withClue("본문이 상태 $state 를 나른다: $this") {
            contains("\"state\":\"$state\"") shouldBe true
        }
    }
}
