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

/**
 * 마이그레이션이 **실패로 끝나야 하는** 시간 — 그 안에 안 끝나면 「기다렸다」는 뜻이다.
 * 마이그레이션의 `SET LOCAL lock_timeout`(5s)보다 넉넉하다.
 */
private const val ELAPSED_BOUND_MS = 20_000L

/** 홀더가 잠금을 **쥐었다고 알리기까지** 기다리는 상한 — 위 상한과 뜻이 다르다(cr L-2). */
private const val ACQUIRE_BOUND_MS = 20_000L

/** 홀더 스레드가 **끝나기를** 기다리는 상한 — 또 다른 뜻이다. */
private const val JOIN_BOUND_MS = 20_000L

/**
 * 충돌 잠금을 쥐고 있는 시간 — **경과 상한보다 길다.** 이 값이 test 를 **스스로 끝나게** 한다:
 * 상한 줄이 없으면 migrate 는 여기까지 기다렸다가 **성공**하고, 그때 `shouldThrow` 가 실패한다
 * (무한 대기로 빌드가 멎지 않는다). JUnit 의 기본 `@Timeout` 은 같은 스레드에서 끝난 **뒤에**
 * 판정하므로 막힌 JDBC 호출을 구해 주지 못한다 — 그래서 상한을 test 자신이 들고 있어야 한다.
 */
private const val LOCK_HOLD_MS = 35_000L

/**
 * 그 자기 종료 불변식을 **코드가 센다**(cr L-2 — 앞 판은 산문에만 있었다): 홀더가 쥐는 시간이 경과
 * 상한보다 길어야 「기다렸다」와 「실패했다」가 구별된다.
 */
private fun requireSelfTerminating() {
    require(LOCK_HOLD_MS > ELAPSED_BOUND_MS) { "홀더가 먼저 놓으면 「기다렸다」를 「실패했다」와 가를 수 없다" }
}

/** V18 직전까지만 적용한다 — 그 다음 한 문장이 이 test 의 대상이다. */
private const val BEFORE_MASKING_MIGRATION = "17"

/** V19 직전까지 적용한다(V18 포함) — 그 다음 파일이 인덱스 축이다. */
private const val BEFORE_INDEX_MIGRATION = "18"

private const val INDEX_MIGRATION = "19"

/** V26 직전까지 적용한다(V25 포함) — 그 다음 파일이 종단 사유 축이다. */
private const val BEFORE_TERMINAL_REASON_MIGRATION = "25"

/** 마지막 마이그레이션 — 재시도가 끝까지 선다는 것을 그 번호로 확인한다. */
private const val LAST_MIGRATION = "27"

/** V26 이 만나는 상대 — relay·보존이 `outbox` 에 쥐는 평범한 읽기(`ACCESS SHARE`). */
private const val LOCK_OUTBOX_READ_SQL = "SELECT 1 FROM outbox LIMIT 1"

/** 관례가 선 자리 — V18 이 첫 선언이고 그 아래는 관례 이전 구간이다. */
private const val CONVENTION_FROM_VERSION = 18

/**
 * **기존 표를 건드리는 마이그레이션 전수**(관례 구간). 새 마이그레이션이 기존 표를 `ALTER` 하거나
 * 그 표 위에 인덱스·트리거를 만들면 이 집합이 늘고, 그때 이 줄을 고치게 된다 — 상한 선언을
 * **사람이 보는 자리**에 둔다(면제 계수 래칫과 같은 관용구).
 */
private val VERSIONS_TOUCHING_EXISTING_TABLES = setOf(18, 19, 24, 25, 26, 27)

/**
 * 건드리지 않는데 **상한을 선언한** 자리 — V23 하나다. 그 파일은 새 표를 만들면서 `account` 에 FK
 * 검증 잠금을 쥐고 그 창을 같은 사유로 막았다(파일 주석이 그렇게 적는다). 과잉 선언은 안전한
 * 쪽이라 거부하지 않고 **이름을 적어 둔다** — 그래야 선언이 조용히 사라지는 것도 보인다.
 * 같은 모양의 V21·V22(새 표 + `account` FK)는 선언하지 않았다. 즉 **FK 축은 관례가 아니다.**
 */
private val VERSIONS_DECLARING_WITHOUT_TOUCHING = setOf(23)

private val SQL_LINE_COMMENT = Regex("--[^\n]*")
private val CREATE_TABLE_TARGET =
    Regex("""CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z_][a-z0-9_]*)""", RegexOption.IGNORE_CASE)
private val ALTER_TABLE_TARGET =
    Regex("""ALTER\s+TABLE\s+(?:ONLY\s+)?([a-z_][a-z0-9_]*)""", RegexOption.IGNORE_CASE)
private val CREATE_INDEX_TARGET =
    Regex(
        """CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:CONCURRENTLY\s+)?(?:IF\s+NOT\s+EXISTS\s+)?\S+\s+ON\s+([a-z_][a-z0-9_]*)""",
        RegexOption.IGNORE_CASE,
    )
private val CREATE_TRIGGER_TARGET =
    Regex("""CREATE\s+TRIGGER\s+[\s\S]*?\sON\s+([a-z_][a-z0-9_]*)""", RegexOption.IGNORE_CASE)
private val LOCK_TIMEOUT_STATEMENT = Regex("""SET\s+LOCAL\s+lock_timeout""", RegexOption.IGNORE_CASE)

/** 한 마이그레이션 파일 — **주석을 걷은 본문**만 본다(주석 속 `ALTER TABLE` 인용이 대상을 늘리지 않게). */
private class MigrationSource(
    val version: Int,
    val statements: String,
) {
    fun tablesCreated(): Set<String> = targetsOf(CREATE_TABLE_TARGET)

    /** 이 파일이 **DDL 대상으로 삼는** 표 — `ALTER` 와 그 표 위의 인덱스·트리거다. */
    fun tablesTargeted(): Set<String> =
        targetsOf(ALTER_TABLE_TARGET) + targetsOf(CREATE_INDEX_TARGET) + targetsOf(CREATE_TRIGGER_TARGET)

    fun declaresLockBound(): Boolean = LOCK_TIMEOUT_STATEMENT.containsMatchIn(statements)

    private fun targetsOf(pattern: Regex): Set<String> =
        pattern.findAll(statements).map { it.groupValues[1].lowercase() }.toSet()
}

/**
 * V18 이 만나는 상대 — 평범한 **읽기**가 쥐는 `ACCESS SHARE`. `ALTER TABLE` 의 `ACCESS EXCLUSIVE` 는
 * 그것과도 부딪힌다(그래서 그 창에서는 읽기까지 큐에 선다).
 */
private const val LOCK_READ_SQL = "SELECT 1 FROM raw_observation LIMIT 1"

/**
 * V19 가 만나는 상대 — **진행 중인 쓰기**가 쥐는 `ROW EXCLUSIVE`(수집 INSERT 가 쥐는 그 잠금이다).
 * `CREATE INDEX` 의 `SHARE` 는 읽기와는 부딪히지 않고 이것과 부딪힌다. 행을 지어내지 않고 잠금만
 * 명시로 쥔다 — 재는 것이 잠금 충돌이고 행 내용이 아니다.
 */
private const val LOCK_WRITE_SQL = "LOCK TABLE notice IN ROW EXCLUSIVE MODE"

/**
 * **두 마이그레이션의 첫 문장(`SET LOCAL lock_timeout`)을 거동으로 잰다**(D-6B3-5 ⑤ · mig M-1 ·
 * cr M-1 · mig L-1).
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
 * V18 과 V19 는 **잠금 모드가 다르고 충돌 상대도 다르다**: V18 의 `ALTER TABLE` 은
 * `ACCESS EXCLUSIVE` 라 평범한 **읽기**(`ACCESS SHARE`)와도 부딪히고, V19 의 `CREATE INDEX` 는
 * `SHARE` 라 읽기는 지나가지만 **진행 중인 쓰기**(`ROW EXCLUSIVE`)와 부딪힌다. 그래서 두 test 의
 * 홀더가 쥐는 잠금이 다르다 — 각자 그 마이그레이션이 실제로 만나는 상대다.
 *
 * **자기 컨테이너를 띄운다.** 공유 하네스의 DB 는 V19 까지 이미 적용돼 있어 「V18 직전」을 만들 수
 * 없고, 같은 클러스터에 두 번째 데이터베이스를 만드는 길도 막혀 있다 — V2 가 만드는 역할은
 * **클러스터 범위**라 두 번째 적용이 「role … already exists」로 깨진다(실측: 그 경로로 처음 짠 이
 * test 가 그 이유로 실패했다 — 틀린 사유의 RED 였다). 그래서 격리 단위가 데이터베이스가 아니라
 * 클러스터다.
 *
 * **비용(cr L-3)**: test 메서드마다 PostgreSQL 클러스터 하나를 띄우고 끝에 멈춘다 — `check` 가 도는
 * 동안 공유 컨테이너와 **이것 하나**가 함께 산다(메서드는 순차 실행이라 둘이 겹치지 않는다).
 * 이 호스트는 동시 무거운 작업으로 earlyoom 사고 이력이 있으므로(`CLAUDE.md` 호스트 규율) 그 사실을
 * 여기 적어 둔다. 상한 줄이 없는 구판(변이) 경로는 홀더가 놓기를 기다리므로 **40초대**를 쓴다.
 */
class MigrationLockTimeoutTest {
    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun `읽기 잠금이 있으면 V18 은 기다리지 않고 실패하고, 풀린 뒤 재시도가 선다`() {
        requireSelfTerminating()
        withOwnCluster { probe ->
            migrate(probe, upTo = BEFORE_MASKING_MIGRATION)
            appliedVersions(probe) shouldContain BEFORE_MASKING_MIGRATION

            val holder = LockHolder(probe)
            val failure =
                try {
                    holder.acquire()

                    var thrown: FlywayException? = null
                    val elapsed = measureTimeMillis { thrown = shouldThrow<FlywayException> { migrate(probe) } }

                    (elapsed < ELAPSED_BOUND_MS) shouldBe true
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

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun `쓰기 잠금이 있으면 V19 는 기다리지 않고 실패하고, 풀린 뒤 재시도가 선다`() {
        requireSelfTerminating()
        withOwnCluster { probe ->
            migrate(probe, upTo = BEFORE_INDEX_MIGRATION)
            appliedVersions(probe) shouldContain BEFORE_INDEX_MIGRATION

            val holder = LockHolder(probe, LOCK_WRITE_SQL)
            val failure =
                try {
                    holder.acquire()

                    var thrown: FlywayException? = null
                    val elapsed = measureTimeMillis { thrown = shouldThrow<FlywayException> { migrate(probe) } }

                    (elapsed < ELAPSED_BOUND_MS) shouldBe true
                    thrown
                } finally {
                    holder.release()
                }

            sqlStateOf(failure) shouldBe LOCK_NOT_AVAILABLE

            // 인덱스 열 개가 통째로 롤백됐다 — 재시도가 전체 빌드를 다시 내지만 **선다**(runbook §1.3).
            migrate(probe)
            appliedVersions(probe) shouldContain INDEX_MIGRATION
        }
    }

    /**
     * **V26 — 이 slice 가 더한 자리의 거동**(cr H-1 · v 독립 재확인). verifier 는 상한 줄이 없을 때
     * 열린 읽기 뒤에서 V26 migrate 가 **25 s 뒤에도 대기 중**이라고 쟀다(홀더를 커밋해야 적용됐다).
     * 그 측정을 test 로 고정한다 — 상한이 있으면 같은 상황이 **상한 안의 실패**다.
     *
     * 상대가 평범한 읽기인 이유는 V18 과 같다(`ALTER TABLE` 의 `ACCESS EXCLUSIVE` 는 `ACCESS SHARE`
     * 와도 부딪힌다). 이 표에서는 그 상대가 상시 있다 — relay 소비자가 연결을 본문 내내 쥔다.
     *
     * **V27 은 거동을 따로 재지 않는다**: 첫 문장이 같은 모양(`ALTER TABLE … ADD COLUMN`)이고 같은
     * 잠금 모드라 이 test 가 재는 성질이 같으며, **전수는 아래 census 가 집합으로 닫는다**. 클러스터
     * 하나가 test 메서드마다 서는 비용(이 파일 KDoc 의 호스트 규율)을 같은 술어에 두 번 쓰지 않는다.
     */
    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun `읽기 잠금이 있으면 V26 은 기다리지 않고 실패하고, 풀린 뒤 재시도가 선다`() {
        requireSelfTerminating()
        withOwnCluster { probe ->
            migrate(probe, upTo = BEFORE_TERMINAL_REASON_MIGRATION)
            appliedVersions(probe) shouldContain BEFORE_TERMINAL_REASON_MIGRATION

            val holder = LockHolder(probe, LOCK_OUTBOX_READ_SQL)
            val failure =
                try {
                    holder.acquire()

                    var thrown: FlywayException? = null
                    val elapsed = measureTimeMillis { thrown = shouldThrow<FlywayException> { migrate(probe) } }

                    (elapsed < ELAPSED_BOUND_MS) shouldBe true
                    thrown
                } finally {
                    holder.release()
                }

            sqlStateOf(failure) shouldBe LOCK_NOT_AVAILABLE

            migrate(probe)
            appliedVersions(probe) shouldContain LAST_MIGRATION
        }
    }

    /**
     * **모집단을 둘로 닫는다**(cr H-1 처방의 구조 쪽). 위 셋은 **고른 세 자리**의 거동을 재므로,
     * 네 번째 마이그레이션이 상한 없이 들어와도 초록이다 — 그것이 이 결함이 리뷰까지 온 경로다.
     * 그래서 파일 전수에서 모집단을 **계산**하고 집합으로 대조한다.
     *
     * 술어는 「기존 표를 DDL 대상으로 삼는가」다 — `ALTER TABLE`, 그 표 위의 `CREATE INDEX`·
     * `CREATE TRIGGER`. 「기존」은 **더 낮은 번호의 파일이 만든 표**이고, 파일마다 자기가 만든 표를
     * 더해 가며 센다. 주석은 걷고 본다.
     *
     * **FK 참조는 넣지 않았다 — 관례가 아니기 때문이다.** V21·V22 는 새 표에서 `account` 를
     * 참조하면서 선언하지 않았고 V23 은 같은 모양에서 선언했다. 참조를 모집단에 넣으면 이 등식이
     * **기존 두 파일 때문에** 붉어지고, 그러면 이 slice 가 남의 마이그레이션을 고치게 된다. 그 비대칭은
     * 숨기지 않고 [VERSIONS_DECLARING_WITHOUT_TOUCHING] 이 이름으로 든다.
     *
     * **관례 이전 구간(V17 이하)은 모집단 밖이다.** 그 경계가 가정이 아니라 사실이 되도록 아래가
     * 두 가지를 함께 센다 — 그 구간에도 기존 표를 건드린 파일이 **있고**(여덟), 그 가운데 선언은
     * **0** 이다. 경계를 뒤로 미루면 이 줄이 먼저 붉어진다.
     */
    @Test
    fun `기존 표를 건드리는 마이그레이션 전수가 잠금 상한을 선언한다 — 집합 등식`() {
        val sources = migrationSources()
        val created = mutableMapOf<String, Int>()
        val touching = mutableSetOf<Int>()
        sources.forEach { source ->
            if (source.tablesTargeted().any { table -> created[table]?.let { it < source.version } == true }) {
                touching += source.version
            }
            source.tablesCreated().forEach { table -> created.putIfAbsent(table, source.version) }
        }
        val declaring = sources.filter { it.declaresLockBound() }.map { it.version }.toSet()
        val inConvention = { version: Int -> version >= CONVENTION_FROM_VERSION }

        // ① 안전 성질 — 기존 표를 건드리는데 상한이 없는 마이그레이션은 **없다**(새 파일에도 선다).
        touching.filter(inConvention).toSet() - declaring shouldBe emptySet()
        // ② 비공허성·래칫 — 술어가 조용히 대상을 놓치면(또는 새 파일이 들어오면) 이 줄이 붉다.
        touching.filter(inConvention).toSet() shouldBe VERSIONS_TOUCHING_EXISTING_TABLES
        // ③ 반대 방향 — 과잉 선언은 V23 하나이고, 선언이 사라지면 이 줄이 붉다.
        declaring.filter(inConvention).toSet() - touching shouldBe VERSIONS_DECLARING_WITHOUT_TOUCHING
        // ④ 관례 경계가 사실인가 — 그 아래에도 건드린 파일이 있고(여덟), 선언은 0 이다.
        touching.filterNot(inConvention).size shouldBe 8
        declaring.filterNot(inConvention) shouldBe emptyList()
    }

    /** `V26__…sql` 의 번호 — 파일 이름이 정본이고 Flyway 가 읽는 규칙과 같다. */
    private fun versionOf(fileName: String): Int = fileName.substringBefore("__").removePrefix("V").toInt()

    /** 출하되는 마이그레이션 자원 전수 — 번호 순. 본문은 주석을 걷은 것이다. */
    private fun migrationSources(): List<MigrationSource> {
        val root = checkNotNull(javaClass.classLoader.getResource("db/migration")) { "마이그레이션 자원을 찾지 못했다" }
        check(root.protocol == "file") { "마이그레이션 자원이 디렉터리가 아니다: $root" }
        val files =
            checkNotNull(java.io.File(root.toURI()).listFiles { file -> file.name.endsWith(".sql") }) {
                "마이그레이션 자원 디렉터리를 읽지 못했다"
            }
        val sources =
            files
                .map { file ->
                    MigrationSource(
                        version = versionOf(file.name),
                        statements = SQL_LINE_COMMENT.replace(file.readText(), ""),
                    )
                }.sortedBy { it.version }
        // 모집단이 비면 위 단언들이 공허해진다 — 자원 읽기 자체를 먼저 센다.
        check(sources.size >= VERSIONS_TOUCHING_EXISTING_TABLES.size) { "마이그레이션을 너무 적게 읽었다: ${sources.size}" }
        return sources
    }

    /**
     * 다른 연결이 **열린 트랜잭션**에서 `ACCESS SHARE` 를 쥔다 — `ALTER TABLE` 의 `ACCESS EXCLUSIVE`
     * 와 충돌하는 가장 평범한 모양(조회 하나)이다. 스스로 [LOCK_HOLD_MS] 뒤에 놓는다.
     */
    private class LockHolder(
        private val source: DataSource,
        private val lockSql: String = LOCK_READ_SQL,
    ) {
        private val acquired = CountDownLatch(1)
        private val releaseNow = CountDownLatch(1)
        private var worker: Thread? = null

        fun acquire() {
            worker =
                thread(name = "v18-lock-holder", isDaemon = true) {
                    source.connection.use { connection ->
                        connection.autoCommit = false
                        connection.createStatement().use { statement -> statement.execute(lockSql) }
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
            holderThread?.join(JOIN_BOUND_MS)
            val finished = holderThread?.isAlive != true
            // 홀더가 안 끝난 상태를 **그 자리에서** 이름 붙인다(cr L-2) — 안 그러면 뒤의 migrate 가
            // 55P03 으로 떨어지는 혼란한 실패로 나타난다.
            check(finished) { "충돌 잠금 홀더가 끝나지 않았다" }
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
