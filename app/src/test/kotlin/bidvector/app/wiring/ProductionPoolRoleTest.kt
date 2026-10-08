package bidvector.app.wiring

import bidvector.app.productionApplication
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.SQLException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * **M6/6E-2a — 풀과 접속 역할을 출하 조립 위에서 잰다**(scope.md P-1·P-2·P-3).
 *
 * 왜 여기인가: 역할 전환을 `grep 'SET ROLE'` 같은 문자열 술어로 재면 표기 하나로 열린다
 * (설계 검토 (1)). 이 test 는 **`main()` 과 같은 조립 호출**([bidvector.app.productionApplication])
 * 로 뜬 컨텍스트의 `DataSource` 빈에서 받은 연결로 **DB 에 직접 묻는다** — 우회하려면 배선
 * 자체를 바꿔야 하고, 그러면 여기가 문다.
 *
 * 컨테이너는 **빈 DB** 다. 그래서 이 class 가 뜨는 것 자체가 「clean DB 기동」의 실측이다 —
 * 역할 `bidvector_app` 은 `V2` 마이그레이션이 만들고, 풀의 연결 초기화가 그 역할로 전환하므로
 * **migrate 가 풀보다 먼저 돌지 않으면 기동이 실패한다**(설계 검토 (3) 「풀 생성 시점」,
 * scope.md 변이 ③).
 *
 * 리터럴을 쓰는 이유: 역할 이름은 마이그레이션의 사실이고 두 이름표(`application_name`)는
 * runbook §2.6 이 운영자에게 약속하는 값이다. production 상수를 참조하면 그 값이 바뀌어도
 * 이 test 가 따라가 **아무것도 재지 않는다**.
 */
class ProductionPoolRoleTest {
    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"
        private const val TEST_CREDENTIAL_VALUE = "production-pool-test-fixture-credential"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_pool_role_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        private lateinit var context: ConfigurableApplicationContext

        @JvmStatic
        @BeforeAll
        fun boot() {
            context =
                productionApplication()
                    .properties(
                        mapOf(
                            "server.port" to "0",
                            "management.server.port" to "0",
                            "bidvector.persistence.jdbc-url" to postgres.jdbcUrl,
                            "bidvector.persistence.username" to postgres.username,
                            "bidvector.persistence.credential" to postgres.password,
                            "operator.credential.value" to TEST_CREDENTIAL_VALUE,
                            "bidvector.evaluation.candidate-cap" to "1000",
                        ),
                    ).run()
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            context.close()
            postgres.stop()
        }
    }

    /** 출하 조립이 실제로 만든 풀 — 다른 자리에서 다시 만들지 않는다. */
    private fun pool(): HikariDataSource = context.getBean(DataSource::class.java) as HikariDataSource

    /**
     * 컨테이너 소유자 자격으로 여는 연결 — `pg_stat_activity` 의 다른 사용자 세션 칸은 소유자
     * 권한에서만 보인다. 이름표를 붙이면 그 세션을 질의로 셀 수 있다.
     */
    private fun ownerConnection(applicationName: String): Connection =
        PGSimpleDataSource()
            .apply {
                setUrl(postgres.jdbcUrl)
                user = postgres.username
                password = postgres.password
                this.applicationName = applicationName
            }.connection

    @Test
    fun `P-2 — 컨텍스트의 DataSource 빈은 풀 하나뿐이다`() {
        context.getBeansOfType(DataSource::class.java).keys shouldHaveSize 1
        context.getBean(DataSource::class.java).shouldBeInstanceOf<HikariDataSource>()
    }

    /**
     * P-3 — 하한 **2**: relay 가 세션 advisory lock 으로 연결 하나를 본문 내내 쥐므로, 작업
     * 연결이 따로 나오지 않으면 relay 가 자기 자신을 기다린다(설계 검토 우회 6). 이 수는
     * production 상수의 사본이 아니라 **그 상수가 만족해야 하는 하한**이다.
     */
    @Test
    fun `P-3 — 풀 최대 크기가 임대 전용 연결과 작업 연결의 하한 2 이상이다`() {
        pool().maximumPoolSize shouldBeGreaterThanOrEqual LEASE_AND_WORK_FLOOR
    }

    /**
     * P-1 — **재사용·동시·재생성 세 경로 전부**에서 같은 역할이다(설계 검토 우회 1·2).
     * `connectionInitSql` 은 물리 연결마다 한 번만 도므로, 「처음 한 연결만 전환됐다」는 형태가
     * 가능한지를 세 경로로 나눠 잰다. 관측한 backend pid 가 둘 이상이어야 「연결 하나만 보고
     * 통과」가 아니다.
     */
    @Test
    fun `P-1 — 풀이 내주는 모든 연결이 접속 역할로 전환돼 있다`() {
        val users = mutableListOf<String>()
        val pids = mutableSetOf<Int>()

        repeat(pool().maximumPoolSize * 2 + 1) { borrowOnce(users, pids) }
        holdAllConcurrently(users, pids)
        pool().hikariPoolMXBean.softEvictConnections()
        borrowOnce(users, pids)

        users.toSet() shouldBe setOf(APPLICATION_ROLE)
        pids.size shouldBeGreaterThanOrEqual 2
    }

    /** 세션 사용자는 접속 자격 그대로다 — 「별도 계정으로 로그인」이 아니라 `SET ROLE` 이다. */
    @Test
    fun `P-1 — 역할은 전환이고 접속 자격은 그대로다`() {
        pool().connection.use { connection ->
            connection.singleString(CURRENT_USER) shouldBe APPLICATION_ROLE
            connection.singleString("SELECT session_user") shouldBe postgres.username
        }
    }

    /**
     * P-1 — **권한 판정과 실제 거부 둘 다** 잰다. `has_table_privilege` 만 재면 「질의는 거짓인데
     * 다른 경로로 지워지는」 경우를 못 본다. SELECT 쪽은 음성 대조다 — 질의가 늘 거짓을 내는
     * 구현에서도 통과하는 것을 막는다.
     */
    @Test
    fun `P-1 — outbox DELETE 권한이 없고 실제 DELETE 가 권한 오류로 거부된다`() {
        pool().connection.use { connection ->
            connection.hasTablePrivilege("outbox", "DELETE") shouldBe false
            connection.hasTablePrivilege("outbox", "SELECT") shouldBe true

            val rejected =
                shouldThrow<SQLException> {
                    connection.createStatement().use { it.executeUpdate("DELETE FROM outbox") }
                }
            rejected.sqlState shouldBe INSUFFICIENT_PRIVILEGE
        }
    }

    /**
     * P-2 — 역할 전환이 **쓰기 경로를 깨지 않는다**. IDENTITY 열은 암묵 시퀀스를 쓰지만
     * PostgreSQL 은 그 자리에 시퀀스 USAGE 를 따로 묻지 않는다(표의 INSERT 권한으로 충분) —
     * 추정이 아니라 이 자리에서 실측한다.
     */
    @Test
    fun `P-2 — IDENTITY 표에 접속 역할로 INSERT 된다`() {
        pool().connection.use { connection ->
            connection.prepareStatement(INSERT_AUDIT_ROW).use { statement ->
                statement.setString(1, "operator")
                statement.setString(2, "GET")
                statement.setString(3, "/api/pool-role-probe")
                statement.setInt(4, 200)
                statement.setLong(5, 1)
                statement.setString(6, "pool-role-probe")
                statement.executeUpdate() shouldBe 1
            }
        }
    }

    /**
     * P-2 — 소유자 연결은 **migrate 지역에서만** 산다(설계 검토 우회 4). 기동이 끝난 뒤 그
     * 이름표를 단 세션이 하나도 없어야 한다.
     *
     * 공허함을 막는 둘: ⓐ 양성 대조 — 같은 이름표의 세션을 하나 열면 같은 질의가 그것을 센다
     * (질의가 늘 0 을 내는 것이 아니다). ⓑ 풀 이름표 — 이름표를 다는 배선 자체가 살아 있고
     * 서버까지 닿는다(그 기계가 죽으면 ⓐ 만으로는 못 본다).
     */
    @Test
    fun `P-2 — 기동이 끝난 뒤 소유자 migration 세션이 남아 있지 않다`() {
        ownerConnection(PROBE_APPLICATION_NAME).use { probe ->
            probe.sessionsNamed(MIGRATION_APPLICATION_NAME) shouldBe 0
            probe.sessionsNamed(POOL_APPLICATION_NAME) shouldBeGreaterThanOrEqual 1

            ownerConnection(MIGRATION_APPLICATION_NAME).use {
                probe.sessionsNamed(MIGRATION_APPLICATION_NAME) shouldBe 1
            }
        }
    }

    private fun borrowOnce(
        users: MutableList<String>,
        pids: MutableSet<Int>,
    ) {
        pool().connection.use { connection ->
            users += connection.singleString(CURRENT_USER)
            pids += connection.singleString(BACKEND_PID).toInt()
        }
    }

    /**
     * 최대 크기만큼의 연결을 **동시에** 쥔다 — 순차 대여만으로는 물리 연결 하나를 돌려 쓰므로
     * 「두 번째 물리 연결은 전환되지 않는다」는 형태를 못 본다.
     */
    private fun holdAllConcurrently(
        users: MutableList<String>,
        pids: MutableSet<Int>,
    ) {
        val size = pool().maximumPoolSize
        val release = CountDownLatch(1)
        val held = CountDownLatch(size)
        val workers = Executors.newFixedThreadPool(size)
        val observed = Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
        try {
            repeat(size) { workers.submit { holdOne(observed, held, release) } }
            held.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        } finally {
            release.countDown()
            workers.shutdown()
            workers.awaitTermination(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        }
        observed.forEach { (user, pid) ->
            users += user
            pids += pid
        }
    }

    private fun holdOne(
        observed: MutableList<Pair<String, Int>>,
        held: CountDownLatch,
        release: CountDownLatch,
    ) {
        pool().connection.use { connection ->
            observed += connection.singleString(CURRENT_USER) to connection.singleString(BACKEND_PID).toInt()
            held.countDown()
            release.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }
}

private const val APPLICATION_ROLE = "bidvector_app"
private const val POOL_APPLICATION_NAME = "bidvector-app"
private const val MIGRATION_APPLICATION_NAME = "bidvector-migration"
private const val PROBE_APPLICATION_NAME = "pool-role-probe"
private const val CURRENT_USER = "SELECT current_user"
private const val BACKEND_PID = "SELECT pg_backend_pid()"
private const val LEASE_AND_WORK_FLOOR = 2
private const val INSUFFICIENT_PRIVILEGE = "42501"
private const val HOLD_TIMEOUT_SECONDS = 30L

private const val INSERT_AUDIT_ROW =
    "INSERT INTO api_request_audit (occurred_at, subject, method, path, status_code, duration_ms, correlation_id) " +
        "VALUES (now(), ?, ?, ?, ?, ?, ?)"

private const val SESSIONS_NAMED =
    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND application_name = ?"

private fun Connection.singleString(sql: String): String =
    createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            check(rows.next()) { "질의가 행을 돌려주지 않았다: $sql" }
            rows.getString(1)
        }
    }

private fun Connection.hasTablePrivilege(
    table: String,
    privilege: String,
): Boolean =
    prepareStatement("SELECT has_table_privilege(current_user, ?, ?)").use { statement ->
        statement.setString(1, table)
        statement.setString(2, privilege)
        statement.executeQuery().use { rows ->
            check(rows.next()) { "has_table_privilege 가 행을 돌려주지 않았다" }
            rows.getBoolean(1)
        }
    }

private fun Connection.sessionsNamed(applicationName: String): Int =
    prepareStatement(SESSIONS_NAMED).use { statement ->
        statement.setString(1, applicationName)
        statement.executeQuery().use { rows ->
            check(rows.next()) { "pg_stat_activity 질의가 행을 돌려주지 않았다" }
            rows.getInt(1)
        }
    }
