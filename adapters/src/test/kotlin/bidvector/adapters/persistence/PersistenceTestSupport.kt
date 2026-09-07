package bidvector.adapters.persistence

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import javax.sql.DataSource

/**
 * S-2~S-5 공유 하네스 — Testcontainers `PostgreSQLContainer` 하나를 재사용한다(3D 설계
 * 검토 「구현 지침」). 컨테이너 이미지 태그는 여기 상수 하나가 정본이다(정책 데이터가
 * 아니라 test 상수 — evidence에 출처를 남긴다: PostgreSQL 16 계열, ADR 0004는 버전을
 * 고정하지 않았고 그 고정은 `milestone-6.md` 6C 소관이다).
 *
 * **Docker 부재는 SKIP이 아니라 실패다** — `assumeTrue`를 쓰지 않는다. `PostgreSQLContainer
 * .start()`가 Docker에 닿지 못하면 예외를 던지고, JUnit이 그것을 test 실패로 기록한다
 * (`@Testcontainers`도 `disabledWithoutDocker`를 기본값 false로 둔 채 쓴다).
 */
@Testcontainers(disabledWithoutDocker = false)
abstract class PersistenceTestSupport {
    protected fun dataSource(): DataSource = adminDataSource

    /** 애플리케이션 역할로 전환한 커넥션 — `bidvector_app`은 LOGIN이 없어 `SET ROLE`로만 얻는다. */
    protected fun appConnection(): Connection =
        adminDataSource.connection.apply {
            autoCommit = false
            createStatement().use { it.execute("SET ROLE bidvector_app") }
        }

    @BeforeEach
    fun truncateAllTables() {
        adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "TRUNCATE TABLE rejected_write, notice_audit, notice, opening_result, " +
                        "qualification_text, collection_run, raw_observation RESTART IDENTITY CASCADE",
                )
            }
        }
    }

    companion object {
        private const val POSTGRES_IMAGE = "postgres:16.4"

        private val container: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        val adminDataSource: DataSource =
            PGSimpleDataSource().apply {
                setUrl(container.jdbcUrl)
                user = container.username
                password = container.password
            }

        init {
            Flyway
                .configure()
                .dataSource(adminDataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }
    }
}
