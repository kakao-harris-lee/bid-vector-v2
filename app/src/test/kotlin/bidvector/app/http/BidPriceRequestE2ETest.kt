package bidvector.app.http

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.adminDataSource
import bidvector.procurement.Agency
import bidvector.procurement.AgencyName
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeTitle
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 투찰가 요청의 **출하 조립 E2E**(M7/7C ②) — production 조립을 실제로 부팅해(`EvaluationDryRunE2ETest`
 * 와 같은 기법) 실 PostgreSQL 에 공고·전략을 심고 endpoint 를 왕복한다.
 *
 * 이 자리에서만 재는 것 셋:
 * 1. **출하 배선이 실제로 선다** — 새 포트 둘(공고 단건 읽기·자격 판정 봉투)과 요청 스코프
 *    팩토리가 production 컨텍스트에서 조립된다. 이중체 위의 test 는 이것을 재지 못한다.
 * 2. **privacy 음성 대조** — 공고명·기관명이 **DB 에 있는데도** 응답 본문에 나타나지 않는다.
 *    원문이 없는 표본으로는 「안 실린다」가 공허하게 참이다.
 * 3. **외부 effect 0** — outbox 행이 늘지 않는다(알림함 기록은 7C ③ 뒤, 지금은 요청까지다).
 */
class BidPriceRequestE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "bid-price-e2e-test-fixture-credential"
        private const val CANDIDATE_CAP = 100

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_bid_price_e2e_test")
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
                                "bidvector.evaluation.candidate-cap" to CANDIDATE_CAP.toString(),
                            ),
                    ).run()
            port = (context as ServletWebServerApplicationContext).webServer?.port ?: error("web server 가 뜨지 않았다")
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            context.close()
            postgres.stop()
        }
    }

    private val restTemplate: TestRestTemplate = TestRestTemplate()

    private fun url(path: String): String = "http://localhost:$port$path"

    private fun dataSource(): DataSource = context.getBean(DataSource::class.java)

    @BeforeEach
    fun resetTables() {
        adminDataSource(postgres).connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "TRUNCATE TABLE notice, notice_audit, rejected_write, raw_observation, " +
                        "provenance_authority, operator_strategy, operator_strategy_revision, " +
                        "outbox, api_request_audit RESTART IDENTITY CASCADE",
                )
            }
        }
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE) }

    private fun post(
        number: String,
        round: String = "000",
        currentActiveBids: Int = 0,
        headers: HttpHeaders = authorizedHeaders(),
    ): ResponseEntity<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return restTemplate.exchange(
            url("/api/bid-price-requests"),
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "noticeNumber" to number,
                    "noticeRound" to round,
                    "currentActiveBids" to currentActiveBids,
                ),
                headers,
            ),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    /** DB 직접 설정 — 전략 편집 HTTP 경로로 `max_active_bids` 를 넣을 수 없다(알려진 제한, D-6A3-4). */
    private fun insertStrategy(maxActiveBids: Int) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO operator_strategy (id, revision, focus_categories, focus_region_terms, " +
                        "exclude_region_terms, required_keyword_terms, exclude_keyword_terms, " +
                        "bid_now_threshold, review_threshold, max_active_bids) " +
                        "VALUES (1, 1, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    val empty = connection.createArrayOf("text", emptyArray<String>())
                    statement.setArray(1, empty)
                    statement.setArray(2, empty)
                    statement.setArray(3, empty)
                    statement.setArray(4, empty)
                    statement.setArray(5, connection.createArrayOf("text", arrayOf("이-문구는-공고에-없다")))
                    statement.setBigDecimal(6, BigDecimal("0.80"))
                    statement.setBigDecimal(7, BigDecimal("0.50"))
                    statement.setInt(8, maxActiveBids)
                    statement.executeUpdate()
                }
        }
    }

    private fun outboxRowCount(): Int =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM outbox").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    /** 공고명·기관명·기초금액·게시 하한율을 **채운** 공고를 심는다(privacy 음성 대조의 전제). */
    private fun insertNotice(
        number: String,
        title: String,
        agencyName: String,
    ): NoticeId {
        val resolution = KONEPS_COLLECTION_POLICY.resolve(LocalDate.now())
        val fieldContracts = (resolution as Resolution.Resolved).value.fieldContracts
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.now(),
            )
        val key = JdbcRawObservationStore(dataSource(), fieldContracts, "bid-price-e2e-test").append(observation)
        val agency = Agency(code = null, name = requireNotNull(AgencyName.of(agencyName)))
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        2_000_000L,
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.Published(id.round),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = FloorRate(Rate.ofFraction(BigDecimal("0.87745")), FloorRateOrigin.NoticeValue(id.round)),
                deadlineAt = Instant.now().plusSeconds(86_400),
                openingScheduledAt = null,
                raw = observation,
                title = NoticeTitle.of(title),
                demandAgency = agency,
                noticeAgency = agency,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    @Test
    fun `출하 조립에서 공고 단건 평가가 근거를 낸다`() {
        insertStrategy(maxActiveBids = 10)
        insertNotice("E2E-BP-1", title = "도로 포장 공사", agencyName = "서울특별시")

        val response = post("E2E-BP-1")

        response.statusCode.value() shouldBe 200
        val body = response.body ?: error("본문이 비었다")
        body["noticeNumber"] shouldBe "E2E-BP-1"
        body["maxActiveBids"] shouldBe 10
        body["correlationId"].shouldNotBeNull()
        // 기초금액은 값과 basis·과세·provenance·해석 갈래를 함께 낸다(DEC-01).
        body["baseAmountWon"] shouldBe 2_000_000
        body["baseAmountBasis"] shouldBe "BASE_AMOUNT"
        body["baseAmountResolution"] shouldBe "DIRECT"
        // 게시 하한율이 근거로 선다(DEC-03) — 법정 하한율은 해석하지 않는다.
        body["publishedFloorRateFraction"] shouldBe "0.87745"
        body["floorRateGroundsState"] shouldBe "USABLE"
        // 자격 축은 프로필 미설정이라 판정 불가다 — 미달이 아니다(U-5).
        body["licenseVerdict"] shouldBe "UNCERTAIN"
        body["licenseValidity"] shouldBe "NOT_VERIFIED"
        // 출하 ML 은 항상 미가용이라 사다리가 「추천 없음」으로 내려온다.
        body["mlAnalysisState"] shouldBe "UNAVAILABLE"
        body["confidenceState"] shouldBe "NOT_AVAILABLE"
        // 가격 세 축은 상태와 사유만 낸다.
        body["bidPriceMenuState"] shouldBe "NOT_COMPUTED"
        body["floorShortfallState"] shouldBe "NOT_COMPUTED"
        // 자격 헤더 경로에는 계정 주체가 없다 — 지어내지 않고 사유를 낸다(M7/7C③-b).
        body["inboxRecordState"] shouldBe "NOT_RECORDED"
        body["inboxRecordNotRecordedReason"] shouldBe "NO_ACCOUNT_SUBJECT"
    }

    /**
     * **privacy 음성 대조**(R-1 관례) — 공고명·기관명이 DB 에 있는데도 응답 어디에도 나타나지
     * 않는다. 「근거 원문」은 (출처 필드 + 요건 면허명 + 정책 version)이고 원시 관측 blob 이 아니다.
     */
    @Test
    fun `공고명과 기관명이 DB 에 있어도 응답에 실리지 않는다`() {
        insertStrategy(maxActiveBids = 10)
        insertNotice("E2E-BP-2", title = "비밀스러운공고명", agencyName = "비밀스러운기관명")

        val rendered = post("E2E-BP-2").body.toString()

        rendered shouldNotContain "비밀스러운공고명"
        rendered shouldNotContain "비밀스러운기관명"
    }

    @Test
    fun `외부 effect 가 없다 — outbox 행이 늘지 않는다`() {
        insertStrategy(maxActiveBids = 10)
        insertNotice("E2E-BP-3", title = "도로 포장 공사", agencyName = "서울특별시")

        post("E2E-BP-3").statusCode.value() shouldBe 200

        outboxRowCount() shouldBe 0
    }

    @Test
    fun `없는 공고는 404 NOTICE_NOT_FOUND 다`() {
        insertStrategy(maxActiveBids = 10)

        val response = post("E2E-BP-ABSENT")

        response.statusCode.value() shouldBe 404
        response.body?.get("code") shouldBe ErrorCode.NOTICE_NOT_FOUND
    }
}
