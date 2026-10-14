package bidvector.adapters.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

/** `lock_not_available` — `lock_timeout` 이 만료됐을 때의 SQLSTATE. 대기가 **실패로** 끝났다는 증거다. */
private const val LOCK_NOT_AVAILABLE = "55P03"

/** V18 의 `SET LOCAL lock_timeout`(5s)보다 넉넉하고, 「기다리지 않는다」를 말할 만큼 좁은 상한. */
private const val FAILURE_BOUND_MS = 20_000L

/**
 * 충돌 잠금을 쥐고 있는 시간 — **상한보다 길다.** 이 값이 test 를 **스스로 끝나게** 한다: 그 줄이
 * 없으면 migrate 는 여기까지 기다렸다가 **성공**하고, 그때 `shouldThrow` 가 실패한다(무한 대기로
 * 빌드가 멎지 않는다). JUnit 의 기본 `@Timeout` 은 같은 스레드에서 끝난 **뒤에** 판정하므로 막힌
 * JDBC 호출을 구해 주지 못한다 — 그래서 상한을 test 자신이 들고 있어야 한다.
 */
private const val LOCK_HOLD_MS = 35_000L

/** V18 직전까지만 적용한다 — 그 다음 한 문장이 이 test 의 대상이다. */
private const val BEFORE_MASKING_MIGRATION = "17"

/**
 * **V18 의 첫 문장(`SET LOCAL lock_timeout`)을 거동으로 잰다**(D-6B3-5 ⑤ · mig M-1).
 *
 * 왜 거동으로 재는가: 그 한 줄이 막는 것은 **「정지」**다. `ALTER TABLE` 은 `ACCESS EXCLUSIVE` 를
 * 요구하므로 그 표에 열린 트랜잭션이 하나라도 있으면(평범한 `SELECT` 의 `ACCESS SHARE` 로 충분하다)
 * 큐에서 기다리고, 기본 `lock_timeout` 은 **0**(무한)이며 Flyway 는 걸지 않는다. 마이그레이션이
 * 기동의 부수효과이므로 그 대기는 곧 **기동 정지**다 — 되돌리기 어려운 경로에서 「실패」보다 비싼
 * 실패 모양이다. 파일 문면을 보는 술어는 쓰지 않는다(그런 게이트는 주석 한 줄로 열린다).
 *
 * 재는 것 셋: ① 상한 안에 **끝난다** ② 끝나는 방식이 `lock_not_available` **실패**다 ③ 잠금이 풀린
 * 뒤 **재시도가 선다**(실패가 기동을 영구히 막지 않는다 — PostgreSQL 은 DDL 이 트랜잭션 안이라 실패한
 * 마이그레이션이 이력에 남지 않고, 그래서 `repair` 가 필요 없다는 것까지 ③ 이 답한다).
 *
 * **자기 컨테이너를 띄운다.** 공유 하네스의 DB 는 V19 까지 이미 적용돼 있어 「V18 직전」을 만들 수
 * 없고, 같은 클러스터에 두 번째 데이터베이스를 만드는 길도 막혀 있다 — V2 가 만드는 역할은
 * **클러스터 범위**라 두 번째 적용이 「role … already exists」로 깨진다(실측: 그 경로로 처음 짠 이
 * test 가 그 이유로 실패했다 — 틀린 사유의 RED 였다). 그래서 격리 단위가 데이터베이스가 아니라
 * 클러스터다.
 */
class V18LockTimeoutTest {
    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun `충돌 잠금이 있으면 V18 은 기다리지 않고 실패하고, 풀린 뒤 재시도가 선다`() {
        withOwnCluster { probe ->
            migrate(probe, upTo = BEFORE_MASKING_MIGRATION)
            appliedVersions(probe) shouldContain BEFORE_MASKING_MIGRATION

            val holder = LockHolder(probe)
            val failure =
                try {
                    holder.acquire()

                    var thrown: FlywayException? = null
                    val elapsed = measureTimeMillis { thrown = shouldThrow<FlywayException> { migrate(probe) } }

                    (elapsed < FAILURE_BOUND_MS) shouldBe true
                    thrown
                } finally {
                    holder.release()
                }

            sqlStateOf(failure) shouldBe LOCK_NOT_AVAILABLE

            // 잠금이 풀렸다 — 같은 명령이 이제 선다(실패가 기동을 영구히 막지 않고 `repair` 도 필요 없다).
            migrate(probe)
            appliedVersions(probe) shouldContain "19"
        }
    }

    /**
     * 다른 연결이 **열린 트랜잭션**에서 `ACCESS SHARE` 를 쥔다 — `ALTER TABLE` 의 `ACCESS EXCLUSIVE`
     * 와 충돌하는 가장 평범한 모양(조회 하나)이다. 스스로 [LOCK_HOLD_MS] 뒤에 놓는다.
     */
    private class LockHolder(
        private val source: DataSource,
    ) {
        private val acquired = CountDownLatch(1)
        private val releaseNow = CountDownLatch(1)
        private var worker: Thread? = null

        fun acquire() {
            worker =
                thread(name = "v18-lock-holder", isDaemon = true) {
                    source.connection.use { connection ->
                        connection.autoCommit = false
                        connection.createStatement().use { statement ->
                            statement.executeQuery("SELECT 1 FROM raw_observation LIMIT 1").close()
                        }
                        acquired.countDown()
                        releaseNow.await(LOCK_HOLD_MS, TimeUnit.MILLISECONDS)
                        connection.rollback()
                    }
                }
            check(acquired.await(FAILURE_BOUND_MS, TimeUnit.MILLISECONDS)) { "충돌 잠금을 쥐지 못했다" }
        }

        fun release() {
            releaseNow.countDown()
            worker?.join(FAILURE_BOUND_MS)
        }
    }

    /** 예외 사슬에서 SQLSTATE 를 찾는다 — Flyway 가 드라이버 예외를 감싸 던진다. */
    private fun sqlStateOf(failure: FlywayException?): String? {
        var cause: Throwable? = failure
        while (cause != null) {
            if (cause is SQLException && cause.sqlState != null) return cause.sqlState
            cause = cause.cause
        }
        return null
    }

    private fun migrate(
        target: DataSource,
        upTo: String? = null,
    ) {
        val configuration = Flyway.configure().dataSource(target).locations("classpath:db/migration")
        if (upTo != null) configuration.target(MigrationVersion.fromVersion(upTo))
        configuration.load().migrate()
    }

    private fun appliedVersions(target: DataSource): List<String> =
        target.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                    .use(::readVersions)
            }
        }

    private fun readVersions(rows: java.sql.ResultSet): List<String> {
        val versions = mutableListOf<String>()
        while (rows.next()) rows.getString(1)?.let { versions += it }
        return versions
    }

    /** 버릴 클러스터 하나 — 이 test 만 쓰고 끝에 멈춘다(공유 컨테이너를 오염시키지 않는다). */
    private fun withOwnCluster(body: (DataSource) -> Unit) {
        val container =
            PostgreSQLContainer(DockerImageName.parse(PersistenceTestSupport.POSTGRES_IMAGE))
                .withDatabaseName("bidvector_lock_probe")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
        container.start()
        try {
            val source =
                PGSimpleDataSource().apply {
                    setUrl(container.jdbcUrl)
                    user = container.username
                    password = container.password
                }
            body(source)
        } finally {
            container.stop()
        }
    }
}
