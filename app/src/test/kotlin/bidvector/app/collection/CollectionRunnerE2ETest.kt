package bidvector.app.collection

import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.wiring.RecordingCollectionTermination
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.spi.FilterReply
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.LoggerFactory
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import javax.sql.DataSource

/**
 * D-6F8-1~4 E2E(M6/6F-8) — production 조립을 `mode=once` 로 부팅해 mock KONEPS → 원문 저장 → 정규화 → 영속 →
 * 회계까지 끝에서 끝으로 잰다(실 KONEPS 호출 없음). 잠그는 것: ① 공고명이 계약 경유로 `notice_title` 에 실린다
 * ② `collection_run` 행 = 조회일 × 업종 ③ 회계 등식과 사유별 탈락 ④ 재실행은 새 notice 행이 0 이다
 * ⑤ 모든 로그·회계에 서비스 키도 공고명 원문(한글)도 없다 ⑥ 종료 코드.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CollectionRunnerE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "collection-e2e-test-fixture-credential"
        private const val SERVICE_KEY = "E2E-SENTINEL+KEY/value="
        private const val SLOTS = 6
        private const val NORMAL_PER_SLOT = 3
        private const val BAD_DATE_ITEM_NUMBER = "BAD-DATE-1"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_collection_e2e_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private val today: LocalDate = LocalDate.now(OPENING_DATE_ZONE)
        private val firstDay: LocalDate = today.minusDays(2)

        /**
         * 같은 JVM 의 mock KONEPS(`com.sun.net.httpserver`)는 **서버 쪽**에서 받은 요청줄을 DEBUG 로 남긴다 —
         * 시험 대상(우리 프로세스의 클라이언트·러너)의 로그가 아니므로 캡처에서 뺀다. 그 밖의 모든 로거는 잡는다.
         */
        private val logs =
            ListAppender<ILoggingEvent>().apply {
                addFilter(
                    object : Filter<ILoggingEvent>() {
                        override fun decide(event: ILoggingEvent): FilterReply =
                            if (event.loggerName.startsWith(
                                    "com.sun.net.httpserver",
                                )
                            ) {
                                FilterReply.DENY
                            } else {
                                FilterReply.NEUTRAL
                            }
                    },
                )
            }
        private lateinit var mock: MockKonepsHttp

        private fun itemsFor(
            operation: String,
            day: String,
        ): List<Map<String, String>> {
            val category = operation.removePrefix("getBidPblancListInfo")

            fun item(
                number: String,
                closing: String = "2026-12-31 10:00:00",
            ) = mapOf(
                "bidNtceNo" to number,
                "bidNtceOrd" to "000",
                "bidNtceNm" to "공고명 $category $number",
                "bsnsDivNm" to "공사",
                "bidClseDt" to closing,
            )
            val normal = (1..NORMAL_PER_SLOT).map { item("E2E-$category-$day-$it") }
            val missingNumber = mapOf("bidNtceNm" to "번호 없는 공고명")
            val duplicate = normal.first()
            val badDate =
                if (category == "Cnstwk" && day == firstDay.toString().replace("-", "")) {
                    listOf(item(BAD_DATE_ITEM_NUMBER, closing = "not-a-date"))
                } else {
                    emptyList()
                }
            return normal + missingNumber + duplicate + badDate
        }

        /** 러너 한 번 — 부팅이 곧 실행이다(`ApplicationRunner`). 반환은 그 실행이 기록한 종료 코드들. */
        private fun runOnce(): List<Int> {
            val context: ConfigurableApplicationContext =
                SpringApplicationBuilder(BidVectorApplication::class.java)
                    .properties(
                        PRODUCTION_DISPATCH_PROPERTIES +
                            mapOf(
                                "server.port" to "0",
                                "spring.profiles.active" to "collection-e2e",
                                "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                                "bidvector.persistence.username" to postgres.username,
                                "bidvector.persistence.credential" to postgres.password,
                                "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                                "bidvector.evaluation.candidate-cap" to "1000",
                                "bidvector.collection.mode" to "once",
                                "bidvector.collection.from" to firstDay.toString(),
                                "bidvector.collection.to" to today.toString(),
                                "bidvector.collection.categories" to "construction,service",
                                "bidvector.koneps.service-key" to SERVICE_KEY,
                                "bidvector.koneps.base-url" to mock.baseUrl,
                            ),
                    ).listeners(ApplicationListener<ApplicationPreparedEvent> { attachLogCapture() })
                    .run()
            return try {
                context.getBean(RecordingCollectionTermination::class.java).exitCodes.toList()
            } finally {
                context.close()
            }
        }

        /**
         * Boot 는 환경 준비 단계에서 로깅 시스템을 다시 초기화해 그 전에 붙인 appender 를 떼어낸다 — 초기화 뒤
         * (`ApplicationPreparedEvent`, 빈 생성 전)에 붙여야 러너와 어댑터의 로그를 전부 잡는다.
         */
        private fun attachLogCapture() {
            val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
            root.level = Level.DEBUG
            if (!logs.isStarted) logs.start()
            if (!root.isAttached(logs)) root.addAppender(logs)
        }

        @JvmStatic
        @BeforeAll
        fun boot() {
            mock = MockKonepsHttp(::itemsFor)
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).detachAppender(logs)
            mock.close()
            postgres.stop()
        }
    }

    private val dataSource: DataSource =
        org.postgresql.ds.PGSimpleDataSource().apply {
            setUrl(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }

    private fun count(sql: String): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun collectionLines(): List<String> =
        logs.list.map { it.formattedMessage }.filter { it.startsWith("collection ") }

    private fun slotField(
        line: String,
        name: String,
    ): Int =
        Regex("$name=(\\d+)")
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.toInt() ?: error("$name 없음: $line")

    @Test
    @Order(1)
    fun `첫 실행 — 조회일 × 업종마다 회계 한 줄, 공고명이 계약 경유로 저장되고 정상 종료한다`() {
        val exitCodes = runOnce()

        exitCodes shouldContainExactly listOf(0)
        count("SELECT COUNT(*) FROM collection_run") shouldBe SLOTS
        count("SELECT COUNT(*) FROM notice") shouldBe SLOTS * NORMAL_PER_SLOT
        count("SELECT COUNT(*) FROM notice WHERE notice_title IS NULL OR notice_title = ''") shouldBe 0
        count("SELECT COUNT(*) FROM notice WHERE notice_title NOT LIKE '공고명 %'") shouldBe 0
        count("SELECT COUNT(*) FROM raw_observation") shouldBe SLOTS * NORMAL_PER_SLOT + 1
        count("SELECT SUM(received) FROM collection_run") shouldBe SLOTS * (NORMAL_PER_SLOT + 2) + 1
        count("SELECT SUM(normalized) FROM collection_run") shouldBe SLOTS * NORMAL_PER_SLOT
        count("SELECT SUM(duplicate) FROM collection_run") shouldBe SLOTS
        count("SELECT SUM(dropped) FROM collection_run") shouldBe SLOTS + 1
        count("SELECT COUNT(*) FROM collection_run WHERE truncated") shouldBe 0
        count("SELECT COUNT(*) FROM notice WHERE notice_number = '$BAD_DATE_ITEM_NUMBER'") shouldBe 0
    }

    @Test
    @Order(2)
    fun `로그에는 서비스 키도 공고명 원문도 없다 — 수집 줄은 건수와 코드뿐이고 모든 로그에 키가 없다`() {
        val lines = collectionLines()

        lines.size shouldBe 1 + SLOTS + 1
        lines.forEach { line ->
            line shouldNotContain SERVICE_KEY
            Regex("[가-힣]").containsMatchIn(line) shouldBe false
        }
        val everything = logs.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message.orEmpty()) }
        val encodedKey = URLEncoder.encode(SERVICE_KEY, StandardCharsets.UTF_8)
        everything shouldNotContain SERVICE_KEY
        everything shouldNotContain encodedKey
        // 표본이 실제로 키를 요청에 실어 보냈다(URL 인코딩 형태) — 부재 단언이 공허하지 않다.
        mock.queries.isNotEmpty() shouldBe true
        mock.queries.all { it.contains("serviceKey=$encodedKey") } shouldBe true
    }

    @Test
    @Order(3)
    fun `같은 범위를 다시 돌리면 새 notice 행이 0 이고 중복이 1차 정규화 수와 같다 — 멱등`() {
        val noticesBefore = count("SELECT COUNT(*) FROM notice")
        logs.list.clear()

        val exitCodes = runOnce()

        exitCodes shouldContainExactly listOf(0)
        count("SELECT COUNT(*) FROM notice") shouldBe noticesBefore
        count("SELECT COUNT(*) FROM collection_run") shouldBe 2 * SLOTS
        val slotLines = collectionLines().filter { it.startsWith("collection slot ") }
        slotLines.size shouldBe SLOTS
        slotLines.forEach { line ->
            slotField(line, "inserted") shouldBe 0
            slotField(line, "normalized") shouldBe 0
            slotField(line, "unchanged") shouldBe NORMAL_PER_SLOT
            slotField(line, "duplicate") shouldBe NORMAL_PER_SLOT + 1
        }
    }
}
