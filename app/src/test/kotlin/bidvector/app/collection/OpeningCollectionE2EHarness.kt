package bidvector.app.collection

import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.wiring.CollectionTerminationTestConfiguration
import bidvector.app.wiring.E2E_FIXED_NOW
import bidvector.app.wiring.FixedClockTestConfiguration
import bidvector.app.wiring.RecordingCollectionTermination
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationPreparedEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationListener
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** 업무마다 여섯 — 마지막 하나는 상세 응답이 비어 원문이 한 줄도 남지 않는다(D-6G-42). */
internal const val NOTICES_PER_SLOT = 6

internal const val TARGET_PER_STRATUM = 2

/** 층 = 업무 × 공고 주 — 이 E2E 는 하루치 두 업무라 층이 둘이다. */
internal const val DIVISIONS = 2

/** 개찰완료 축을 두 쪽으로 나눈다 — 투찰 행 셋이 2 + 1 로 갈린다(D-6G-58). */
internal const val OPENING_COMPLETE_PAGE_SIZE = 2

/** mock 이 공고마다 내는 투찰 행 수 — 전 참가자다. */
internal const val BIDDERS_PER_NOTICE = 3

private const val POSTGRES_IMAGE = "postgres:16.4"
private const val TEST_CREDENTIAL_VALUE = "opening-e2e-test-fixture-credential"
private const val SERVICE_KEY = "OPENING-E2E-SENTINEL+KEY/value="
private const val BIDDER_NAME = "SYN-투찰업체-이름"

/**
 * 개찰 수집 E2E 의 **출하 조립 기동기**. 두 test 클래스가 이것 하나를 쓴다 — 클래스를 가른 축은
 * 「무엇을 부르는가」(표본·원문·조립 타입)와 「얼마나·언제 부르는가」(상한·잠금·이어 돌기)이고,
 * 기동 장치를 두 벌 두면 두 test 가 **서로 다른 조립**을 재게 된다. 그 어긋남은 조용하다.
 *
 * 기동은 `bidvector.opening-collection.mode=once` 로 `app` 의 배선 그대로 뜨고, 바깥 호출은 전부
 * loopback in-process mock 으로 간다(실 KONEPS 호출 0).
 */
internal class OpeningCollectionE2EHarness {
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

    val logs = ListAppender<ILoggingEvent>()

    /** test 마다 새 실행 상태 — 앞 test 의 공고번호(nonce 가 다르다)와 시도를 물려받지 않는다. */
    lateinit var runStateDir: Path
        private set

    fun stop() {
        postgres.stop()
    }

    /** 실행 상태를 **밖에서** 새로 깐다 — seed 나 잠금을 미리 걸어 두는 test 가 쓴다. */
    fun freshRunStateDir(): Path = Files.createTempDirectory("6g-e2e-run-state").also { runStateDir = it }

    /** 전수 표본 — 상세가 비는 마지막 순번까지 표본에 들어야 그 축의 이어 돌기를 잴 수 있다. */
    fun censusSample(): Map<String, String> =
        mapOf("bidvector.opening-collection.sample-size" to (NOTICES_PER_SLOT * DIVISIONS).toString())

    fun pendingLines(): Int = attemptKindCount(runStateDir, "PENDING")

    fun httpLines(): Int = attemptKindCount(runStateDir, "HTTP")

    fun capturedLog(): String = logs.list.joinToString("\n") { it.formattedMessage }

    fun <T> query(
        sql: String,
        read: (ResultSet) -> T,
    ): T = queryOne(dataSource, sql, read)

    fun bootAndRun(
        extra: Map<String, String>,
        nonce: String? = null,
        throttleOnce: Set<String> = emptySet(),
        openingCompletePageSize: Int = 0,
        failOpeningCompleteSecondPageOnce: Boolean = false,
        reuseRunState: Boolean = false,
        now: Instant? = null,
        inspect: (ApplicationContext) -> Unit = {},
    ): Pair<List<Int>, MockOpeningKonepsHttp> {
        logs.list.clear()
        E2E_FIXED_NOW.set(now)
        if (!reuseRunState) freshRunStateDir()
        val mock =
            MockOpeningKonepsHttp(
                noticesPerSlot = NOTICES_PER_SLOT,
                bidderName = BIDDER_NAME,
                nonce = nonce ?: newE2ENonce(),
                throttleOnce = throttleOnce,
                openingCompletePageSize = openingCompletePageSize,
                failOpeningCompleteSecondPageOnce = failOpeningCompleteSecondPageOnce,
            )
        val context =
            SpringApplicationBuilder(
                BidVectorApplication::class.java,
                CollectionTerminationTestConfiguration::class.java,
                FixedClockTestConfiguration::class.java,
            ).properties(
                PRODUCTION_DISPATCH_PROPERTIES + baseProperties(mock) + extra,
            ).listeners(ApplicationListener<ApplicationPreparedEvent> { attachRootLogCapture(logs) })
                .run()
        return try {
            inspect(context)
            context.getBean(RecordingCollectionTermination::class.java).exitCodes.toList() to mock
        } finally {
            context.close()
            mock.close()
            E2E_FIXED_NOW.set(null)
        }
    }

    /** 기동 속성의 바탕 — test 가 `extra` 로 덮어쓴다. */
    private fun baseProperties(mock: MockOpeningKonepsHttp): Map<String, String> =
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
            "bidvector.opening-collection.run-state-dir" to runStateDir.toString(),
            "bidvector.koneps.service-key" to SERVICE_KEY,
            "bidvector.koneps.base-url" to mock.baseUrl,
            "bidvector.koneps.opening.scsbid-base-url" to mock.baseUrl,
        )

    /** 이 mock 이 로그에 새지 않아야 하는 값 둘 — 「로그에 서비스 키도 상호도 없다」가 쓴다. */
    val sentinels: Pair<String, String> get() = SERVICE_KEY to BIDDER_NAME
}
