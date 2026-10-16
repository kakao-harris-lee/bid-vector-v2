package bidvector.app.http

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.adminDataSource
import bidvector.procurement.Agency
import bidvector.procurement.AgencyCode
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
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.shouldBe
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
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 공고 검색을 **출하 조립 + 실 PostgreSQL** 로 끝까지 돈다(`EvaluationDryRunE2ETest` 와 같은
 * 기법). 다른 test 들이 각자 한 층만 재는 것과 달리 여기서는 컨트롤러·조회기·어댑터·DB 가
 * 한 줄로 이어진 상태를 본다.
 *
 * 이 자리가 필요한 이유는 **verifier r1 F-2** 다 — `U+0000` 질의는 HTTP 층 fake 포트 위에서는
 * 멀쩡히 200 이었고, 실 DB 바인딩에 닿는 순간 예외가 되어 계약이 400 을 선언한 입력이 500 으로
 * 나갔다. 그 갈림은 **실 DB 가 붙어야만** 보인다.
 */
class NoticeSearchProductionE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "notice-search-e2e-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_notice_search_e2e_test")
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

    private val restTemplate: TestRestTemplate = TestRestTemplate()

    private fun url(path: String): String = "http://localhost:$port$path"

    private fun dataSource(): DataSource = context.getBean(DataSource::class.java)

    @BeforeEach
    fun resetTables() {
        adminDataSource(postgres).connection.use { connection ->
            connection.createStatement().use {
                it.execute("TRUNCATE TABLE notice, notice_audit, raw_observation, api_request_audit CASCADE")
            }
        }
    }

    private fun authorizedHeaders(): HttpHeaders =
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE) }

    @Suppress("UNCHECKED_CAST")
    private fun search(query: String): ResponseEntity<List<Map<String, Any?>>> =
        restTemplate.exchange(
            URI.create(url("/api/notices$query")),
            HttpMethod.GET,
            HttpEntity<Void>(authorizedHeaders()),
            List::class.java,
        ) as ResponseEntity<List<Map<String, Any?>>>

    @Suppress("UNCHECKED_CAST")
    private fun errorOf(query: String): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            URI.create(url("/api/notices$query")),
            HttpMethod.GET,
            HttpEntity<Void>(authorizedHeaders()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    private fun insertNotice(
        number: String,
        title: String,
        agencyName: String,
        baseAmountWon: Long,
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
        val key = JdbcRawObservationStore(dataSource(), fieldContracts, "notice-search-e2e-test").append(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount =
                    ResolvedBaseAmount.Direct.of(
                        baseAmountWon,
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.Published(id.round),
                    ),
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = Instant.parse("2026-11-01T05:00:00Z"),
                openingScheduledAt = null,
                raw = observation,
                title = NoticeTitle.of(title),
                demandAgency = Agency(AgencyCode.of(agencyName), AgencyName.of(agencyName)),
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    /**
     * **F-2 회귀** — 실 DB 가 붙은 출하 조립에서 `U+0000` 질의가 **400** 이다. 거부 규칙이
     * 없으면 이 값은 바인딩 단계까지 가서 `PSQLException` → 500 `INTERNAL_ERROR` 가 된다.
     */
    @Test
    fun `NUL 질의는 실 DB 위에서도 500 이 아니라 400 이다`() {
        val response = errorOf("?q=%00")

        response.statusCode.value() shouldBe 400
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    @Test
    fun `수집 전에는 빈 본문 + 수집 전 사유다`() {
        val response = search("")

        response.statusCode.value() shouldBe 200
        response.body?.size shouldBe 0
        response.headers.getFirst(TOTAL_COUNT_HEADER) shouldBe "0"
        response.headers.getFirst(RESULT_STATE_HEADER) shouldBe "NOTHING_COLLECTED"
    }

    @Test
    fun `수집된 공고를 제목·기관·금액으로 찾는다 — 조건이 좁으면 다른 사유다`() {
        insertNotice("E2E-1", "도로 포장 공사", "서울특별시", 1_500L)

        val byTitle = search("?q=%ED%8F%AC%EC%9E%A5")
        byTitle.statusCode.value() shouldBe 200
        byTitle.body?.single()?.get("noticeNumber") shouldBe "E2E-1"
        byTitle.body?.single()?.get("baseAmountWon") shouldBe 1500
        byTitle.headers.getFirst(TOTAL_COUNT_HEADER) shouldBe "1"
        byTitle.headers.getFirst(RESULT_STATE_HEADER) shouldBe "MATCHED"

        search("?agency=%EC%84%9C%EC%9A%B8").body?.size shouldBe 1
        search("?amountMin=1000&amountMax=2000").body?.size shouldBe 1
        search("?amountMin=5000").headers.getFirst(RESULT_STATE_HEADER) shouldBe "NO_MATCH"
    }

    @Test
    fun `자격증명이 없으면 실 DB 조립에서도 401 이다`() {
        val response = restTemplate.getForEntity(URI.create(url("/api/notices")), Map::class.java)

        response.statusCode.value() shouldBe 401
    }
}
