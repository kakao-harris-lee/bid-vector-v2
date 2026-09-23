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
import io.kotest.matchers.collections.shouldBeEmpty
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
 * acceptance ①~⑦(scope.md) — production 조립을 실제로 부팅해(`ProductionAssemblyAuthAuditTest`
 * 와 같은 기법) 평가 dry-run endpoint 를 E2E 로 잰다. 전략에 `maxActiveBids`를 설정하는 HTTP
 * 경로가 아직 없어(`OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`, 6A-2 소관) 이 test 는 **DB 직접
 * INSERT**로 전략을 준비한다 — D-6A3-4가 명시한 알려진 제한 그대로.
 *
 * **결정성 표본(⑤) 설계** — 감시 텍스트가 빈 문자열이라(6F-4 실 데이터 0건) `requiredKeywordTerms`
 * 류 포함 규칙은 전건 불일치한다(scope.md 「확인하지 않은 것」). 대신 `excludeKeywordTerms`
 * 하나(빈 텍스트에 나타날 수 없는 임의 문구)만 채운 전략을 쓴다 — `WatchRules.isEmpty()`를
 * `false`로 만들어 `NoGate`(거부)를 피하면서, 그 축은 항상 `Passed`(매치 없음)를 낸다.
 */
class EvaluationDryRunE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "evaluation-e2e-test-fixture-credential"
        private const val CANDIDATE_CAP = 100

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_eval_e2e_test")
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

    private fun post(
        currentActiveBids: Int,
        headers: HttpHeaders = authorizedHeaders(),
    ): ResponseEntity<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return restTemplate.exchange(
            url("/api/evaluation-dry-runs"),
            HttpMethod.POST,
            HttpEntity(mapOf("currentActiveBids" to currentActiveBids), headers),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>
    }

    /**
     * DB 직접 설정(D-6A3-4 알려진 제한) — `max_active_bids`·(선택) 임계치·(선택) 제외
     * 키워드 하나. NOT NULL 배열 다섯은 항상 채운다(V9 제약).
     */
    private fun insertStrategy(
        maxActiveBids: Int?,
        bidNowThreshold: String? = null,
        reviewThreshold: String? = null,
        excludeKeywordTerm: String? = null,
    ) {
        val excludeKeywordTerms = excludeKeywordTerm?.let { arrayOf(it) } ?: emptyArray()
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
                    statement.setArray(5, connection.createArrayOf("text", excludeKeywordTerms))
                    statement.setBigDecimal(6, bidNowThreshold?.let(::BigDecimal))
                    statement.setBigDecimal(7, reviewThreshold?.let(::BigDecimal))
                    if (maxActiveBids != null) {
                        statement.setInt(8, maxActiveBids)
                    } else {
                        statement.setNull(8, Types.INTEGER)
                    }
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
        val key = JdbcRawObservationStore(dataSource(), fieldContracts, "eval-e2e-test").append(observation)
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
    fun `① 후보 없음 → 200, 네 배열 빈, candidateCount 0`() {
        insertStrategy(maxActiveBids = 10)

        val response = post(0)

        response.statusCode.value() shouldBe 200
        response.body?.get("candidateCount") shouldBe 0
        (response.body?.get("bidNowNoticeIds") as? List<*>)?.shouldBeEmpty()
        (response.body?.get("reviewNoticeIds") as? List<*>)?.shouldBeEmpty()
        (response.body?.get("skipNoticeIds") as? List<*>)?.shouldBeEmpty()
        (response.body?.get("notReachedNoticeIds") as? List<*>)?.shouldBeEmpty()
        (response.body?.get("wouldNotifyNoticeIds") as? List<*>)?.shouldBeEmpty()
    }

    @Test
    fun `② 상한 없는 전략 → 409`() {
        insertStrategy(maxActiveBids = null)

        val response = post(0)

        response.statusCode.value() shouldBe 409
        response.body?.get("code") shouldBe ErrorCode.MAX_ACTIVE_BIDS_NOT_CONFIGURED
    }

    @Test
    fun `③ 음수 현재값 → 400`() {
        insertStrategy(maxActiveBids = 10)

        val response = post(-1)

        response.statusCode.value() shouldBe 400
        response.body?.get("code") shouldBe ErrorCode.INVALID_REQUEST
    }

    @Test
    fun `④ 인증 없음 → 401`() {
        insertStrategy(maxActiveBids = 10)

        val response = post(0, HttpHeaders())

        response.statusCode.value() shouldBe 401
    }

    /**
     * ⑤⑥⑦을 한 test 로 묶는다 — 같은 호출 하나의 응답에서 세 불변식을 같이 잰다
     * (별도 호출로 가르면 서로 다른 판정 인스턴스를 대조하게 된다).
     */
    @Test
    fun `⑤⑥⑦ 표본 공고 1건 — 판정이 결정적으로 review, outbox 등식, wouldNotify == bidNow`() {
        insertStrategy(
            maxActiveBids = 10,
            bidNowThreshold = "0.9",
            reviewThreshold = "0.1",
            excludeKeywordTerm = "이-문구는-빈-감시텍스트에-나타날-수-없다",
        )
        val noticeId = insertNotice("20260101001", Instant.now().plusSeconds(86_400))
        val beforeOutbox = outboxRowCount()

        val response = post(0)

        response.statusCode.value() shouldBe 200
        response.body?.get("candidateCount") shouldBe 1
        // UnavailableMlAnalysis 라 ML 도달 후보는 전부 Review(MlUnavailable) — BidNow 는 없다.
        val reviewIds = response.body?.get("reviewNoticeIds") as? List<*>
        reviewIds?.size shouldBe 1
        reviewIds?.first().toString() shouldBe "${noticeId.number.value}:${noticeId.round.value}"
        (response.body?.get("bidNowNoticeIds") as? List<*>)?.shouldBeEmpty()
        (response.body?.get("skipNoticeIds") as? List<*>)?.shouldBeEmpty()
        (response.body?.get("notReachedNoticeIds") as? List<*>)?.shouldBeEmpty()

        // ⑥ — dry-run 은 outbox 를 건드리지 않는다(존재 단언이 아니라 전후 등식).
        outboxRowCount() shouldBe beforeOutbox

        // ⑦ — 사다리와 알림 요청 경로가 같은 판정을 본다(둘 다 빈 집합이어도 등식 자체가 불변식).
        val bidNowIds = (response.body?.get("bidNowNoticeIds") as? List<*>).orEmpty().toSet()
        val wouldNotifyIds = (response.body?.get("wouldNotifyNoticeIds") as? List<*>).orEmpty().toSet()
        wouldNotifyIds shouldBe bidNowIds
    }
}
