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
import bidvector.workflow.evaluation.EVALUATION_LADDER_POLICY_VERSION
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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
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

/** 심는 전략 개정 — **1 이 아니다**(R2-H-1: 1 은 test 지원 기본값이라 상수 변이를 숨긴다). */
private const val SEEDED_REVISION = 7

/** 거동 축에서 올려 보는 개정. */
private const val RAISED_REVISION = 11

/** 저장된 outbox 행의 두 칸 — 타입 열과 payload 문자열. */
private data class OutboxRow(
    val payloadType: String,
    val payload: String,
)

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
 * **한 번만 부팅한다(cr R-11 로 문면 정정 — 앞 판은 「두 번 부팅」이라고 적었다).** mode 를
 * 켜지 않고 한 번 띄워 Flyway 마이그레이션과 평가 port 싱글턴 배선만 얻고, 전략·공고를 심은
 * 뒤 **러너를 직접 조립해** 돌린다. `mode=once` 로 띄우면 러너가 기동 중에 돌면서
 * `exitProcess` 를 불러 test JVM 을 죽인다 — 그래서 종료 자리를 [RecordedExitCodes] 로
 * 바꿔치운 조립을 손으로 세운다(배선 자체는 `EvaluationCommitWiringTest` 가 든다).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
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

        /**
         * `bidNowThreshold` 0.9 — fake ML 의 priority 0.9 가 확정 `BidNow` 를 낸다.
         *
         * **개정을 1 이 아닌 [SEEDED_REVISION] 으로 심는다(R2-H-1).** 1 은 여러 test 지원
         * 함수의 기본값이라, 1 로 심으면 「개정을 상수 1 로 바꿔치우는」 변이가 우연히 같은
         * 값을 내며 빠져나간다.
         */
        private fun insertStrategy() {
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO operator_strategy (id, revision, focus_categories, focus_region_terms, " +
                            "exclude_region_terms, required_keyword_terms, exclude_keyword_terms, " +
                            "bid_now_threshold, review_threshold, max_active_bids) " +
                            "VALUES (1, $SEEDED_REVISION, ?, ?, ?, ?, ?, ?, ?, ?)",
                    ).use { statement ->
                        val empty = connection.createArrayOf("text", emptyArray<String>())
                        statement.setArray(1, empty)
                        statement.setArray(2, empty)
                        statement.setArray(3, empty)
                        statement.setArray(4, empty)
                        statement.setArray(5, connection.createArrayOf("text", arrayOf("없는-문구")))
                        statement.setBigDecimal(6, java.math.BigDecimal("0.9"))
                        statement.setBigDecimal(7, java.math.BigDecimal("0.1"))
                        statement.setInt(8, MAX_ACTIVE_BIDS)
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
     * **순서 독립을 표 비우기로 만든다**(D-6F10-31 ④, `PersistenceTestSupport` 와 같은 규율).
     * 이 클래스는 컨테이너·컨텍스트를 클래스당 하나만 쓰므로 test 들이 같은 DB 를 물려받는다 —
     * 각 test 가 자기 전제를 **스스로** 세우지 않으면 실행 순서가 답을 바꾼다.
     *
     * 비우는 것은 `outbox` 와 `inbox` 뿐이고 seed(전략·공고·raw)는 남긴다 — 그 셋은
     * `@BeforeAll` 이 한 번 심는 입력이다. 전략 **개정**은 거동 축 test 가 바꾸므로 여기서
     * 되돌린다(그 test 가 중간에 실패해도 다음 test 가 영향을 받지 않는다).
     *
     * **순서를 불리한 쪽으로 고정한다**(`@Order`) — 행을 남기는 거동 축 test 가 **먼저**
     * 돌고, 행 하나를 단언하는 test 가 뒤에 온다. 그러지 않으면 이 비우기가 실제로 지는
     * 짐이 없어 「지워도 초록」이다(실측: JUnit 의 기본 순서에서는 변이가 안 잡혔다).
     * 이제 비우기를 떼면 뒤 test 의 `single()` 이 두 행을 보고 붉는다.
     */
    @BeforeEach
    fun resetRunState() {
        clearOutbox()
        setStrategyRevision(SEEDED_REVISION)
        commitLog.clear()
    }

    /**
     * ①②④ — 커밋 run 이 outbox 행을 남기고, **변한 표가 `outbox` 하나**이며, 종료 코드가 0 이다.
     *
     * 「변한 표 == {outbox}」가 D-6F7-11 의 「도메인 write 만 커밋되고 outbox 행이 없다」가 오늘
     * 성립하지 않는다는 사실의 측정이다 — 오늘 평가에는 outbox 밖 write 가 없다. 판정 기록 표가
     * 생기는 slice 가 이 단언을 다시 받는다(`OPEN-6F10-EVALUATION-DOMAIN-WRITE`).
     */
    @Test
    @Order(2)
    fun `커밋 run 은 outbox 행을 남기고 변한 표는 outbox 하나이며 종료 코드가 0 이다`() {
        val before = rowCountsByTable()

        val exitCodes = runCommitRunner()

        val after = rowCountsByTable()
        changedTables(before, after) shouldBe setOf("outbox")
        after.getValue("outbox") shouldBe before.getValue("outbox") + 1
        exitCodes shouldBe listOf(EvaluationCommitExitCode.COMPLETE.value)
        // R-13 ⓑ — 러너의 마침 줄을 단언한다(앞 판은 로그를 모으기만 했다).
        commitLog.single { it.startsWith("evaluation-commit finished") } shouldContain
            "exit=${EvaluationCommitExitCode.COMPLETE.value}"
        val row = notificationRows().single()
        row.payloadType shouldBe "NotificationRequested"
        row.payload shouldContain "20260101010"
        // **값 축**(R2-H-1) — 판정이 지난 사다리 정책 식별자가 저장된 행에 축어로 있다.
        // `EVALUATION_LADDER_POLICY_VERSION` 은 production 상수이므로 투영이 이 값을 다른
        // 것으로 바꿔치우면 이 단언이 붉어진다.
        row.payload shouldContain EVALUATION_LADDER_POLICY_VERSION.source
    }

    /**
     * **거동 축**(R2-H-1) — 전략 개정 **하나만** 바꾸면 저장되는 payload 가 달라지고, 되돌리면
     * 같은 payload 가 다시 나온다. 같은 공고로 세 번 돌린다(이 slice 는 이중 요청을 막지
     * 않는다, D-6F7-6 — 그래서 같은 공고가 매 run 마다 새 행을 남긴다).
     *
     * 왜 「달라진다 + 되돌아온다」 둘인가: 「달라진다」만 보면 **아무 입력에나 흔들리는**
     * 구현도 초록이고, 「되돌아온다」만 보면 **개정을 무시하는** 구현도 초록이다.
     *
     * 저장 형식을 이 test 가 알지 않는다 — 문자열 두 개를 **서로** 비교한다. `|` 로 쪼개
     * 칸을 집으면 wire 형식 사본이 여기 하나 더 생긴다(그 형식의 정본은 `adapters` 의
     * 어휘 골든이고, 디코더는 그 모듈 `internal` 이라 여기서 부를 수 없다).
     */
    @Test
    @Order(1)
    fun `전략 개정을 올리면 저장되는 payload 가 달라지고 되돌리면 같아진다`() {
        val first = runOnceAndTakePayload()

        setStrategyRevision(RAISED_REVISION)
        val raised = runOnceAndTakePayload()

        setStrategyRevision(SEEDED_REVISION)
        val restored = runOnceAndTakePayload()
        // 다음 test 를 위한 복원은 `resetRunState` 가 진다 — 이 test 가 중간에 죽어도 선다.

        raised shouldNotBe first
        restored shouldBe first
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
    @Order(3)
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

    private fun notificationRows(): List<OutboxRow> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT payload_type, payload FROM outbox " +
                        "WHERE payload_type = 'NotificationRequested' ORDER BY inserted_at, entry_id",
                ).use { statement ->
                    statement.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(OutboxRow(rs.getString(1), rs.getString(2))) }
                    }
                }
        }

    /**
     * 한 run 의 payload — **비우고 돌려** 행 하나를 집는다. 「마지막 행」으로 집지 않는 이유:
     * `inserted_at` 은 production 시각이라 같은 run 들 사이에서 같을 수 있고 `entry_id` 는
     * 시간순이 아니다(UUID) — 정렬로는 「그 run 의 행」을 고를 수 없다.
     */
    private fun runOnceAndTakePayload(): String {
        clearOutbox()
        runCommitRunner() shouldBe listOf(EvaluationCommitExitCode.COMPLETE.value)
        return notificationRows().single().payload
    }

    private fun clearOutbox() =
        dataSource().connection.use { connection ->
            connection.createStatement().use { it.execute("TRUNCATE TABLE outbox, inbox") }
        }

    private fun setStrategyRevision(revision: Int) =
        dataSource().connection.use { connection ->
            connection.prepareStatement("UPDATE operator_strategy SET revision = ? WHERE id = 1").use { statement ->
                statement.setInt(1, revision)
                statement.executeUpdate()
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
