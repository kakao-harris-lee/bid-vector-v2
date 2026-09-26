package bidvector.app.collection

import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.app.BidVectorApplication
import bidvector.app.PRODUCTION_DISPATCH_PROPERTIES
import bidvector.app.wiring.RecordingCollectionTermination
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import bidvector.workflow.evaluation.WatchSubjectOutcome
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import javax.sql.DataSource

/**
 * D-6F8-1~4 E2E(M6/6F-8) — production 조립을 `mode=once` 로 부팅해 mock KONEPS → 원문 저장 → 정규화 → 영속 →
 * 회계까지 끝에서 끝으로 잰다(실 KONEPS 호출 없음). 잠그는 것: ① 공고명이 계약 경유로 `notice_title` 에 실린다
 * ② `collection_run` 행 = 조회일 × 업종 ③ 회계 등식과 사유별 탈락(형식이 어긋난 차수 포함 — 실행이 죽지 않는다)
 * ④ 재실행은 새 notice 행이 0 이다 ⑤ 모든 로그·표준 출력·표준 오류(예외 cause 체인 전체)에 서비스 키도 공고명
 * 원문(한글)도 없다 ⑥ 종료 코드.
 *
 * 첫 실행은 `@BeforeAll` 에서 한 번 돌고 그 결과를 스냅숏으로 잡는다 — test 는 서로의 실행 순서에 기대지 않는다.
 */
class CollectionRunnerE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "collection-e2e-test-fixture-credential"
        private const val SERVICE_KEY = "E2E-SENTINEL+KEY/value="
        private const val SLOTS = 6
        private const val NORMAL_PER_SLOT = 3
        private const val BLANK_PER_SLOT = 1
        private const val NOTICES_PER_SLOT = NORMAL_PER_SLOT + BLANK_PER_SLOT
        private const val BAD_DATE_ITEM_NUMBER = "BAD-DATE-1"
        private const val BAD_ROUND_ITEM_NUMBER = "BAD-ROUND-1"
        private const val DAYS = 3
        private const val SERVICE_CLASS_CODE = "81111500"
        private const val SERVICE_CLASS_NAME = "정보시스템 개발 서비스"
        private const val CONSTRUCTION_TYPE = "전기공사업"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_collection_e2e_test")
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
        private val firstDay: LocalDate = today.minusDays(2)

        /** 로거 이벤트 전부(예외 cause 체인 포함) — 러너·어댑터·Boot 의 로그를 한 자리에서 잡는다. */
        private val logs = ListAppender<ILoggingEvent>()
        private lateinit var mock: MockKonepsHttp

        private fun itemOf(
            category: String,
            number: String,
            closing: String = "2026-12-31 10:00:00",
            classification: Map<String, String> = emptyMap(),
        ) = mapOf(
            "bidNtceNo" to number,
            "bidNtceOrd" to "000",
            "bidNtceNm" to "공고명 $category $number",
            // 실 응답에는 이 키가 없다(6F-8 실측) — 있어도 대분류는 오퍼레이션이 정한다(D-6F9-1): 용역 응답에도 「공사」를 싣는다.
            "bsnsDivNm" to "공사",
            "bidClseDt" to closing,
        ) + classification

        private fun itemsFor(
            operation: String,
            day: String,
        ): List<Map<String, String>> {
            val category = operation.removePrefix("getBidPblancListInfo")

            fun item(
                number: String,
                closing: String = "2026-12-31 10:00:00",
                classification: Map<String, String> = emptyMap(),
            ) = itemOf(category, number, closing, classification)
            val normal =
                (1..NORMAL_PER_SLOT).map {
                    item(
                        "E2E-$category-$day-$it",
                        classification = classificationFor(category, it),
                    )
                }
            // D-6F8-11 — KONEPS 는 옵션 일시·금액을 빈 문자열로 내기도 한다(실수집 실측). 합성 표본이며 정규화되고 마감은 null 이다.
            val blankOptionals =
                item("E2E-$category-$day-BLANK", closing = "", classification = blankClassificationFor(category)) +
                    mapOf("opengDt" to "", "bssamt" to "", "presmptPrce" to "", "chgDt" to "", "tpEvalApplClseDt" to "")
            val missingNumber = mapOf("bidNtceNm" to "번호 없는 공고명")
            val duplicate = normal.first()
            val firstConstructionDay = category == "Cnstwk" && day == firstDay.toString().replace("-", "")
            val badDate =
                if (firstConstructionDay) {
                    listOf(
                        item(BAD_DATE_ITEM_NUMBER, closing = "not-a-date"),
                    )
                } else {
                    emptyList()
                }
            // 차수가 비어 있지 않지만 세 자리 숫자가 아니다 — 어댑터는 통과시키고 정규화가 IDENTIFIER 탈락으로 접는다.
            val badRound =
                if (firstConstructionDay) {
                    listOf(
                        item(BAD_ROUND_ITEM_NUMBER) + mapOf("bidNtceOrd" to "1"),
                    )
                } else {
                    emptyList()
                }
            return normal + listOf(blankOptionals) + missingNumber + duplicate + badDate + badRound
        }

        /**
         * D-6F9-2 — 오퍼레이션마다 응답이 싣는 세부 분류 키가 다르다(6F-8 실측): 용역은 용역구분·공공조달분류 번호·명, 공사는 주공종이고
         * 주공종은 일부 항목만 채워진다(전기공사업 · 빈 문자열 · 키 없음이 한 슬롯에 섞인다).
         */
        private fun classificationFor(
            category: String,
            index: Int,
        ): Map<String, String> =
            when (category) {
                "Servc" -> {
                    mapOf(
                        "srvceDivNm" to if (index == 1) "일반용역" else "기술용역",
                        "pubPrcrmntClsfcNo" to SERVICE_CLASS_CODE,
                        "pubPrcrmntClsfcNm" to SERVICE_CLASS_NAME,
                    )
                }

                else -> {
                    when (index) {
                        1 -> mapOf("mainCnsttyNm" to CONSTRUCTION_TYPE)
                        2 -> mapOf("mainCnsttyNm" to "")
                        else -> emptyMap()
                    }
                }
            }

        /** 빈 문자열로 오는 옵션 값(D-6F8-11) — 새 세부 분류 키도 빈 값은 없는 값이다. */
        private fun blankClassificationFor(category: String): Map<String, String> =
            when (category) {
                "Servc" -> mapOf("srvceDivNm" to "", "pubPrcrmntClsfcNo" to " ", "pubPrcrmntClsfcNm" to "  ")
                else -> mapOf("mainCnsttyNm" to "   ")
            }

        /** 러너 한 번의 관측 — 종료 코드, 로거 이벤트 전부, 표준 출력·표준 오류 전부. */
        private class RunResult(
            val exitCodes: List<Int>,
            val events: List<ILoggingEvent>,
            val stdio: String,
        ) {
            val lines: List<String> = events.map { it.formattedMessage }.filter { it.startsWith("collection ") }

            /** 로거 메시지·예외 cause 체인 전체·표준 출력/오류를 한 덩이로 — 부재 단언의 대상이다. */
            val everything: String =
                events.joinToString("\n") { event ->
                    event.formattedMessage + "\n" + event.throwableProxy?.let(ThrowableProxyUtil::asString).orEmpty()
                } + "\n" + stdio
        }

        /** 러너 한 번 — 부팅이 곧 실행이다(`ApplicationRunner`). 로그·표준 출력·표준 오류를 실행 동안 전부 잡는다. */
        private fun runOnce(extraProperties: Map<String, String> = emptyMap()): RunResult {
            logs.list.clear()
            val stdio = ByteArrayOutputStream()
            val originalOut = System.out
            val originalErr = System.err
            val sink = PrintStream(stdio, true, StandardCharsets.UTF_8)
            System.setOut(sink)
            System.setErr(sink)
            try {
                val exitCodes = bootAndRun(extraProperties)
                return RunResult(exitCodes, logs.list.toList(), stdio.toString(StandardCharsets.UTF_8))
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }
        }

        private fun bootAndRun(extraProperties: Map<String, String>): List<Int> {
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
                            ) + extraProperties,
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
            // 같은 JVM 의 mock KONEPS(`com.sun.net.httpserver`)는 **서버 쪽**에서 받은 요청줄(키 포함)을 DEBUG 로 남긴다 —
            // 시험 대상(우리 프로세스의 클라이언트·러너)의 로그가 아니므로 모든 출구(캡처·표준 출력)에서 뺀다.
            (LoggerFactory.getLogger("com.sun.net.httpserver") as Logger).level = Level.WARN
            if (!logs.isStarted) logs.start()
            if (!root.isAttached(logs)) root.addAppender(logs)
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

        /** DB 의 집계 스냅숏 — 첫 실행 직후 한 번 잡아, 뒤 test 의 재실행과 무관하게 첫 실행을 단언한다. */
        private class DbCounts(
            val collectionRuns: Int = count("SELECT COUNT(*) FROM collection_run"),
            val notices: Int = count("SELECT COUNT(*) FROM notice"),
            val untitledNotices: Int =
                count("SELECT COUNT(*) FROM notice WHERE notice_title IS NULL OR notice_title = ''"),
            val foreignTitleNotices: Int = count("SELECT COUNT(*) FROM notice WHERE notice_title NOT LIKE '공고명 %'"),
            val rawObservations: Int = count("SELECT COUNT(*) FROM raw_observation"),
            val received: Int = count("SELECT SUM(received) FROM collection_run"),
            val normalized: Int = count("SELECT SUM(normalized) FROM collection_run"),
            val duplicate: Int = count("SELECT SUM(duplicate) FROM collection_run"),
            val dropped: Int = count("SELECT SUM(dropped) FROM collection_run"),
            val truncatedRuns: Int = count("SELECT COUNT(*) FROM collection_run WHERE truncated"),
            val badDateNotices: Int =
                count("SELECT COUNT(*) FROM notice WHERE notice_number = '$BAD_DATE_ITEM_NUMBER'"),
            val badRoundNotices: Int =
                count("SELECT COUNT(*) FROM notice WHERE notice_number = '$BAD_ROUND_ITEM_NUMBER'"),
            val blankOptionalNotices: Int =
                count("SELECT COUNT(*) FROM notice WHERE notice_number LIKE 'E2E-%-BLANK' AND deadline_at IS NULL"),
        )

        private lateinit var firstRun: RunResult
        private lateinit var firstRunDb: DbCounts

        @JvmStatic
        @BeforeAll
        fun boot() {
            mock = MockKonepsHttp(::itemsFor)
            firstRun = runOnce()
            firstRunDb = DbCounts()
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).detachAppender(logs)
            mock.close()
            postgres.stop()
        }
    }

    private fun slotLines(run: RunResult): List<String> = run.lines.filter { it.startsWith("collection slot ") }

    private fun slotKey(line: String): String =
        Regex("date=\\S+ source=\\S+").find(line)?.value ?: error("슬롯 표식 없음: $line")

    private fun slotField(
        line: String,
        name: String,
    ): Int =
        Regex("$name=(\\d+)")
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.toInt() ?: error("$name 없음: $line")

    /** 서비스 키는 원문·URL 인코딩 형태 모두 로거 메시지·예외 cause 체인·표준 출력·표준 오류 어디에도 없다. */
    private fun assertNoServiceKey(run: RunResult) {
        run.everything shouldNotContain SERVICE_KEY
        run.everything shouldNotContain URLEncoder.encode(SERVICE_KEY, StandardCharsets.UTF_8)
    }

    @Test
    fun `첫 실행 — 조회일 × 업종마다 회계 한 줄, 공고명이 계약 경유로 저장되고 정상 종료한다`() {
        firstRun.exitCodes shouldContainExactly listOf(0)
        val db = firstRunDb
        db.collectionRuns shouldBe SLOTS
        db.notices shouldBe SLOTS * NOTICES_PER_SLOT
        db.untitledNotices shouldBe 0
        db.foreignTitleNotices shouldBe 0
        // 정상 항목 + 정규화에서 탈락한 둘(일시 오류·차수 형식 오류) — 번호 없는 항목은 어댑터 단계 탈락이라 원문이 없다.
        db.rawObservations shouldBe SLOTS * NOTICES_PER_SLOT + 2
        db.received shouldBe SLOTS * (NOTICES_PER_SLOT + 2) + 2
        db.normalized shouldBe SLOTS * NOTICES_PER_SLOT
        db.duplicate shouldBe SLOTS
        db.dropped shouldBe SLOTS + 2
        db.truncatedRuns shouldBe 0
        db.badDateNotices shouldBe 0
        db.badRoundNotices shouldBe 0
    }

    @Test
    fun `빈 문자열인 옵션 일시·금액 항목은 탈락하지 않고 마감이 null 인 공고로 저장된다 — 슬롯마다 하나`() {
        firstRunDb.blankOptionalNotices shouldBe SLOTS * BLANK_PER_SLOT
        // 빈 일시를 「해석 실패」로 세지 않는다 — DATE_TIME 탈락은 형식이 어긋난 표본 하나뿐이다.
        slotLines(firstRun).count { "CollectionParseFailure(kind=DATE_TIME)" in it } shouldBe 1
        slotLines(firstRun).count { "CollectionParseFailure(kind=NUMERIC)" in it } shouldBe 0
    }

    /**
     * D-6F9-1·2 — 대분류는 오퍼레이션이, 세부 분류는 응답 필드가 정하고 각자 자기 열에 간다. 공사 응답의 주공종은 코드 없이 이름만이라
     * 공사 공고의 업무구분 코드·라벨 열은 비고, 용역구분은 업무구분 라벨 열과 접히지 않으며, 빈 문자열은 부재(`NULL`)다.
     * (공고번호는 저장 시 대문자로 정규화되므로 표본 접두도 대문자다.)
     */
    private fun assertClassificationColumns() {
        fun notices(where: String) = count("SELECT COUNT(*) FROM notice WHERE $where")
        val construction = "notice_number LIKE 'E2E-CNSTWK-%'"
        val service = "notice_number LIKE 'E2E-SERVC-%'"

        notices("business_division IS NULL") shouldBe 0
        notices("$construction AND business_division = '공사'") shouldBe DAYS * NOTICES_PER_SLOT
        // 응답이 용역 오퍼레이션에도 `bsnsDivNm=공사` 를 실었지만 용역 공고의 대분류는 용역이다(오퍼레이션이 이긴다).
        notices("$service AND business_division = '용역'") shouldBe DAYS * NOTICES_PER_SLOT

        notices("$service AND service_division IN ('일반용역', '기술용역')") shouldBe DAYS * NORMAL_PER_SLOT
        notices("$service AND service_division = '일반용역'") shouldBe DAYS
        val classified =
            "business_category_code = '$SERVICE_CLASS_CODE' AND business_category_label = '$SERVICE_CLASS_NAME'"
        notices("$service AND $classified") shouldBe DAYS * NORMAL_PER_SLOT
        notices("$service AND main_construction_type IS NOT NULL") shouldBe 0

        notices("$construction AND main_construction_type = '$CONSTRUCTION_TYPE'") shouldBe DAYS
        notices("$construction AND main_construction_type IS NOT NULL") shouldBe DAYS
        notices(
            "$construction AND (business_category_code IS NOT NULL OR business_category_label IS NOT NULL)",
        ) shouldBe
            0
        notices("$construction AND service_division IS NOT NULL") shouldBe 0

        notices("service_division IN (business_category_label, business_category_code)") shouldBe 0
        notices("service_division = '' OR main_construction_type = '' OR business_category_code = ''") shouldBe 0
    }

    @Test
    fun `첫 실행 — 업무구분 새 칸 셋이 오퍼레이션·응답 필드에서 각자 자기 열에 채워진다 — 주공종은 코드를 낳지 않는다`() {
        firstRun.exitCodes shouldContainExactly listOf(0)

        assertClassificationColumns()
    }

    @Test
    fun `감시 관심 업종 집합은 저장된 네 칸에서 조립된다 — 빈 값은 원소가 아니다`() {
        val day = firstDay.toString().replace("-", "")
        val repository = JdbcNoticeRepository(dataSource)

        fun categoriesOf(number: String): Set<String> {
            val notice = requireNotNull(repository.find(NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))))
            val outcome = NoticeWatchSubjectPort().subjectFor(notice) as WatchSubjectOutcome.Found
            return outcome.subject.categories
                .map { it.value }
                .toSet()
        }

        categoriesOf("E2E-Servc-$day-2") shouldBe setOf("용역", "기술용역", SERVICE_CLASS_CODE)
        categoriesOf("E2E-Cnstwk-$day-1") shouldBe setOf("공사", CONSTRUCTION_TYPE)
        categoriesOf("E2E-Cnstwk-$day-3") shouldBe setOf("공사")
        categoriesOf("E2E-Servc-$day-BLANK") shouldBe setOf("용역")
    }

    @Test
    fun `새 열이 없던 시절의 기존 행을 같은 범위 재수집이 채운다 — 슬롯마다 updated 이고 새 공고는 0 이다`() {
        val noticesBefore = count("SELECT COUNT(*) FROM notice")
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.executeUpdate(
                    "UPDATE notice SET business_division = NULL, service_division = NULL, " +
                        "main_construction_type = NULL, business_category_code = NULL, business_category_label = NULL",
                )
            }
        }
        count("SELECT COUNT(*) FROM notice WHERE business_division IS NOT NULL") shouldBe 0

        val rerun = runOnce(mapOf("spring.main.web-application-type" to "none"))

        rerun.exitCodes shouldContainExactly listOf(0)
        count("SELECT COUNT(*) FROM notice") shouldBe noticesBefore
        slotLines(rerun).size shouldBe SLOTS
        slotLines(rerun).forEach { line ->
            slotField(line, "inserted") shouldBe 0
            slotField(line, "updated") shouldBe NOTICES_PER_SLOT
            slotField(line, "unchanged") shouldBe 0
        }
        assertClassificationColumns()
    }

    @Test
    fun `형식이 어긋난 차수 항목은 실행을 죽이지 않고 IDENTIFIER 탈락으로 회계에 오른다`() {
        firstRun.exitCodes shouldContainExactly listOf(0)
        slotLines(firstRun).count { "CollectionParseFailure(kind=IDENTIFIER)=1" in it } shouldBe 1
        slotLines(firstRun).count { "CollectionParseFailure(kind=DATE_TIME)=1" in it } shouldBe 1
    }

    @Test
    fun `로그에는 서비스 키도 공고명 원문도 없다 — 수집 줄은 건수와 코드뿐이고 로그·표준 출력·표준 오류 전부에 키가 없다`() {
        val lines = firstRun.lines

        lines.size shouldBe 1 + SLOTS + 1
        lines.forEach { line ->
            line shouldNotContain SERVICE_KEY
            Regex("[가-힣]").containsMatchIn(line) shouldBe false
        }
        assertNoServiceKey(firstRun)
        // 캡처가 콘솔 출력을 실제로 본다 — 표준 출력에 대한 부재 단언이 공허하지 않다.
        firstRun.stdio shouldContain "collection start"
        // 표본이 실제로 키를 요청에 실어 보냈다(URL 인코딩 형태) — 부재 단언이 공허하지 않다.
        val encodedKey = URLEncoder.encode(SERVICE_KEY, StandardCharsets.UTF_8)
        mock.queries.isNotEmpty() shouldBe true
        mock.queries.all { it.contains("serviceKey=$encodedKey") } shouldBe true
    }

    @Test
    fun `같은 범위를 다시 돌리면 새 notice 행이 0 이고 슬롯마다 정규화 0, 중복은 1차 정규화 + 1차 중복이다 — 멱등`() {
        val noticesBefore = count("SELECT COUNT(*) FROM notice")
        val runsBefore = count("SELECT COUNT(*) FROM collection_run")

        // 실수집 권장 형태 — 웹 서버 없이(`web-application-type=none`) 러너만 돈다. 포트를 열지 않는다.
        val second = runOnce(mapOf("spring.main.web-application-type" to "none"))

        second.exitCodes shouldContainExactly listOf(0)
        count("SELECT COUNT(*) FROM notice") shouldBe noticesBefore
        count("SELECT COUNT(*) FROM collection_run") shouldBe runsBefore + SLOTS
        val firstBySlot = slotLines(firstRun).associateBy(::slotKey)
        slotLines(second).size shouldBe SLOTS
        slotLines(second).forEach { line ->
            val first = firstBySlot.getValue(slotKey(line))
            slotField(line, "inserted") shouldBe 0
            slotField(line, "normalized") shouldBe 0
            slotField(line, "unchanged") shouldBe slotField(first, "inserted") + slotField(first, "updated")
            slotField(line, "duplicate") shouldBe slotField(first, "normalized") + slotField(first, "duplicate")
            slotField(line, "dropped") shouldBe slotField(first, "dropped")
        }
        assertNoServiceKey(second)
    }
}
