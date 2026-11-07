package bidvector.app.wiring

import bidvector.app.adminDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

/** `lock_not_available` — `lock_timeout` 이 만료됐다는 증거다. 대기가 **실패로** 끝났다. */
private const val LOCK_NOT_AVAILABLE = "55P03"

/** 실패로 끝나야 하는 시간 — 출하 상한(5s)보다 넉넉하고 아래 보유 시간보다 짧다. */
private const val ELAPSED_BOUND_MS = 20_000L

/** 홀더가 잠금을 **쥐었다고 알리기까지** 기다리는 상한. */
private const val ACQUIRE_BOUND_MS = 20_000L

/**
 * 충돌 잠금을 쥐는 시간 — **경과 상한보다 길다.** 상한이 없으면 migrate 는 여기까지 기다렸다가
 * **성공**하고 그때 `shouldThrow` 가 실패한다(무한 대기로 빌드가 멎지 않는다).
 */
private const val LOCK_HOLD_MS = 35_000L

/**
 * **상한을 선언하지 않은** 마이그레이션 직전까지 적용한다. V14 는 `notice` 를 `ALTER` 하면서
 * `SET LOCAL lock_timeout` 을 적지 않는다(V18 이 관례를 세우기 전 파일이다) — 그래서 이 자리에서
 * 상한이 서는 유일한 이유는 **연결 설정**이다.
 */
private const val BEFORE_UNDECLARED_ALTER_MIGRATION = "13"

/** V14 가 `ALTER` 하는 표 — 평범한 읽기(`ACCESS SHARE`)가 그 `ACCESS EXCLUSIVE` 와 부딪힌다. */
private const val LOCK_READ_SQL = "SELECT 1 FROM notice LIMIT 1"

private const val POSTGRES_IMAGE = "postgres:16.4"

/**
 * **출하 마이그레이션 구성의 잠금 상한이 연결에 걸려 있는가**(verifier r6 V-1).
 *
 * 앞 라운드는 상한을 **파일마다** 적고 그 선언을 정규식 census 로 셌다. 그 술어는 SQL 을 파싱하지
 * 않으므로 평범한 표기(`ALTER TABLE IF EXISTS`·스키마 한정·따옴표·동적 SQL·`DROP TRIGGER`)에
 * 빗나가고, 값이 `0` 이거나 DDL **뒤에** 적힌 선언도 선언으로 센다 — 실측 15 변이 가운데 10 이
 * 초록이었다. 그래서 바닥을 **연결**로 내렸다([PersistenceWiring.ownerMigration] 의 `initSql`).
 *
 * **이 test 가 고르는 자리가 그 차이를 가른다.** V14 는 상한을 선언하지 않은 채 기존 표를
 * `ALTER` 하는 파일이다. 파일 census 로는 영원히 닿지 않고(관례 이전 구간), 파일 선언으로도
 * 막히지 않는다. 여기서 `55P03` 이 나오면 상한을 세운 것은 **연결 하나**다 — 그래서 이 단언이
 * 「모든 현재·미래 마이그레이션」을 덮는다.
 *
 * **출하 구성을 그대로 부른다.** 준비(V13 까지)는 target 을 주어야 해서 별도 Flyway 지만, **재는
 * 호출**은 `ownerMigration(...)` 하나다 — test 가 자기 사본으로 Flyway 를 세우면 출하 쪽
 * `initSql` 을 지워도 초록이다.
 *
 * **비용**: 버릴 클러스터 하나(`check` 동안 공유 컨테이너와 함께 산다). 이 호스트의 메모리 규율
 * (`CLAUDE.md`)을 그대로 따라 적어 둔다 — adapters 의 `MigrationLockTimeoutTest` 와 같은 사유다.
 */
class MigrationLockBoundTest {
    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun `상한을 선언하지 않은 마이그레이션도 연결 설정 덕에 기다리지 않고 실패한다`() {
        require(LOCK_HOLD_MS > ELAPSED_BOUND_MS) { "홀더가 먼저 놓으면 「기다렸다」를 「실패했다」와 가를 수 없다" }
        withOwnCluster { owner ->
            migrateUpTo(owner, BEFORE_UNDECLARED_ALTER_MIGRATION)

            val holder = LockHolder(owner)
            val failure =
                try {
                    holder.acquire()

                    var thrown: FlywayException? = null
                    val elapsed =
                        measureTimeMillis {
                            thrown = shouldThrow<FlywayException> { PersistenceWiring.ownerMigration(owner).migrate() }
                        }

                    (elapsed < ELAPSED_BOUND_MS) shouldBe true
                    thrown
                } finally {
                    holder.release()
                }

            sqlStateOf(failure) shouldBe LOCK_NOT_AVAILABLE

            // 잠금이 풀리면 같은 구성이 끝까지 선다 — 상한이 기동을 영구히 막지 않는다.
            PersistenceWiring.ownerMigration(owner).migrate()
            appliedVersions(owner) shouldContain RELEASE_AXIS_MIGRATION
        }
    }

    /** 준비 단계 — target 을 주어야 하므로 출하 구성이 아니다(재는 호출은 위 하나다). */
    private fun migrateUpTo(
        owner: DataSource,
        version: String,
    ) {
        Flyway
            .configure()
            .dataSource(owner)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate()
    }

    private fun appliedVersions(owner: DataSource): List<String> =
        owner.connection.use { connection ->
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

    /** 다른 연결이 **열린 트랜잭션**에서 `ACCESS SHARE` 를 쥔다 — 조회 하나로 충분하다. */
    private class LockHolder(
        private val source: DataSource,
    ) {
        private val acquired = CountDownLatch(1)
        private val releaseNow = CountDownLatch(1)
        private var worker: Thread? = null

        fun acquire() {
            worker =
                thread(name = "migration-lock-bound-holder", isDaemon = true) {
                    source.connection.use { connection ->
                        connection.autoCommit = false
                        connection.createStatement().use { statement -> statement.execute(LOCK_READ_SQL) }
                        acquired.countDown()
                        releaseNow.await(LOCK_HOLD_MS, TimeUnit.MILLISECONDS)
                        connection.rollback()
                    }
                }
            check(acquired.await(ACQUIRE_BOUND_MS, TimeUnit.MILLISECONDS)) { "충돌 잠금을 쥐지 못했다" }
        }

        fun release() {
            releaseNow.countDown()
            val holderThread = worker
            holderThread?.join(ACQUIRE_BOUND_MS)
            check(holderThread?.isAlive != true) { "충돌 잠금 홀더가 끝나지 않았다" }
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

    /** 버릴 클러스터 하나 — 공유 컨테이너는 이미 전부 적용돼 있어 「V13 직전」을 만들 수 없다. */
    private fun withOwnCluster(body: (DataSource) -> Unit) {
        val container =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_lock_bound")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
        container.start()
        try {
            body(adminDataSource(container))
        } finally {
            container.stop()
        }
    }
}

/**
 * 이 slice 가 더한 마지막 마이그레이션(cr r3 L-3 — 「마지막 마이그레이션」이라는 이름은 V28 이
 * 들어오면 거짓이 된다). 재시도가 **여기까지 왔는가**를 묻는 포함 단언이라 뒤에 새 파일이 생겨도
 * 참이다.
 */
private const val RELEASE_AXIS_MIGRATION = "27"
