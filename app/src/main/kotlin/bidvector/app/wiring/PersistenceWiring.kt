package bidvector.app.wiring

import bidvector.adapters.strategy.JdbcStrategyRepository
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyPolicyData
import bidvector.workflow.strategy.StrategyRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * DataSource·Flyway·전략 repository 조립(scope.md in_scope).
 *
 * **M6/6E-2a D-6E2A-1~3 — 런타임 연결과 migration 연결이 갈라졌다.** 런타임은 HikariCP 풀
 * 하나이고 그 풀의 **물리 연결마다** [CONNECTION_INIT_SQL] 이 돌아 최소 권한 역할
 * ([APPLICATION_ROLE], `V2` 가 `NOLOGIN` 으로 만든다)로 전환된다. migration 은 그 역할이
 * 권한을 갖지 않는 일(`flyway_schema_history` 쓰기·DDL)이므로 **풀을 지나지 않고**
 * [migrateAsOwner] 의 지역 비풀링 연결로만 돈다.
 *
 * **방어 경계**(설계 검토 (0)): 이 배선이 막는 것은 **앱 결함** — 정상 DML 경로가 GRANT 밖의
 * 쓰기(outbox DELETE·notice_audit INSERT·TRUNCATE·DDL)를 하면 DB 가 거부한다. 막지 않는 것은
 * 앱 프로세스 장악과 **소유자 자격 값 유출**이다 — 그 값은 여전히 앱 환경에 있고 세션은
 * `RESET ROLE` 을 할 수 있다(`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`).
 *
 * **전제**: 접속 사용자가 [APPLICATION_ROLE] 의 멤버이거나 superuser 여야 `SET ROLE` 이
 * 선다(출하 배포 모양에서 접속 사용자 == DB 소유자). runbook §2.6 이 그 전제를 든다.
 *
 * (2b) 「경계로 처리」 — 이 클래스는 환경변수 → `DataSource`/`Flyway` 호출 변환만 하고
 * 도메인 값을 만들지 않는다(`STRATEGY_POLICY.resolve`가 유일한 정책 해소이고, 실패하면
 * 지어내지 않고 던진다 — `bidvector.strategy` 밖에서 `OperatorStrategy`를 만들지 않는다는
 * D-6F1-2 불변식을 조립도 지킨다).
 */
@Configuration
@EnableConfigurationProperties(PersistenceProperties::class)
open class PersistenceWiring {
    /**
     * **컨텍스트의 유일한 `DataSource`** — migration 용 소유자 연결은 빈이 아니다(설계 검토
     * 우회 3·4). 빈이 둘이면 다른 빈이 소유자 권한 연결을 주입받을 수 있다.
     *
     * **migrate 가 풀보다 먼저 돈다 — 싱글턴 생성 순서가 아니라 이 함수의 순서다.** 앞 판은
     * `strategyRepository` 빈 생성의 부수효과로 migrate 를 돌려 「`DataSource` 를 쓰는 다른 빈이
     * 먼저 생기지 않는다」는 전제에 기대고 있었다. 지금은 풀 객체가 [migrateAsOwner] 가
     * 돌아오기 전에는 **존재할 수 없다** — 순서를 어기려면 이 함수를 고쳐야 하고, 고치면 빈 DB
     * 기동이 즉시 실패한다([APPLICATION_ROLE] 은 `V2` 가 만들므로 migrate 전에는 없고,
     * [CONNECTION_INIT_SQL] 이 그 자리에서 죽는다).
     */
    @Bean
    open fun dataSource(properties: PersistenceProperties): DataSource {
        migrateAsOwner(properties)
        return pool(properties)
    }

    /**
     * **D-6A2b-23 — 해소는 조립에서 한 번이다.**
     * 이 자리와 편집 배선이 각자 `resolve(LocalDate.now())` 를 부르면, 같은 기동 안에서 서로
     * 다른 정책 인스턴스(그리고 자정을 넘기면 다른 값)를 쥘 여지가 생긴다. 정책 파일의
     * 독자를 하나로 둔다 — 해소되지 않으면 지어내지 않고 기동을 실패시킨다.
     */
    @Bean
    open fun resolvedStrategyPolicy(): Resolution.Resolved<StrategyPolicyData> {
        val resolution = STRATEGY_POLICY.resolve(LocalDate.now())
        return resolution as? Resolution.Resolved<StrategyPolicyData>
            ?: error("전략 정책이 해소되지 않았다: $resolution")
    }

    @Bean
    open fun strategyRepository(
        dataSource: DataSource,
        resolvedStrategyPolicy: Resolution.Resolved<StrategyPolicyData>,
    ): StrategyRepository = JdbcStrategyRepository(dataSource, resolvedStrategyPolicy)

    /**
     * 풀 — 설정 키를 새로 만들지 않는다(운영자 결정 A-2 (a)). 값의 근거는 [PersistenceWiring]
     * 의 companion 상수 주석에 있고 runbook §2.6 이 같은 표를 든다.
     *
     * `ApplicationName` 을 다는 이유: 운영자가 `pg_stat_activity` 에서 앱 세션과 다른 세션을
     * 가를 수 있어야 한다. 그 이름표는 migration 세션의 부재를 재는 test 의 입력이기도 하다.
     */
    private fun pool(properties: PersistenceProperties): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = properties.jdbcUrl
                username = properties.username
                password = properties.credential
                poolName = POOL_APPLICATION_NAME
                maximumPoolSize = MAX_POOL_SIZE
                connectionTimeout = CONNECTION_TIMEOUT_MS
                initializationFailTimeout = INITIALIZATION_FAIL_TIMEOUT_MS
                connectionInitSql = CONNECTION_INIT_SQL
                addDataSourceProperty("ApplicationName", POOL_APPLICATION_NAME)
            },
        )

    /**
     * migration 전용 소유자 연결 — **지역 객체**다. 빈으로 노출하지 않고 필드로도 남기지
     * 않으므로 기동이 끝난 뒤 이 연결을 얻을 경로가 컨텍스트에 없다(설계 검토 (2b)
     * 「경계로 처리」). `PGSimpleDataSource` 는 연결을 보관하지 않아 Flyway 가 닫으면 세션이
     * 끝난다 — [MIGRATION_APPLICATION_NAME] 이름표가 그 부재를 **셀 수 있게** 한다.
     */
    private fun migrateAsOwner(properties: PersistenceProperties) {
        val owner =
            PGSimpleDataSource().apply {
                setUrl(properties.jdbcUrl)
                user = properties.username
                password = properties.credential
                applicationName = MIGRATION_APPLICATION_NAME
            }
        Flyway
            .configure()
            .dataSource(owner)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    companion object {
        /** `V2__provenance_guard.sql` 이 `NOLOGIN` 으로 만드는 최소 권한 역할. */
        internal const val APPLICATION_ROLE = "bidvector_app"

        /** 물리 연결마다 한 번 돈다 — Hikari 는 반납 시 역할을 되돌리지 않는다(설계 의도). */
        internal const val CONNECTION_INIT_SQL = "SET ROLE $APPLICATION_ROLE"

        /**
         * 하한은 **2**(임대 전용 1 + 작업 1): relay 가 세션 advisory lock 으로 연결 하나를 본문
         * 내내 쥐므로 1 이면 자기 자신을 기다린다(`PooledLeaseFloorTest` 가 그 교착을 실측).
         * 10 은 Hikari 기본값이며 단일 운영자·일회성 러너라는 오늘의 부하에 여유가 있다 —
         * 이 값을 설정 키로 올리는 것은 운영 실측(M7)이 요구할 때다.
         */
        internal const val MAX_POOL_SIZE = 10

        /** 연결을 못 받으면 요청을 10초까지만 붙잡는다(기본 30초는 한 요청이 그만큼 산다). */
        internal const val CONNECTION_TIMEOUT_MS = 10_000L

        /** 기동 시 첫 연결을 얻지 못하면 **즉시 실패**한다 — 빈 풀로 뜨면 readiness 가 UP 인 채 모든 요청이 죽는다. */
        internal const val INITIALIZATION_FAIL_TIMEOUT_MS = 1L

        /** `pg_stat_activity.application_name` 에 실리는 이름표(runbook §2.6). */
        internal const val POOL_APPLICATION_NAME = "bidvector-app"

        /** 같은 축의 migration 쪽 이름표 — 기동 뒤 이 이름의 세션은 0 이어야 한다. */
        internal const val MIGRATION_APPLICATION_NAME = "bidvector-migration"
    }
}

/**
 * DataSource 설정 키(D-6A1-19) — `…password`를 쓰지 않는다(`leak-patterns.txt` 자기참조,
 * 실측). 값은 환경변수 주입, 기본값 없음 — Spring의 relaxed binding이 누락 시
 * `BindException`으로 기동을 fail-fast 시킨다(직접 null 검사를 재구현하지 않는다).
 *
 * **D-6A2a-13 ① — `data class`가 아니다.** 컴파일러가 합성하는
 * `toString()`은 [credential] 원문을 그대로 낸다. Boot의 바인딩 실패 분석기는 실패한 속성의
 * 값을 문면에 내고, 기동 실패 문면은 CI job 로그로도 운영 배치의 **영구 로그**로도 간다 —
 * 지금 이 타입에 형식 검증이 없어 그 경로가 닫혀 있을 뿐이고, 뒤 slice 가 검증을 붙이는
 * 순간 열린다. [bidvector.app.OperatorCredentialProperties]와 같은 근거이며 같은
 * 처방이다: 재정의하지 않은 `Any.toString()`(클래스명@해시코드)을 쓴다.
 *
 * `equals`/`hashCode`/`copy`/구조 분해도 함께 사라진다 — 이 타입은 [PersistenceWiring.dataSource]
 * 가 한 번 읽고 버리므로 어느 것도 쓰이지 않는다(호출 자리 전수 확인).
 */
@ConfigurationProperties(prefix = "bidvector.persistence")
class PersistenceProperties(
    val jdbcUrl: String,
    val username: String,
    val credential: String,
)
