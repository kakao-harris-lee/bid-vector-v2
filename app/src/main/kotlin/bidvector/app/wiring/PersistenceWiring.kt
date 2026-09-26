package bidvector.app.wiring

import bidvector.adapters.strategy.JdbcStrategyRepository
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyPolicyData
import bidvector.workflow.strategy.StrategyRepository
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * DataSource·Flyway·전략 repository 조립(scope.md in_scope) — 이 slice가 production 코드에
 * **처음** Flyway 기동 배선을 놓는다(`PersistenceTestSupport`는 test 전용, preflight 조사
 * 확인). D-6A1-18 — `PGSimpleDataSource`(비풀링) 그대로, HikariCP를 들이지 않는다
 * (`OPEN-6A1-CONNECTION-POOL`).
 *
 * (2b) 「경계로 처리」 — 이 클래스는 환경변수 → `DataSource`/`Flyway` 호출 변환만 하고
 * 도메인 값을 만들지 않는다(`STRATEGY_POLICY.resolve`가 유일한 정책 해소이고, 실패하면
 * 지어내지 않고 던진다 — `bidvector.strategy` 밖에서 `OperatorStrategy`를 만들지 않는다는
 * D-6F1-2 불변식을 조립도 지킨다).
 */
@Configuration
@EnableConfigurationProperties(PersistenceProperties::class)
open class PersistenceWiring {
    @Bean
    open fun dataSource(properties: PersistenceProperties): DataSource =
        PGSimpleDataSource().apply {
            setUrl(properties.jdbcUrl)
            user = properties.username
            password = properties.credential
        }

    /**
     * 기동 시 1회 migrate 후 [JdbcStrategyRepository]를 만든다 — 정책은 항상 해소된다
     * (`STRATEGY_POLICY`가 `EffectiveFrom.Initial` 항목 하나뿐이라 어떤 기준일도 적용된다,
     * preflight 조사 확인). 해소되지 않으면 지어내지 않고 기동을 실패시킨다.
     */
    @Bean
    open fun strategyRepository(dataSource: DataSource): StrategyRepository {
        migrate(dataSource)
        val resolution = STRATEGY_POLICY.resolve(LocalDate.now())
        val resolved =
            resolution as? Resolution.Resolved<StrategyPolicyData>
                ?: error("전략 정책이 해소되지 않았다: $resolution")
        return JdbcStrategyRepository(dataSource, resolved)
    }

    private fun migrate(dataSource: DataSource) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}

/**
 * DataSource 설정 키(D-6A1-19) — `…password`를 쓰지 않는다(`leak-patterns.txt` 자기참조,
 * 팀장 실측). 값은 환경변수 주입, 기본값 없음 — Spring의 relaxed binding이 누락 시
 * `BindException`으로 기동을 fail-fast 시킨다(직접 null 검사를 재구현하지 않는다).
 *
 * **D-6A2a-13 ①(privacy-gate r1 L-2) — `data class`가 아니다.** 컴파일러가 합성하는
 * `toString()`은 [credential] 원문을 그대로 낸다. Boot의 바인딩 실패 분석기는 실패한 속성의
 * 값을 문면에 내고, 기동 실패 문면은 CI job 로그로도 운영 배치의 **영구 로그**로도 간다 —
 * 지금 이 타입에 형식 검증이 없어 그 경로가 닫혀 있을 뿐이고, 뒤 slice 가 검증을 붙이는
 * 순간 열린다. [bidvector.app.OperatorCredentialProperties](6A-1 r4)와 같은 근거이며 같은
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
