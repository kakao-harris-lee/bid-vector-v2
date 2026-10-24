package bidvector.app.http

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.app.adminDataSource
import bidvector.app.productionApplication
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeTitle
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

private const val SUGGESTION_EDIT_SESSIONS = "/api/strategy/edit-sessions"

/** 이 test 조립의 스캔 상한(계산 예산) — 표시 상한과 다른 자리(운영 설정)에서 온다. */
private const val E2E_CANDIDATE_CAP = 200

/**
 * 제안 목록을 **출하 조립 + 실 PostgreSQL** 로 끝까지 돈다(M7/7C①b). 계약의 STR-07 판정 test 둘이 여기 있다.
 *
 *  ① **저장 뒤 옛 결과가 보이지 않는다** — 편집 세션 확정(오늘 유일한 코드 쓰기 경로)으로 감시 조건을 바꾸면
 *    다음 조회가 새 개정·새 결과다.
 *  ② **「모든 편집 경로」를 구조로 덮는다** — 워크플로를 거치지 않고 개정도 올리지 않는 **수동 SQL** 로 전략
 *    값을 바꿔도 다음 조회가 새 값으로 계산된다. 판정이 쓰기 쪽의 규율(무효화 호출·이벤트·개정 +1)을 보지
 *    않고 읽힌 값만 보기 때문이다.
 *  ③ **계산 중 저장된 결과는 채택하지 않는다** — [SuggestionE2EProbes] 가 출하 조립의 `strategyRepository`
 *    빈을 감싸, 그 GET 의 N 번째 `load()` 직후 실 SQL 로 전략을 바꾼다(수정 라운드 1, cr H-1).
 *
 * 덧붙여 GET 한 번의 읽기 문장 수(`5 + 2n + h`, cr M-2)를 단언하고, 마감 임박 순서와 표시 상한이 마감
 * 임박 쪽을 남기는 것(cr M-3)을 잰다. 계산 예산(스캔 상한) 초과는 조용히 자르지 않고 409 이고, 상한까지
 * 채운 조회 한 번의 소요를 남긴다(관측값, `System.out`).
 */
@Suppress("UNCHECKED_CAST")
class SuggestionProductionE2ETest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "suggestion-e2e-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_suggestion_e2e_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private val probes = SuggestionE2EProbes()
        private lateinit var context: ConfigurableApplicationContext
        private var port: Int = 0

        @JvmStatic
        @BeforeAll
        fun boot() {
            context =
                productionApplication()
                    .initializers(probes.initializer())
                    .properties(
                        mapOf(
                            "server.port" to "0",
                            "management.server.port" to "0",
                            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                            "bidvector.persistence.username" to postgres.username,
                            "bidvector.persistence.credential" to postgres.password,
                            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                            "bidvector.evaluation.candidate-cap" to E2E_CANDIDATE_CAP.toString(),
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

    /** 실 시계 기준 미래 — 고정 날짜를 쓰면 그 날이 지나는 순간 후보가 사라진다. */
    private val deadline: Instant = Instant.now().plus(Duration.ofDays(30))

    private fun url(path: String): String = "http://localhost:$port$path"

    private fun dataSource(): DataSource = context.getBean(DataSource::class.java)

    @BeforeEach
    fun resetTables() {
        adminDataSource(postgres).connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "TRUNCATE TABLE notice, notice_audit, raw_observation, operator_strategy, " +
                        "operator_strategy_revision, edit_session, outbox, api_request_audit, " +
                        "notice_requirement CASCADE",
                )
            }
        }
    }

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            set(OperatorCredentialAuthenticator.CREDENTIAL_HEADER, TEST_CREDENTIAL_VALUE)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun suggestions(): ResponseEntity<List<Map<String, Any?>>> =
        restTemplate.exchange(
            URI.create(url("/api/suggestions")),
            HttpMethod.GET,
            HttpEntity<Void>(headers()),
            List::class.java,
        ) as ResponseEntity<List<Map<String, Any?>>>

    private fun errorOfSuggestions(): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(
            URI.create(url("/api/suggestions")),
            HttpMethod.GET,
            HttpEntity<Void>(headers()),
            Map::class.java,
        ) as ResponseEntity<Map<String, Any?>>

    private fun post(
        path: String,
        json: String,
    ): ResponseEntity<Map<String, Any?>> =
        restTemplate.exchange(url(path), HttpMethod.POST, HttpEntity(json, headers()), Map::class.java)
            as ResponseEntity<Map<String, Any?>>

    /** 편집 세션 확정 — 오늘 전략을 바꾸는 유일한 코드 경로(checklist §0.6 W1~W4). */
    private fun confirmRequiredKeyword(
        term: String,
        seenRevision: Int,
    ) {
        val begun = post(SUGGESTION_EDIT_SESSIONS, """{"field":"REQUIRED_KEYWORD"}""")
        val session = "$SUGGESTION_EDIT_SESSIONS/${begun.body?.get("sessionId") as String}"
        post("$session/value", """{"commandId":"v-$term","field":"REQUIRED_KEYWORD","terms":["$term"]}""")
        val confirmed = post("$session/confirm", """{"commandId":"c-$term","seenRevision":$seenRevision}""")
        confirmed.body?.get("state") shouldBe "APPLIED"
    }

    private fun insertNotice(
        number: String,
        title: String,
        deadlineAt: Instant = deadline,
    ): NoticeId {
        val policy = KONEPS_COLLECTION_POLICY.resolve(LocalDate.now()) as Resolution.Resolved
        val fieldContracts = policy.value.fieldContracts
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                Instant.now(),
            )
        val key = JdbcRawObservationStore(dataSource(), fieldContracts, "suggestion-e2e-test").append(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = deadlineAt,
                openingScheduledAt = null,
                raw = observation,
                title = NoticeTitle.of(title),
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    private fun ResponseEntity<List<Map<String, Any?>>>.numbers(): List<Any?> =
        body.orEmpty().map { it["noticeNumber"] }

    /** 소유자 연결로 SQL 하나 — 워크플로·개정 규율을 거치지 않는 쓰기의 대역이자 fixture 자리. */
    private fun adminUpdate(sql: String): Int =
        adminDataSource(postgres).connection.use { connection ->
            connection.createStatement().use { it.executeUpdate(sql) }
        }

    private fun setRequiredKeywordBySql(term: String) {
        adminUpdate("UPDATE operator_strategy SET required_keyword_terms = ARRAY['$term'] WHERE id = 1") shouldBe 1
    }

    /** 요건 **헤더** 한 행 — 면허 게이트의 요건 조회가 행 질의를 하나 더 하게 만드는 조건(`JdbcRequirementStore.find`). */
    private fun insertRequirementHeader(id: NoticeId) {
        val sql =
            "INSERT INTO notice_requirement (notice_number, notice_round, status) " +
                "VALUES ('${id.number.value}', '${id.round.value}', 'FAILED')"
        adminUpdate(sql) shouldBe 1
    }

    /** 무장한 GET 한 번의 읽기 문장을 표별로 센다. */
    private fun selectCountsOfOneGet(): Pair<ResponseEntity<List<Map<String, Any?>>>, Map<String, Int>> {
        probes.statements.arm()
        var recorded: List<String> = emptyList()
        // 단언이 깨지거나 요청이 던져도 기록기를 해제한다 — 묵은 기록이 다음 test 로 번지지 않게(cr r2 low).
        val response =
            try {
                suggestions()
            } finally {
                recorded = probes.statements.disarm()
            }
        return response to recorded.selectCountsByTable()
    }

    /** [plan] 을 무장한 채 [call] 을 한 번 돌리고 무장 구간의 `load()` 횟수를 함께 낸다 — 해제는 `finally`(cr r2 low). */
    private fun <T> withStrategyChanges(
        plan: Map<Int, () -> Unit>,
        call: () -> T,
    ): Pair<T, Int> {
        probes.strategy.arm(plan)
        var loads = 0
        val result =
            try {
                call()
            } finally {
                loads = probes.strategy.disarm()
            }
        return result to loads
    }

    /**
     * 기대 문장 수 — 입력 읽기 2(전략·프로필) + 후보 스캔 1 + 감시 통과 후보마다 2(요건 헤더·프로필)
     * + 그 가운데 요건 헤더가 있는 후보마다 1(요건 행) + 계산 뒤 비교 2(전략·프로필). 감시를 통과하지
     * 못한 후보는 0 이다(면허 게이트까지 가지 않는다).
     */
    private fun expectedSelects(
        watchPassed: Int,
        withHeader: Int,
    ): Map<String, Int> =
        buildMap {
            put("operator_strategy", 2)
            put("operator_profile", 2 + watchPassed)
            put("notice", 1)
            if (watchPassed > 0) put("notice_requirement", watchPassed)
            if (withHeader > 0) put("notice_requirement_row", withHeader)
        }

    @Test
    fun `STR-07 ① — 편집 세션으로 저장하면 다음 조회가 새 개정으로 계산되고 옛 결과가 보이지 않는다`() {
        insertNotice("PAVE-1", "도로 포장 공사")
        insertNotice("SEWER-1", "하수관로 정비")

        suggestions().headers.getFirst(RESULT_STATE_HEADER) shouldBe "WATCH_NOT_CONFIGURED"

        confirmRequiredKeyword("포장", seenRevision = 0)
        val first = suggestions()
        first.headers.getFirst(STRATEGY_REVISION_HEADER) shouldBe "1"
        first.numbers() shouldBe listOf("PAVE-1")
        first.body?.single()?.get("licenseVerdict") shouldBe "UNCERTAIN"

        confirmRequiredKeyword("하수", seenRevision = 1)
        val second = suggestions()
        second.headers.getFirst(STRATEGY_REVISION_HEADER) shouldBe "2"
        second.numbers() shouldBe listOf("SEWER-1")
    }

    @Test
    fun `STR-07 ② — 개정을 올리지 않는 수동 SQL 로 전략을 바꿔도 다음 조회가 새 값으로 계산된다`() {
        insertNotice("PAVE-1", "도로 포장 공사")
        insertNotice("SEWER-1", "하수관로 정비")
        confirmRequiredKeyword("포장", seenRevision = 0)
        suggestions().numbers() shouldBe listOf("PAVE-1")

        setRequiredKeywordBySql("하수")

        val after = suggestions()
        // 개정은 그대로다(쓰기 쪽이 아무 규율도 지키지 않았다) — 그래도 결과는 새 값이다.
        after.headers.getFirst(STRATEGY_REVISION_HEADER) shouldBe "1"
        after.numbers() shouldBe listOf("SEWER-1")
    }

    @Test
    fun `STR-07 ③ — 계산 중 전략이 실 SQL 로 바뀌면 첫 결과를 버리고 새 값으로 다시 계산한다`() {
        insertNotice("PAVE-1", "도로 포장 공사")
        insertNotice("SEWER-1", "하수관로 정비")
        confirmRequiredKeyword("포장", seenRevision = 0)

        val (response, loads) = withStrategyChanges(mapOf(1 to { setRequiredKeywordBySql("하수") })) { suggestions() }

        response.statusCode.value() shouldBe 200
        response.numbers() shouldBe listOf("SEWER-1")
        // 시도 1(고정 1 · 비교 1, 다름) + 시도 2(고정 1 · 비교 1, 같음) — 감싼 빈이 제안 경로의 그 reader 다.
        loads shouldBe 4
    }

    @Test
    fun `STR-07 ③ — 다시 계산하는 동안에도 실 SQL 로 바뀌면 409 로 끝내고 낡은 결과를 내지 않는다`() {
        insertNotice("PAVE-1", "도로 포장 공사")
        insertNotice("SEWER-1", "하수관로 정비")
        confirmRequiredKeyword("포장", seenRevision = 0)

        val (response, loads) =
            withStrategyChanges(mapOf(1 to { setRequiredKeywordBySql("하수") }, 3 to { setRequiredKeywordBySql("정비") })) {
                errorOfSuggestions()
            }

        response.statusCode.value() shouldBe 409
        response.body?.get("code") shouldBe ErrorCode.STRATEGY_CHANGED_DURING_COMPUTATION
        loads shouldBe 4
    }

    @Test
    fun `GET 한 번의 읽기 문장 수는 5 + 2n + h 다(n 감시 통과 · h 그 가운데 요건 헤더 보유)`() {
        confirmRequiredKeyword("포장", seenRevision = 0)
        val passing = (1..3).map { insertNotice("PAVE-$it", "포장 $it") }
        val notPassing = (1..2).map { insertNotice("SEWER-$it", "하수관로 $it") }

        val (headerless, headerlessCounts) = selectCountsOfOneGet()
        headerless.body?.size shouldBe 3
        headerlessCounts shouldBe expectedSelects(watchPassed = 3, withHeader = 0)
        headerlessCounts.values.sum() shouldBe 5 + 2 * 3

        // 헤더는 통과 후보 하나와 비통과 후보 하나에 — 비통과 쪽은 문장을 늘리지 않는다.
        insertRequirementHeader(passing.first())
        insertRequirementHeader(notPassing.first())
        val (mixed, mixedCounts) = selectCountsOfOneGet()
        mixed.body?.size shouldBe 3
        mixedCounts shouldBe expectedSelects(watchPassed = 3, withHeader = 1)
        mixedCounts.values.sum() shouldBe 5 + 2 * 3 + 1
    }

    @Test
    fun `마감 임박 순으로 내고 표시 상한은 마감 임박 쪽을 남긴다`() {
        confirmRequiredKeyword("포장", seenRevision = 0)
        val now = Instant.now()
        insertNotice("D30", "포장 D+30", now.plus(Duration.ofDays(30)))
        insertNotice("D01", "포장 D+1", now.plus(Duration.ofDays(1)))
        insertNotice("D10", "포장 D+10", now.plus(Duration.ofDays(10)))

        suggestions().numbers() shouldBe listOf("D01", "D10", "D30")

        adminUpdate("UPDATE operator_strategy SET candidate_limit = 2 WHERE id = 1") shouldBe 1
        val limited = suggestions()
        limited.numbers() shouldBe listOf("D01", "D10")
        limited.headers.getFirst(TOTAL_COUNT_HEADER) shouldBe "3"
    }

    @Test
    fun `계산 예산(스캔 상한) 초과는 조용히 자르지 않고 409 다`() {
        confirmRequiredKeyword("포장", seenRevision = 0)
        (1..E2E_CANDIDATE_CAP + 1).forEach { insertNotice("OVER-$it", "포장 $it") }

        val response = errorOfSuggestions()

        response.statusCode.value() shouldBe 409
        response.body?.get("code") shouldBe ErrorCode.CANDIDATE_CAP_EXCEEDED
    }

    @Test
    fun `스캔 상한까지 찬 후보를 한 번에 계산한다 — 표시 상한 미설정이면 전부 낸다(소요는 관측값)`() {
        confirmRequiredKeyword("포장", seenRevision = 0)
        (1..E2E_CANDIDATE_CAP).forEach { insertNotice("FULL-$it", "포장 $it") }

        val started = System.nanoTime()
        val response = suggestions()
        val elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis()

        response.statusCode.value() shouldBe 200
        response.body?.size shouldBe E2E_CANDIDATE_CAP
        response.headers.getFirst(TOTAL_COUNT_HEADER) shouldBe E2E_CANDIDATE_CAP.toString()
        println("[7c1b-cost] candidates=$E2E_CANDIDATE_CAP elapsedMillis=$elapsedMillis")
    }
}
