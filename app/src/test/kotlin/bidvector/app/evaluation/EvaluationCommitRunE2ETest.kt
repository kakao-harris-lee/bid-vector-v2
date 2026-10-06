package bidvector.app.evaluation

import bidvector.adapters.evaluation.EvaluationCommitRun
import bidvector.adapters.evaluation.RequestCapacityPort
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.collection.CollectionLog
import bidvector.app.wiring.RecordedExitCodes
import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
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
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

private const val POSTGRES_IMAGE = "postgres:16.4"
private const val CANDIDATE_CAP = 100
private const val TEST_CREDENTIAL_VALUE = "evaluation-commit-e2e-test-fixture-credential"
private const val MAX_ACTIVE_BIDS = 10
private const val BLOCK_CONSTRAINT = "outbox_commit_run_blocked"

/**
 * **R1-H-1 의 답** — production 커밋 조립(`EvaluationCommitRun`)과 그 러너를 **실 DB 위에서
 * 끝까지 돌린다.** 앞 판은 이 경로를 어떤 test 도 돌리지 않아, 알림 port 를
 * `RecordingNotificationRequestPort` 로 바꿔치워도(= 커밋 러너가 dry-run 이 되어도) test
 * 클래스 240 개가 전부 초록이었다.
 *
 * 이 파일이 한 사슬로 잠그는 것 넷:
 * ① `BidNow` 판정이 **실제 outbox 행**을 남긴다(D-6F10-4) ② 그 run 뒤 **변한 표는 `outbox`
 * 하나**다(「도메인 write 만 커밋되고 outbox 행이 없다」가 성립하지 않음을 **test 로 사실화**
 * — `EvaluationCommitRun` KDoc 이 예고한 그 test 다) ③ **outbox 쓰기 실패 주입** → port 가
 * `Failed` 를 값으로 돌려주고 → `tallyOf` 가 그것을 세고 → 러너가 **비-0 종료 코드**를 낸다
 * (설계 검토 (2) 5행, cr L-2) ④ 성공 run 의 종료 코드는 0 이다.
 *
 * **두 번 부팅하는 이유**: 러너는 `ApplicationRunner` 라 기동 중에 돈다 — 그러므로 전략·공고는
 * 그 **앞에** 심어야 하고, 표는 Flyway 가 만든 뒤여야 한다. 그래서 ⓐ mode 없이 한 번 띄워
 * 마이그레이션·seed 를 하고 ⓑ `mode=once` 로 다시 띄워 러너를 돌린다.
 *
 * `exitProcess` 를 부르는 production 종료 자리는 [RECORDING_TERMINATION_PROFILE] 이 기록으로
 * 바꿔치운다 — 그러지 않으면 이 test 가 test JVM 을 죽인다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EvaluationCommitRunE2ETest {
    companion object {
        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_commit_run_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var seedContext: ConfigurableApplicationContext

        @JvmStatic
        @BeforeAll
        fun migrateAndSeed() {
            seedContext = boot()
            insertStrategy()
            insertNotice("20260101010", Instant.now().plusSeconds(86_400))
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            seedContext.close()
            postgres.stop()
        }

        /**
         * **mode 를 켜지 않고** 띄운다 — Flyway 마이그레이션과 평가 port 싱글턴 배선만 쓴다.
         * 러너는 아래에서 직접 조립한다: `mode=once` 로 띄우면 러너가 기동 중에 돌면서
         * production 종료 자리(`exitProcess`)를 불러 **test JVM 을 죽인다**(실측 — Gradle 은
         * 그것을 「초록 + 남은 test 건너뜀」으로 보고해 초록으로 보이는 실패가 된다).
         * 배선 자체는 `EvaluationCommitWiringTest` 가 든다.
         */
        private fun boot(): ConfigurableApplicationContext =
            SpringApplicationBuilder(BidVectorApplication::class.java)
                .properties(
                    PRODUCTION_DISPATCH_PROPERTIES +
                        mapOf(
                            "server.port" to "0",
                            "spring.main.web-application-type" to "none",
                            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                            "bidvector.persistence.username" to postgres.username,
                            "bidvector.persistence.credential" to postgres.password,
                            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                            "bidvector.evaluation.candidate-cap" to CANDIDATE_CAP.toString(),
                        ),
                ).run()

        private fun dataSource(): DataSource = seedContext.getBean(DataSource::class.java)

        /** `bidNowThreshold` 0.9 — fake ML 의 priority 0.9 가 확정 `BidNow` 를 낸다. */
        private fun insertStrategy() {
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
                        statement.setArray(5, connection.createArrayOf("text", arrayOf("없는-문구")))
                        statement.setBigDecimal(6, java.math.BigDecimal("0.9"))
                        statement.setBigDecimal(7, java.math.BigDecimal("0.1"))
                        statement.setInt(8, 10)
                        statement.executeUpdate()
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
            val key =
                JdbcRawObservationStore(dataSource(), fieldContracts, "commit-run-e2e-test").append(observation)
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
    }

    /**
     * ①②④ — 커밋 run 이 outbox 행을 남기고, **변한 표가 `outbox` 하나**이며, 종료 코드가 0 이다.
     *
     * 「변한 표 == {outbox}」가 D-6F7-11 의 「도메인 write 만 커밋되고 outbox 행이 없다」가 오늘
     * 성립하지 않는다는 사실의 측정이다 — 오늘 평가에는 outbox 밖 write 가 없다. 판정 기록 표가
     * 생기는 slice 가 이 단언을 다시 받는다(`OPEN-6F10-EVALUATION-DOMAIN-WRITE`).
     */
    @Test
    fun `커밋 run 은 outbox 행을 남기고 변한 표는 outbox 하나이며 종료 코드가 0 이다`() {
        val before = rowCountsByTable()

        val exitCodes = runCommitRunner()

        val after = rowCountsByTable()
        changedTables(before, after) shouldBe setOf("outbox")
        after.getValue("outbox") shouldBe before.getValue("outbox") + 1
        exitCodes shouldBe listOf(EvaluationCommitExitCode.COMPLETE.value)
        notificationPayloads().single() shouldContain "20260101010"
    }

    /**
     * ③ — **outbox 쓰기 실패 주입.** `NotificationRequested` payload 의 INSERT 를 거부하는
     * CHECK 제약을 걸어 `OutboxNotificationRequestPort` 가 `SQLException → Failed` 로 가게
     * 하고, 그 값이 `tallyOf` 를 지나 러너의 **비-0 종료 코드**로 올라오는지 잰다.
     *
     * 이 사슬의 가운데 고리(`tallyOf`)가 앞 판에서 미측정이었다 — `outcomeOf` 의 두 갈래를
     * 뒤집는 변이가 전 test 초록이었다(cr L-2).
     */
    @Test
    fun `outbox 쓰기가 실패하면 판정은 남고 러너는 INCOMPLETE 2 로 끝난다`() {
        blockNotificationInserts()
        val before = rowCountsByTable()
        try {
            val exitCodes = runCommitRunner()

            exitCodes shouldBe listOf(EvaluationCommitExitCode.INCOMPLETE.value)
            // 행이 하나도 안 생겼다 = 쓰기가 실제로 거부됐다(주입이 들었다는 증거).
            changedTables(before, rowCountsByTable()).shouldBeEmpty()
        } finally {
            unblockNotificationInserts()
        }
    }

    /**
     * **production 조립을 직접 세워 러너 본문을 돌린다** — `EvaluationCommitRun` 과
     * `EvaluationCommitRunner` 는 production 클래스고, 바꿔치우는 것은 셋뿐이다: ML port(판정이
     * `BidNow` 에 닿게 하는 fake), 로그 출구, 종료 자리. 나머지 port 는 **배선된 빈 그대로**
     * 쓴다(전략 저장소·후보 원천·감시·면허·시각·correlation id).
     */
    private fun runCommitRunner(): List<Int> {
        val termination = RecordedExitCodes()
        val run =
            EvaluationCommitRun(
                dataSource = dataSource(),
                strategies = seedContext.getBean(StrategyRepository::class.java),
                candidateSource = seedContext.getBean(CandidateSourcePort::class.java),
                watchSubjects = seedContext.getBean(WatchSubjectPort::class.java),
                licenseGate = seedContext.getBean(LicenseGatePort::class.java),
                mlAnalysis = AlwaysBidNowMl(),
                capacity = RequestCapacityPort(0, MAX_ACTIVE_BIDS),
                correlationIds = seedContext.getBean(CorrelationIdFactory::class.java),
                clock = seedContext.getBean(Clock::class.java),
                analysisBudget = null,
            )
        EvaluationCommitRunner(run, CANDIDATE_CAP, CollectionLog { commitLog += it }, termination)
            .run(DefaultApplicationArguments())
        return termination.recorded()
    }

    private val commitLog = mutableListOf<String>()

    private fun rowCountsByTable(): Map<String, Long> =
        dataSource().connection.use { connection ->
            val tables = mutableListOf<String>()
            connection
                .prepareStatement(
                    "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                ).use { statement ->
                    statement.executeQuery().use { rs -> while (rs.next()) tables += rs.getString(1) }
                }
            tables.associateWith { table ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM \"$table\"").use { rs ->
                        check(rs.next())
                        rs.getLong(1)
                    }
                }
            }
        }

    private fun changedTables(
        before: Map<String, Long>,
        after: Map<String, Long>,
    ): Set<String> = before.keys.filter { before.getValue(it) != after.getValue(it) }.toSet()

    private fun notificationPayloads(): List<String> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("SELECT payload FROM outbox WHERE payload_type = 'NotificationRequested'")
                .use { statement ->
                    statement.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(rs.getString(1)) }
                    }
                }
        }

    private fun blockNotificationInserts() =
        dataSource().connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "ALTER TABLE outbox ADD CONSTRAINT $BLOCK_CONSTRAINT " +
                        "CHECK (payload_type <> 'NotificationRequested')",
                )
            }
        }

    private fun unblockNotificationInserts() =
        dataSource().connection.use { connection ->
            connection.createStatement().use { it.execute("ALTER TABLE outbox DROP CONSTRAINT $BLOCK_CONSTRAINT") }
        }
}

/**
 * priority 0.9(전략의 `bidNowThreshold` 와 같은 값 — `VerdictLadder.priorityBidNowOutcome` 가
 * `>=` 로 승격한다)를 내는 fake. `BidNowFakeMlAnalysisTestConfiguration` 과 같은 형태지만 그쪽은
 * `@TestConfiguration` 이라 **명시 등록이 필요한** 배선용이고, 이 test 는 Spring 을 지나지 않고
 * 조립을 직접 세우므로 지역 fake 를 쓴다.
 */
private class AlwaysBidNowMl : MlAnalysisPort {
    override suspend fun analyze(
        notice: bidvector.procurement.Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome =
        MlAnalysisOutcome.Analyzed(
            priorityScore = UnitScore(java.math.BigDecimal("0.9")),
            probabilityScore = null,
            matchedScore = null,
            evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided),
        )
}
