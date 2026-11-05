package bidvector.app.collection

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.adapters.result.JdbcAwardWinnerNameView
import bidvector.adapters.result.JdbcBatchOpeningFactsView
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.wiring.CollectionTerminationTestConfiguration
import bidvector.app.wiring.E2E_FIXED_NOW
import bidvector.app.wiring.FixedClockTestConfiguration
import bidvector.app.wiring.RecordingCollectionTermination
import bidvector.procurement.BusinessDivision
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.settlement.AWARD_MATCHING_POLICY
import bidvector.settlement.AwardMatchingPolicyData
import bidvector.settlement.AwardWinnerTradeName
import bidvector.settlement.OperatorTradeName
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleCandidate
import bidvector.workflow.collection.SampleConfirmation
import bidvector.workflow.collection.SampleOutcome
import bidvector.workflow.collection.SampleScope
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import bidvector.workflow.opening.OPENING_PROGRESS_POLICY
import bidvector.workflow.opening.resolveOpeningResultView
import bidvector.workflow.result.ReconciliationVerdict
import bidvector.workflow.result.reconcileBid
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** 업무마다 내는 낙찰 목록 행 수 — 두 업무라 한 스윕의 원문은 이 수의 두 배다. */
internal const val AWARD_NOTICES_PER_SLOT = 3

/** 낙찰자 상호 표지 — 원문에는 남고 **로그에는 없어야** 한다. */
internal const val AWARD_WINNER_NAME = "SYN-낙찰업체-이름"

private const val POSTGRES_IMAGE = "postgres:16.4"
private const val TEST_CREDENTIAL_VALUE = "award-e2e-test-fixture-credential"
private const val SERVICE_KEY = "AWARD-E2E-SENTINEL+KEY/value="

/**
 * 낙찰 목록 수집 E2E 의 **출하 조립 기동기**(M9/award-ingest).
 *
 * 한 컨테이너 안에서 두 갈래를 **따로** 기동한다: 낙찰 수집(`bidvector.award-collection.mode=once`)과
 * 기존 승격(`bidvector.opening-promote.mode=once`). 한 프로세스에 함께 켤 수 없기 때문이고(일회 러너는
 * 하나다), 그 분리가 곧 이 slice 의 주장이다 — 수집은 원문만 적재하고 canonical 칸은 **고치지 않은**
 * 승격 러너가 채운다.
 *
 * 바깥 호출은 전부 loopback in-process mock 으로 간다(실 조달청 호출 0).
 */
internal class AwardCollectionE2EHarness {
    private val postgres: PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
            .withDatabaseName("bidvector_award_e2e_test")
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

    private var lastStdio: String = ""

    /** 낙찰 수집 갈래의 실행 상태 — **승격 쪽과 다른 디렉터리**다(상한 회계가 갈래마다 따로다). */
    lateinit var awardRunStateDir: Path
        private set

    /** 승격이 읽는 실행 상태 — 확정 표본이 여기 있다(수집이 아니라 test 가 심는다). */
    lateinit var promoteRunStateDir: Path
        private set

    fun stop() {
        postgres.stop()
    }

    fun freshRunStateDirs() {
        awardRunStateDir = Files.createTempDirectory("m9-award-run-state")
        promoteRunStateDir = Files.createTempDirectory("m9-promote-run-state")
    }

    fun capturedLog(): String = logs.list.joinToString("\n") { it.formattedMessage }

    /** 부재 단언의 대상 — 메시지·예외 cause 체인·표준 출력·오류 전부(형제 갈래와 같은 규율). */
    fun capturedEverything(): String =
        logs.list.joinToString("\n") { event ->
            event.formattedMessage + "\n" + event.throwableProxy?.let(ThrowableProxyUtil::asString).orEmpty()
        } + "\n" + lastStdio

    /** 실행 상태 디렉터리의 파일 전부 — 원장·표본·장부에 표지가 적히지 않았는가. */
    fun runStateText(): String =
        listOf(awardRunStateDir, promoteRunStateDir).joinToString("\n") { dir ->
            Files
                .list(dir)
                .use { paths -> paths.filter { Files.isRegularFile(it) }.toList() }
                .joinToString("\n") { runCatching { Files.readString(it) }.getOrDefault("") }
        }

    val serviceKeySentinel: String get() = SERVICE_KEY

    fun <T> query(
        sql: String,
        read: (ResultSet) -> T,
    ): T = queryOne(dataSource, sql, read)

    /** 이 nonce 로 mock 이 낼 공고 — test 가 수집 **전에** 그 공고의 개찰 축을 심을 수 있게 한다. */
    fun constructionNoticeId(
        nonce: String,
        index: Int = 1,
    ): NoticeId =
        NoticeId(
            NoticeNumber.of(awardNoticeNumber(nonce, "Cnstwk", index)),
            NoticeRound.of(AWARD_NOTICE_ROUND),
        )

    /**
     * 「낙찰자 상호만 비어 있던 상태」를 짓는다 — 공고 행과 **개찰결과 목록** 축 원문(진행구분·참가
     * 업체수)이다. 낙찰 목록 축은 심지 않는다: 그것을 가져오는 것이 이 slice 의 수집이다.
     */
    fun seedOpeningAxisBeforeAward(id: NoticeId) {
        val policy = (KONEPS_COLLECTION_POLICY.resolve(today) as Resolution.Resolved).value
        val rawStore = JdbcRawObservationStore(dataSource, policy.fieldContracts, "award-e2e")
        val noticeObservation = identityObservation(id, SourceEndpoint.NOTICE_LIST)
        JdbcNoticeRepository(dataSource).persist(
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = Instant.now().minusSeconds(SECONDS_PER_DAY),
                openingScheduledAt = null,
                raw = noticeObservation,
            ),
            rawStore.append(noticeObservation),
        )
        rawStore.append(
            RawNoticeObservation.of(
                mapOf(
                    NOTICE_NUMBER_RAW_KEY to id.number.value,
                    NOTICE_ROUND_RAW_KEY to id.round.value,
                    RawKey("progrsDivCdNm") to OPENED_PROGRESS_LABEL,
                    RawKey("prtcptCnum") to "4",
                ),
                SourceEndpoint.OPENING_RESULT_LIST,
                Instant.now(),
            ),
        )
    }

    /** 승격 범위 — 기존 갈래의 확정 표본이다(낙찰 수집이 표본을 확정하지 않는다). */
    fun confirmSample(ids: List<NoticeId>) {
        val keys = ids.map { NoticeKeyHash.of(it.number.value, it.round.value) }
        val strataByKey = keys.associateWith { SampleCandidate(it, BusinessDivision.CONSTRUCTION, today).stratum }
        val runState = RunStateDirectory(promoteRunStateDir)
        try {
            val outcome =
                SampleOutcome(
                    selected = keys,
                    strata = emptyMap(),
                    strataByKey = strataByKey,
                    requested = keys.size,
                )
            runState.sampleList.confirm(
                SampleConfirmation(
                    outcome,
                    SampleScope(today, today, setOf(BusinessDivision.CONSTRUCTION)),
                ),
            )
        } finally {
            runState.close()
        }
    }

    /**
     * SET-01 대사 한 건을 **실 DB 왕복으로** 판정한다 — 공고 사실과 낙찰자 상호를 출하 읽기 어댑터
     * 둘에서 읽고, 판정은 출하와 같은 순수 함수가 한다. 운영자 상호는 프로필이 주는 값이라 인자다.
     */
    fun verdictOf(
        id: NoticeId,
        operatorName: String,
    ): ReconciliationVerdict {
        val facts = JdbcBatchOpeningFactsView(dataSource).lookupAll(setOf(id)).getValue(id)
        val winner =
            JdbcAwardWinnerNameView(dataSource).winnerNames(setOf(id))[id] ?: AwardWinnerTradeName.NotPublished
        val matching = (AWARD_MATCHING_POLICY.resolve(today) as Resolution.Resolved<AwardMatchingPolicyData>).value
        return reconcileBid(
            resolveOpeningResultView(facts, Instant.now(), OPENING_PROGRESS_POLICY),
            OperatorTradeName.Declared(operatorName),
            winner,
            matching,
        )
    }

    /** 낙찰 목록 수집 갈래를 기동한다 — 종료 코드와 그 실행이 쓴 mock 을 돌려준다. */
    fun bootAward(
        nonce: String,
        extra: Map<String, String> = emptyMap(),
        noticesPerSlot: Int = AWARD_NOTICES_PER_SLOT,
        pageSize: Int = 0,
        rejectAfterFirstPageWith: String? = null,
    ): Pair<List<Int>, MockAwardKonepsHttp> =
        captured {
            val started =
                MockAwardKonepsHttp(
                    noticesPerSlot,
                    nonce,
                    AWARD_WINNER_NAME,
                    pageSize,
                    rejectAfterFirstPageWith,
                )
            try {
                boot(awardProperties(started) + extra) to started
            } finally {
                // **`finally` 다**(형제 기동기와 같은 자리) — 기동이 던지는 판(상한 구성 오류·배선 거부)이
                // 이 E2E 의 단언 대상이고, 그때 서버를 닫지 않으면 실패한 기동마다 loopback 서버와 그
                // 스레드 풀이 test JVM 에 남는다.
                started.close()
            }
        }

    /** 기존 승격 갈래를 기동한다 — **이 slice 가 고치지 않은** 배선이다. */
    fun bootPromote(extra: Map<String, String> = emptyMap()): List<Int> = captured { boot(promoteProperties() + extra) }

    private fun <T> captured(body: () -> T): T {
        logs.list.clear()
        val stdio = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        System.setOut(PrintStream(stdio, true, StandardCharsets.UTF_8))
        System.setErr(PrintStream(stdio, true, StandardCharsets.UTF_8))
        try {
            E2E_FIXED_NOW.set(null)
            return body()
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            lastStdio = stdio.toString(StandardCharsets.UTF_8)
        }
    }

    private fun boot(properties: Map<String, String>): List<Int> {
        var context: ConfigurableApplicationContext? = null
        try {
            context =
                SpringApplicationBuilder(
                    BidVectorApplication::class.java,
                    CollectionTerminationTestConfiguration::class.java,
                    FixedClockTestConfiguration::class.java,
                ).properties(PRODUCTION_DISPATCH_PROPERTIES + baseProperties() + properties)
                    .listeners(ApplicationListener<ApplicationPreparedEvent> { attachRootLogCapture(logs) })
                    .run()
            return context.getBean(RecordingCollectionTermination::class.java).exitCodes.toList()
        } finally {
            context?.close()
        }
    }

    private fun baseProperties(): Map<String, String> =
        mapOf(
            "server.port" to "0",
            "spring.profiles.active" to "collection-e2e",
            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
            "bidvector.persistence.username" to postgres.username,
            "bidvector.persistence.credential" to postgres.password,
            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
            "bidvector.evaluation.candidate-cap" to "1000",
        )

    private fun awardProperties(mock: MockAwardKonepsHttp): Map<String, String> =
        mapOf(
            "bidvector.award-collection.mode" to "once",
            "bidvector.award-collection.from" to today.toString(),
            "bidvector.award-collection.to" to today.toString(),
            "bidvector.award-collection.categories" to "construction,service",
            "bidvector.award-collection.calls-per-day" to "100",
            "bidvector.award-collection.calls-total" to "100",
            "bidvector.award-collection.run-state-dir" to awardRunStateDir.toString(),
            "bidvector.koneps.service-key" to SERVICE_KEY,
            "bidvector.koneps.award.scsbid-base-url" to mock.baseUrl,
        )

    private fun promoteProperties(): Map<String, String> =
        mapOf(
            "bidvector.opening-promote.mode" to "once",
            "bidvector.opening-promote.run-state-dir" to promoteRunStateDir.toString(),
            "bidvector.opening-promote.reference-date" to today.toString(),
        )

    private fun identityObservation(
        id: NoticeId,
        endpoint: SourceEndpoint,
    ): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(NOTICE_NUMBER_RAW_KEY to id.number.value, NOTICE_ROUND_RAW_KEY to id.round.value),
            endpoint,
            Instant.now(),
        )
}

/** 진행구분 라벨 — 정책이 이 라벨을 개찰완료로 옮긴다(판정이 상호를 보는 갈래로 들어간다). */
internal const val OPENED_PROGRESS_LABEL = "개찰완료"

private const val SECONDS_PER_DAY = 86_400L
