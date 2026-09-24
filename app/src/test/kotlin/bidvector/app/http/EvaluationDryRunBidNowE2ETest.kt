package bidvector.app.http

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.collections.shouldNotBeEmpty
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
import java.math.BigDecimal
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/**
 * D-6A3-17(a) 거동 test(검토 라운드 1 HIGH-1 둘째 다리, code-reviewer MEDIUM · verifier M3
 * 동시 해소) — `BidNow`를 내는 fake ML(`BidNowFakeMlAnalysisTestConfiguration`, `spring.
 * profiles.active=evaluation-bidnow-fake`)로 production 조립을 부팅해 `EvaluationDryRunE2ETest`
 * ⑤⑥⑦이 공집합 대 공집합으로만 확인하던 불변식을 **비어 있지 않은 값**으로 잰다:
 * ① `wouldNotifyNoticeIds` 가 비어 있지 않고 `bidNowNoticeIds` 와 같다(사다리·알림 요청 경로가
 * 같은 판정을 본다는 것을 실제 BidNow 로 확인) ② dry-run 전후 outbox 행 수가 같다(effect 0 이
 * BidNow 판정이 나온 뒤에도 유지된다 — verifier M3: `wouldNotifyNoticeIds`를 빈 목록으로
 * 바꾸는 변이가 이 test 에서만 RED).
 *
 * **profile 은 이 test 부팅에만 있다** — 다른 모든 test(`EvaluationDryRunE2ETest`·
 * `ProductionAssemblyAuthAuditTest`)는 `evaluation-bidnow-fake`를 활성화하지 않으므로
 * `BidNowFakeMlAnalysisTestConfiguration`의 `@Bean`이 등록되지 않고 `UnavailableMlAnalysis`
 * 가 그대로 유일한 배선이다.
 */
class EvaluationDryRunBidNowE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "evaluation-bidnow-e2e-test-fixture-credential"
        private const val CANDIDATE_CAP = 100

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_eval_bidnow_e2e_test")
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
                                "spring.profiles.active" to "evaluation-bidnow-fake",
                                "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                                "bidvector.persistence.username" to postgres.username,
                                "bidvector.persistence.credential" to postgres.password,
                                "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                                "bidvector.evaluation.candidate-cap" to CANDIDATE_CAP.toString(),
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

    /** D-6A1-40 관례 — 매 test 시작 전 이 slice가 건드리는 표를 비운다(test 간 행 격리). */
    @BeforeEach
    fun resetTables() {
        dataSource().connection.use { connection ->
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
        HttpHeaders().apply { set(OperatorCredentialFilter.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE) }

    private fun post(currentActiveBids: Int): ResponseEntity<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return restTemplate.exchange(
            url("/api/evaluation-dry-runs"),
            HttpMethod.POST,
            HttpEntity(mapOf("currentActiveBids" to currentActiveBids), authorizedHeaders()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    /** `EvaluationDryRunE2ETest.insertStrategy`와 같은 형태 — bidNowThreshold 0.9 이상이면 BidNow. */
    private fun insertStrategy(
        maxActiveBids: Int,
        bidNowThreshold: String,
        reviewThreshold: String,
        excludeKeywordTerm: String,
    ) {
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
                    statement.setArray(5, connection.createArrayOf("text", arrayOf(excludeKeywordTerm)))
                    statement.setBigDecimal(6, BigDecimal(bidNowThreshold))
                    statement.setBigDecimal(7, BigDecimal(reviewThreshold))
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

    private fun insertNotice(
        number: String,
        deadline: Instant,
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
        val key = JdbcRawObservationStore(dataSource(), fieldContracts, "eval-bidnow-e2e-test").append(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = deadline,
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    @Test
    fun `BidNow 를 내는 fake ML — wouldNotifyNoticeIds 비어있지 않음, bidNowNoticeIds 와 같음, outbox 전후 등식`() {
        insertStrategy(
            maxActiveBids = 10,
            bidNowThreshold = "0.9",
            reviewThreshold = "0.1",
            excludeKeywordTerm = "이-문구는-빈-감시텍스트에-나타날-수-없다",
        )
        val noticeId = insertNotice("20260101002", Instant.now().plusSeconds(86_400))
        val beforeOutbox = outboxRowCount()

        val response = post(0)

        response.statusCode.value() shouldBe 200
        response.body?.get("candidateCount") shouldBe 1

        @Suppress("UNCHECKED_CAST")
        val bidNowIds = (response.body?.get("bidNowNoticeIds") as? List<String>).orEmpty().toSet()

        @Suppress("UNCHECKED_CAST")
        val wouldNotifyIds = (response.body?.get("wouldNotifyNoticeIds") as? List<String>).orEmpty().toSet()
        val expectedLabel = "${noticeId.number.value}:${noticeId.round.value}"

        bidNowIds shouldBe setOf(expectedLabel)
        wouldNotifyIds.shouldNotBeEmpty()
        wouldNotifyIds shouldBe bidNowIds

        // dry-run 은 판정이 BidNow 여도 outbox 를 건드리지 않는다(D-6A3-3, 존재 단언이 아니라 전후 등식).
        outboxRowCount() shouldBe beforeOutbox
    }
}
