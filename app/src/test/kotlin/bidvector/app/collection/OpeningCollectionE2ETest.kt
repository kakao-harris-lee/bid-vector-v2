package bidvector.app.collection

import bidvector.adapters.persistence.JdbcCollectedAxisStore
import bidvector.adapters.persistence.JdbcCollectionCallLedgerStore
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.wiring.CollectionTerminationTestConfiguration
import bidvector.app.wiring.OPENING_COLLECTION_LOCK_KEY
import bidvector.app.wiring.RecordingCollectionTermination
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.CollectionCallLedgerStore
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationPreparedEvent
import org.springframework.context.ApplicationListener
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/**
 * M6/6G D-6G-1·11·19·20 E2E — **출하 조립**을 `bidvector.opening-collection.mode=once` 로 부팅해
 * mock KONEPS → 표본틀 → 표본 → 상세 넷 → 원문 적재까지 끝에서 끝으로 잰다(실 KONEPS 호출 없음).
 *
 * 잠그는 것: ① 표본에 뽑힌 공고만 상세를 부른다 ② 공사는 A값까지 넷, 용역은 셋 ③ 호출 상한에 닿으면
 * 멈추고 종료 코드가 미완이다 ④ 원문이 `raw_observation` 에 남는다 ⑤ 로그에 서비스 키도 상호도 없다.
 */
class OpeningCollectionE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "opening-e2e-test-fixture-credential"
        private const val SERVICE_KEY = "OPENING-E2E-SENTINEL+KEY/value="
        private const val BIDDER_NAME = "SYN-투찰업체-이름"
        private const val NOTICES_PER_SLOT = 4
        private const val TARGET_PER_STRATUM = 2

        /** 층 = 업무 × 공고 주 — 이 E2E 는 하루치 두 업무라 층이 둘이다. */
        private const val DIVISIONS = 2

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_opening_e2e_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private val dataSource: DataSource =
            org.postgresql.ds.PGSimpleDataSource().apply {
                setUrl(postgres.jdbcUrl)
                user = postgres.username
                password = postgres.password
            }

        private val today: LocalDate = LocalDate.now(OPENING_DATE_ZONE)
        private val logs = ListAppender<ILoggingEvent>()

        @AfterAll
        @JvmStatic
        fun stop() {
            postgres.stop()
        }
    }

    /** test 마다 새 표본 목록 파일 — 앞 test 의 공고번호(nonce 가 다르다)를 표본으로 물려받지 않는다. */
    private lateinit var sampleListFile: Path

    private fun bootAndRun(
        extra: Map<String, String>,
        inspect: (org.springframework.context.ApplicationContext) -> Unit = {},
    ): Pair<List<Int>, MockOpeningKonepsHttp> {
        logs.list.clear()
        sampleListFile = Files.createTempDirectory("6g-e2e-sample").resolve("sample-list.tsv")
        val mock = MockOpeningKonepsHttp(noticesPerSlot = NOTICES_PER_SLOT, bidderName = BIDDER_NAME)
        val context =
            SpringApplicationBuilder(
                BidVectorApplication::class.java,
                CollectionTerminationTestConfiguration::class.java,
            ).properties(
                PRODUCTION_DISPATCH_PROPERTIES +
                    mapOf(
                        "server.port" to "0",
                        "spring.profiles.active" to "collection-e2e",
                        "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                        "bidvector.persistence.username" to postgres.username,
                        "bidvector.persistence.credential" to postgres.password,
                        "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                        "bidvector.evaluation.candidate-cap" to "1000",
                        "bidvector.opening-collection.mode" to "once",
                        "bidvector.opening-collection.from" to today.toString(),
                        "bidvector.opening-collection.to" to today.toString(),
                        "bidvector.opening-collection.categories" to "construction,service",
                        "bidvector.opening-collection.sampling-seed" to "6g-e2e-seed",
                        "bidvector.opening-collection.sample-size" to (TARGET_PER_STRATUM * DIVISIONS).toString(),
                        "bidvector.opening-collection.calls-per-day" to "1000",
                        "bidvector.opening-collection.calls-total" to "1000",
                        // **test 마다 다른 예산 시작 시점.** 원장이 영속이라(D-6G-29 ①) 앞 test 가
                        // 남긴 collection_run 행이 다음 test 의 상한을 갉아먹는다 — 그것이 영속이
                        // 실제로 동작한다는 증거이기도 하다.
                        "bidvector.opening-collection.budget-since" to Instant.now().toString(),
                        "bidvector.opening-collection.sample-list-file" to sampleListFile.toString(),
                        "bidvector.koneps.service-key" to SERVICE_KEY,
                        "bidvector.koneps.base-url" to mock.baseUrl,
                        "bidvector.koneps.opening.scsbid-base-url" to mock.baseUrl,
                    ) + extra,
            ).listeners(ApplicationListener<ApplicationPreparedEvent> { attachLogCapture() })
                .run()
        return try {
            inspect(context)
            context.getBean(RecordingCollectionTermination::class.java).exitCodes.toList() to mock
        } finally {
            context.close()
            mock.close()
        }
    }

    private fun attachLogCapture() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        root.level = Level.DEBUG
        (LoggerFactory.getLogger("com.sun.net.httpserver") as Logger).level = Level.WARN
        if (!logs.isStarted) logs.start()
        if (!root.isAttached(logs)) root.addAppender(logs)
    }

    private fun <T> query(
        sql: String,
        read: (ResultSet) -> T,
    ): T =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    rows.next()
                    read(rows)
                }
            }
        }

    @Test
    fun `표본에 뽑힌 공고만 상세를 부르고 공사는 A값까지 넷을 부른다`() {
        val (exitCodes, mock) = bootAndRun(emptyMap())

        exitCodes shouldContainExactly listOf(0)
        // 업무 둘 × 공고일 하나 = 목록 슬롯 둘. 층마다 목표 2 씩 = 표본 넷(표본틀 여덟 가운데).
        mock.listCalls.size shouldBe 2
        mock.reservePriceNotices.size shouldBe TARGET_PER_STRATUM * 2
        mock.openingCompleteNotices.size shouldBe TARGET_PER_STRATUM * 2
        mock.baseAmountNotices.size shouldBe TARGET_PER_STRATUM * 2
        // A값은 공사 층에서만 — 용역 표본에는 안 나간다.
        mock.formulaANotices.size shouldBe TARGET_PER_STRATUM
        // 공고번호는 canonical 화에서 대문자로 선다 — 요청에 실려 나가는 것은 그 canonical 값이다.
        mock.formulaANotices.forEach { it shouldContain "CNSTWK" }
        // 표본틀 전체(8)를 부르지 않았다.
        mock.reservePriceNotices.size shouldBe 4
    }

    @Test
    fun `원문이 적재되고 어느 로그에도 서비스 키와 상호가 없다`() {
        bootAndRun(emptyMap())

        val rawRows = query("SELECT count(*) FROM raw_observation") { it.getInt(1) }
        rawRows shouldBeGreaterThan 0
        val captured = logs.list.joinToString("\n") { it.formattedMessage }
        captured shouldNotContain SERVICE_KEY
        captured shouldNotContain "ServiceKey"
        // 상호는 raw 관측까지는 오지만 **로그에는 없다**(러너의 줄이 계수와 열거값뿐이다).
        captured shouldNotContain BIDDER_NAME
        captured shouldContain "opening-collection finished"
    }

    /**
     * `@ConditionalOnMissingBean` 은 배선 조건 test 가 DB 없이 기동 조건을 재게 하려고 연 자리다.
     * 그 자리가 **출하에서도** 열려 있으면 승인 상한을 세는 원장과 이어 돌기 저장소가 조용히 다른
     * 빈으로 갈릴 수 있다 — 메모리 대역이 서면 상한은 매 기동 0 에서 시작하고 이어 돌기는 아무것도
     * 기억하지 못한다. 실 DB 로 뜬 **출하 조립**에서 실제 타입을 잰다(D-6G-44).
     */
    @Test
    fun `출하 조립의 원장과 이어 돌기는 JDBC 구현이다`() {
        bootAndRun(emptyMap()) { context ->
            context
                .getBean(CollectionCallLedgerStore::class.java)
                .shouldBeInstanceOf<JdbcCollectionCallLedgerStore>()
            context.getBean(CollectedAxisStore::class.java).shouldBeInstanceOf<JdbcCollectedAxisStore>()
        }
    }

    /**
     * D-6G-42 M-3 — 겹쳐 도는 두 실행은 같은 표본을 두 번 부르고 두 상한 회계가 서로의 호출을 보지
     * 못한다. 잠금을 **밖에서 들고** 기동해, 출하 조립이 실제로 아무것도 부르지 않고 끝나는지 잰다.
     */
    @Test
    fun `이미 도는 실행이 있으면 아무것도 부르지 않고 끝난다`() {
        dataSource.connection.use { held ->
            held.prepareStatement("SELECT pg_advisory_lock(?)").use { statement ->
                statement.setLong(1, OPENING_COLLECTION_LOCK_KEY)
                statement.executeQuery().use { it.next() }
            }

            val (exitCodes, mock) = bootAndRun(emptyMap())

            exitCodes shouldContainExactly listOf(CollectionExitCode.ALREADY_RUNNING.value)
            mock.listCalls.shouldBeEmpty()
            logs.list.joinToString("\n") { it.formattedMessage } shouldContain "ALREADY_RUNNING"
        }
    }

    /**
     * 출하 조립이 표본을 **파일로** 확정한다(D-6G-39) — 다음 실행이 다시 뽑지 못하게 하는 것은 이
     * 파일이다. 파일이 없으면 「결과를 보기 전에 확정했다」는 실행 로그의 주장일 뿐이다.
     */
    @Test
    fun `표본 목록이 저장소 밖 파일로 확정된다 — 층마다 목표만큼`() {
        bootAndRun(emptyMap())

        val lines = Files.readString(sampleListFile).trimEnd('\n').lines()
        lines shouldHaveSize TARGET_PER_STRATUM * DIVISIONS
        val hashes = lines.map { it.substringBefore('\t') }
        hashes shouldContainExactly hashes.sorted()
        lines.map { it.split('\t')[1] }.toSet() shouldBe setOf("CONSTRUCTION", "SERVICE")
        val captured = logs.list.joinToString("\n") { it.formattedMessage }
        captured shouldContain "sampled=${lines.size} "
    }

    @Test
    fun `호출 상한에 닿으면 멈추고 종료 코드가 미완이며 반만 받은 공고가 없다`() {
        val (exitCodes, mock) =
            bootAndRun(
                mapOf(
                    // 일 상한만 낮춘다 — 총 상한을 같이 낮추면 TOTAL 이 먼저 물어 사유가 바뀐다.
                    "bidvector.opening-collection.calls-per-day" to "6",
                    "bidvector.opening-collection.calls-total" to "1000",
                ),
            )

        exitCodes shouldContainExactly listOf(CollectionExitCode.INCOMPLETE.value)
        val captured = logs.list.joinToString("\n") { it.formattedMessage }
        captured shouldContain "opening-collection halted budgetLimit=DAILY"
        // 상한을 넘겨 쓰지 않는다. **정확한 수를 고정하지 않는다** — 표본 순서가 공고 키 해시로
        // 정해지고 mock 의 번호에 실행마다 다른 표식이 들어가, 먼저 뽑히는 층(공사 4축·용역 3축)이
        // 실행마다 달라진다. 계약은 「넘겨 쓰지 않는다」이지 「정확히 6 이다」가 아니다.
        (mock.listCalls.size + mock.detailCallCount()) shouldBeLessThanOrEqual 6
        // **반만 받은 공고가 없다**(D-6G-29 ④) — 상세를 하나라도 받은 공고는 자기 축 전부를 받았다.
        val touched =
            (mock.reservePriceNotices + mock.openingCompleteNotices + mock.baseAmountNotices).toSet()
        touched.forEach { notice ->
            mock.reservePriceNotices shouldContain notice
            mock.openingCompleteNotices shouldContain notice
            mock.baseAmountNotices shouldContain notice
            if (notice.contains("CNSTWK")) mock.formulaANotices shouldContain notice
        }
    }
}
