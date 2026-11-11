package bidvector.app.evaluation

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.adapters.persistence.ReleaseIdentifier
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.decision.MlUnavailableReason
import bidvector.decision.PublishedFloorExclusion
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
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.event.CorrelationId
import io.kotest.matchers.shouldBe
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** M9/OPS-10 — 이 조립이 쓰는 행에 찍히는 릴리스 라벨(test 고정값) — 속성과 저장소가 같은 값을 쓴다. */
private const val TEST_RELEASE_LABEL = "evaluation-commit-run-e2e-release"

private const val POSTGRES_IMAGE = "postgres:16.4"
private const val TEST_CREDENTIAL_VALUE = "evaluation-commit-e2e-test-fixture-credential"

/**
 * `EvaluationCommitRunE2ETest` 의 **전제를 세우는 자리** — 컨테이너·컨텍스트 수명과 seed 셋
 * (전략·공고·raw)만 든다. 가르는 선은 [rowCountsByTable] 묶음(`CommitRunDbSupport`)과 같다:
 * 여기 모인 것은 전부 「입력을 세우고 조립을 얻는 일」이고, 남는 파일은 **무엇을 단언하는가**만 든다.
 *
 * **수명은 여전히 클래스당 하나다** — `object` 가 아니라 class 이고, test 클래스의 `@BeforeAll` 이
 * 세우고 `@AfterAll` 이 닫는다. 싱글턴으로 두면 한 번 닫힌 컨테이너를 다음 클래스가 되살릴 수 없고,
 * 「이 test 들이 같은 DB 를 물려받는다」는 전제(순서 독립을 비우기로 만드는 규율)가 파일 밖으로 새어
 * 읽는 사람이 그 결합을 보지 못한다.
 *
 * **심는 값은 생성자로 받는다.** 그 값이 왜 그 값인지(개정이 1 이 아닌 이유 같은 것)는 **단언하는
 * 쪽의 근거**라 test 파일에 남는다 — 이 자리는 받은 값을 심을 뿐이다. 같은 이름의 상수를 패키지의
 * 다른 test 파일들이 **다른 값으로** 각자 private 하게 들고 있어서(개정 7 ↔ 907, 상한 100 ↔ 5),
 * 공유 상수로 끌어올리면 그 파일들의 이름이 조용히 이쪽을 가리킬 수 있다.
 */
internal class CommitRunE2EFixture(
    private val seededRevision: Int,
    private val maxActiveBids: Int,
    private val candidateCap: Int,
) {
    private val postgres: PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
            .withDatabaseName("bidvector_commit_run_test")
            .withUsername("bidvector_admin")
            .withPassword("bidvector_test_only")

    private lateinit var seedContext: ConfigurableApplicationContext

    /** 이 fixture 가 찍는 릴리스 — 러너·relay 조립이 같은 값을 받아야 행과 속성이 어긋나지 않는다. */
    val release: ReleaseIdentifier = ReleaseIdentifier(TEST_RELEASE_LABEL)

    fun start() {
        postgres.start()
        seedContext = boot()
        insertStrategy()
        insertNotice("20260101010", Instant.now().plusSeconds(86_400))
    }

    fun stop() {
        seedContext.close()
        postgres.stop()
    }

    /** 최소 권한 역할로 연결하는 **production 빈** — 조립이 실제로 지나는 자리다. */
    fun dataSource(): DataSource = seedContext.getBean(DataSource::class.java)

    /**
     * 컨테이너 **소유자** 자격 — 전제를 세우고(권한 밖 `TRUNCATE`) 표 모집단을 역할로 걸러지지 않은
     * 채 읽는 자리다. 비풀링이라 호출마다 새로 여는 것이 값싸다(`adminDataSource` KDoc).
     */
    fun adminDataSource(): DataSource = bidvector.app.adminDataSource(postgres)

    /** 배선된 production 빈 — 러너 조립이 바꿔치우지 않는 port 들을 이 자리로 얻는다. */
    fun <T : Any> bean(type: Class<T>): T = seedContext.getBean(type)

    /**
     * **mode 를 켜지 않고** 띄운다 — Flyway 마이그레이션과 평가 port 싱글턴 배선만 쓴다.
     * 러너는 test 가 직접 조립한다: `mode=once` 로 띄우면 러너가 기동 중에 돌면서
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
                        // M9/OPS-10 — `bidvector.release.sha` 는 기본값이 없다(`ReleaseWiring`).
                        // 값을 주지 않으면 출하 조립이 바인딩 실패로 뜨지 않는다.
                        "bidvector.release.sha" to TEST_RELEASE_LABEL,
                        "bidvector.persistence.username" to postgres.username,
                        "bidvector.persistence.credential" to postgres.password,
                        "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                        "bidvector.evaluation.candidate-cap" to candidateCap.toString(),
                    ),
            ).run()

    /** `bidNowThreshold` 0.9 — fake ML 의 priority 0.9 가 확정 `BidNow` 를 낸다. */
    private fun insertStrategy() {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO operator_strategy (id, revision, focus_categories, focus_region_terms, " +
                        "exclude_region_terms, required_keyword_terms, exclude_keyword_terms, " +
                        "bid_now_threshold, review_threshold, max_active_bids) " +
                        "VALUES (1, $seededRevision, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    val empty = connection.createArrayOf("text", emptyArray<String>())
                    statement.setArray(1, empty)
                    statement.setArray(2, empty)
                    statement.setArray(3, empty)
                    statement.setArray(4, empty)
                    statement.setArray(5, connection.createArrayOf("text", arrayOf("없는-문구")))
                    statement.setBigDecimal(6, java.math.BigDecimal("0.9"))
                    statement.setBigDecimal(7, java.math.BigDecimal("0.1"))
                    statement.setInt(8, maxActiveBids)
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
        val key = JdbcRawObservationStore(dataSource(), fieldContracts, release).append(observation)
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
 * priority 0.9(전략의 `bidNowThreshold` 와 같은 값 — `VerdictLadder.priorityBidNowOutcome` 가
 * `>=` 로 승격한다)를 내는 fake. `BidNowFakeMlAnalysisTestConfiguration` 과 같은 형태지만 그쪽은
 * `@TestConfiguration` 이라 **명시 등록이 필요한** 배선용이고, 이 test 는 Spring 을 지나지 않고
 * 조립을 직접 세우므로 지역 fake 를 쓴다.
 */
internal class AlwaysBidNowMl : MlAnalysisPort {
    override suspend fun analyze(
        notice: bidvector.procurement.Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome =
        MlAnalysisOutcome.Analyzed(
            priorityScore = UnitScore(java.math.BigDecimal("0.9")),
            probabilityScore = null,
            matchedScore = null,
            evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided),
            // DEC-10(r1 CR H-1) — **non-null 로 둔다.** 이 값이 `Analyzed` → `Reached` → `tallyOf`
            // → 마침 줄을 지나는지가 그 test 의 축 하나다. `null` 로 두면 그 세 고리가 전부
            // 미측정이 된다(앞 판이 그랬다 — 전건 초록인데 가운데가 끊겨도 보이지 않았다).
            floorExclusion = PublishedFloorExclusion.IMPLAUSIBLE,
        )
}
