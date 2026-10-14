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
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

/** `lock_not_available` — `lock_timeout` 이 만료됐을 때의 SQLSTATE. 대기가 **실패로** 끝났다는 증거다. */
private const val LOCK_NOT_AVAILABLE = "55P03"

/** V18 의 `SET LOCAL lock_timeout` 값(5s)보다 넉넉하고, 「기다리지 않는다」를 말할 만큼 좁은 상한. */
private const val FAILURE_BOUND_MS = 30_000L

/** 이 probe 가 쓰는 임시 데이터베이스 — V17 까지만 적용한 상태를 만들어야 하므로 공유 DB 를 쓸 수 없다. */
private const val PROBE_DATABASE = "ret_v18_lock_probe"

/**
 * **V18 의 첫 문장(`SET LOCAL lock_timeout`)을 거동으로 잰다**(D-6B3-5 ⑤ · mig M-1).
 *
 * 왜 거동으로 재는가: 그 한 줄이 막는 것은 **「정지」**다. `ALTER TABLE` 은 `ACCESS EXCLUSIVE` 를
 * 요구하므로 그 표에 열린 트랜잭션이 하나라도 있으면(평범한 `SELECT` 의 `ACCESS SHARE` 로 충분하다)
 * 큐에서 기다리고, 기본 `lock_timeout` 은 **0**(무한)이며 Flyway 는 걸지 않는다. 마이그레이션이
 * 기동의 부수효과이므로 그 대기는 곧 **기동 정지**다 — 되돌리기 어려운 경로에서 「실패」보다 비싼
 * 실패 모양이다.
 *
 * 그래서 재는 것 셋이다: ① 상한 안에 **끝난다** ② 끝나는 방식이 `lock_not_available` **실패**다
 * ③ 잠금이 풀린 뒤 **재시도가 선다**(실패가 기동을 영구히 막지 않는다 — 그 재시도에 `repair` 가
 * 필요한지도 이 test 가 답한다: PostgreSQL 은 DDL 이 트랜잭션 안이라 실패한 마이그레이션이
 * 이력에 남지 않는다).
 *
 * 파일 문면을 보는 술어를 쓰지 않는다 — 그런 게이트는 주석 한 줄로 열린다. 변이(그 줄 삭제)는 이
 * test 를 **[Timeout] 으로** 붉게 만든다(무한 대기가 곧 그 변이의 모양이다).
 */
class V18LockTimeoutTest : PersistenceTestSupport() {
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    fun `충돌 잠금이 있으면 V18 은 기다리지 않고 실패하고, 풀린 뒤 재시도가 선다`() {
        withProbeDatabase { probe ->
            migrate(probe, upTo = "17")

            val failure =
                probe.connection.use { holder ->
                    holder.autoCommit = false
                    holder.createStatement().use { statement ->
                        statement.executeQuery("SELECT 1 FROM raw_observation LIMIT 1").close()
                    }

                    var thrown: FlywayException? = null
                    val elapsed = measureTimeMillis { thrown = shouldThrow<FlywayException> { migrate(probe) } }

                    (elapsed < FAILURE_BOUND_MS) shouldBe true
                    holder.rollback()
                    thrown
                }

            sqlStateOf(failure) shouldBe LOCK_NOT_AVAILABLE

            // 잠금이 풀렸다 — 같은 명령이 이제 선다(실패가 기동을 영구히 막지 않는다).
            migrate(probe)
            appliedVersions(probe) shouldContain "19"
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
                    .use { rs ->
                        val versions = mutableListOf<String>()
                        while (rs.next()) rs.getString(1)?.let { versions += it }
                        versions
                    }
            }
        }

    /**
     * 공유 컨테이너 안의 **임시 데이터베이스** — 공유 DB 는 V19 까지 이미 적용돼 있어 「V18 직전」을
     * 만들 수 없다. 끝에 `WITH (FORCE)` 로 지운다(남은 연결이 있어도 선다).
     */
    private fun withProbeDatabase(body: (DataSource) -> Unit) {
        admin("DROP DATABASE IF EXISTS $PROBE_DATABASE WITH (FORCE)")
        admin("CREATE DATABASE $PROBE_DATABASE")
        try {
            body(probeDataSource())
        } finally {
            admin("DROP DATABASE IF EXISTS $PROBE_DATABASE WITH (FORCE)")
        }
    }

    private fun admin(sql: String) =
        dataSource().connection.use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }

    /** 공유 컨테이너의 접속 URL 에서 데이터베이스 이름만 바꾼다(포트를 test 가 짓지 않는다). */
    private fun probeDataSource(): DataSource {
        val url = dataSource().connection.use { it.metaData.url }
        return PGSimpleDataSource().apply {
            setUrl(url.replaceAfterLast('/', PROBE_DATABASE))
            user = PROBE_USER
            password = PROBE_PASSWORD
        }
    }

    private companion object {
        /** 공유 하네스와 같은 자격 — `PersistenceTestSupport` 의 컨테이너 설정이 정본이다. */
        const val PROBE_USER = "bidvector_admin"
        const val PROBE_PASSWORD = "bidvector_test_only"
    }
}
