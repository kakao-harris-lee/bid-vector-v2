package bidvector.adapters.persistence

import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.procurement.RawNoticeObservation
import bidvector.sharedkernel.Resolution
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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

    /**
     * **풀 위에서 재는 자리**(M6/6E-2a P-3) — production 의 `DataSource` 는 HikariCP 풀이고,
     * 세션 advisory lock 은 「연결을 쥔다」는 성질에 기댄다. 비풀링 `DataSource` 위에서만 재면
     * 반납·재사용·재생성이 그 성질에 하는 일을 아무도 보지 못한다.
     *
     * **역할 전환(`connectionInitSql`)은 걸지 않는다** — 이 하네스의 시나리오는
     * `pg_terminate_backend` 로 홀더 백엔드를 끊고 그것은 관리자 권한이다. 여기서 재는 축은
     * 「풀 위에서 임대가 성립하는가」이고, 역할 축은 출하 조립을 띄우는 `ProductionPoolRoleTest`
     * (app)가 진다.
     */
    protected fun pooledDataSource(): DataSource = pooledSource

    /** 크기를 지정한 일회용 풀 — 하한 실측([bidvector.adapters.event.PooledLeaseFloorTest])이 쓴다. 호출자가 닫는다. */
    protected fun newPool(
        maximumPoolSize: Int,
        connectionTimeoutMs: Long,
    ): HikariDataSource = buildPool(maximumPoolSize, connectionTimeoutMs)

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
     * 사본을 두지 않는다(production 경로 대신 test 사본을 지나는 자리였다).
     */
    protected fun appendRawObservation(observation: RawNoticeObservation) =
        JdbcRawObservationStore(dataSource(), testFieldContracts(), TEST_RELEASE_SHA).append(observation)

    @BeforeEach
    fun truncateAllTables() {
        adminDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                // outbox·inbox 도 매 test 전 비운다(설계 검토 (4)-⑥) — 빠뜨리면
                // claim/dedup 결과가 실행 순서에 의존하는 조용한 실패가 된다.
                // edit_session 도 같은 이유로 더한다(추가만, 기존 목록 무편집).
                // operator_strategy·operator_strategy_revision 도 매 test 전 비운다
                // (D-6F1-1, outbox·inbox에 쓴 것과 같은 이유 — 싱글턴 행이 test 간에
                // 새어 나가면 「전략 없음」 test가 다른 test의 잔여 행을 보게 된다).
                // operator_profile 도 같은 이유로 더한다(싱글턴, D-6F6-3).
                // notice_requirement도 같은 이유로 더한다(D-6F5-4, 추가만).
                // notice_requirement_row는 FK ON DELETE CASCADE라 헤더가 비워지면 함께 비워진다
                // (명시 나열 없이 CASCADE로 정합, 다른 자식 표와 다른 점 — 명시 대상은 아니다).
                // api_request_audit도 같은 이유로 더한다(D-6A1-7, 추가만) — 감사
                // 행이 test 간에 새어 나가면 「요청당 정확히 한 행」 대조가 실행 순서에 의존한다.
                statement.execute(
                    "TRUNCATE TABLE rejected_write, notice_audit, notice, opening_result, " +
                        "qualification_text, collection_run, raw_observation, outbox, inbox, " +
                        "edit_session, operator_strategy, operator_strategy_revision, " +
                        "operator_profile, notice_requirement, api_request_audit " +
                        "RESTART IDENTITY CASCADE",
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

        /**
         * 공유 풀 — production 상수의 **사본이 아니다**. 이 수의 근거는 한 시나리오가 동시에 쥐는
         * 연결(임대 1 + 트랜잭션 1 + 종료자 1)보다 넉넉하다는 것뿐이다. 하한 그 자체는
         * [bidvector.adapters.event.PooledLeaseFloorTest] 가 자기 풀로 따로 잰다.
         */
        private const val POOLED_TEST_MAX_SIZE = 8
        private const val POOLED_TEST_TIMEOUT_MS = 10_000L

        private val pooledSource: HikariDataSource by lazy {
            buildPool(POOLED_TEST_MAX_SIZE, POOLED_TEST_TIMEOUT_MS)
        }

        private fun buildPool(
            maximumPoolSize: Int,
            connectionTimeoutMs: Long,
        ): HikariDataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = container.jdbcUrl
                    username = container.username
                    password = container.password
                    poolName = "persistence-test-pool-$maximumPoolSize"
                    this.maximumPoolSize = maximumPoolSize
                    connectionTimeout = connectionTimeoutMs
                },
            )

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
