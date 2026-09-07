package bidvector.adapters.persistence

import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.procurement.RawNoticeObservation
import bidvector.sharedkernel.Resolution
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.time.LocalDate
import javax.sql.DataSource

/** test 전역 고정 배포 식별자 — 실제 출처는 M6 6C 소관, 여기서는 비어 있지 않은 값만 필요하다. */
internal const val TEST_RELEASE_SHA = "test-release"

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

    /**
     * 운영 필드 계약 레지스트리 — `KONEPS_COLLECTION_POLICY`는 `EffectiveFrom.Initial` 한
     * entry뿐이라 기준일 값 자체는 관측에 영향이 없다(항상 적용된다).
     */
    protected fun testFieldContracts(): KonepsFieldContractRegistry {
        val resolution = KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 7))
        return (resolution as Resolution.Resolved).value.fieldContracts
    }

    /**
     * 실제 production 경로([JdbcRawObservationStore])로 raw를 append한다 — 손으로 짠 SQL
     * 사본을 두지 않는다(verifier r1 「범위 밖 참고」 — production 경로 대신 test 사본을
     * 지나는 자리였다).
     */
    protected fun appendRawObservation(observation: RawNoticeObservation) =
        JdbcRawObservationStore(dataSource(), testFieldContracts(), TEST_RELEASE_SHA).append(observation)

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
