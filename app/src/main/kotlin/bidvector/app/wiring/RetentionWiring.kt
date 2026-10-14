package bidvector.app.wiring

import bidvector.adapters.retention.JdbcRetentionStore
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.retention.RetentionRunner
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.strategy.Clock
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 일회성 보존 배선(M6/6B-3 P-3) — **`bidvector.retention.mode=once` 일 때만** 이 설정 전체가
 * 올라온다. 속성이 없으면(기본) 러너도, 소유자 연결도, 저장소도 없다. compose 는 `*.mode` 를
 * 설정하지 않는다(D-6A2a-5) — 그래서 **출하 조립의 기본값은 「보존이 꺼진 상태」**다.
 *
 * **빈이 하나뿐이다**(설계 검토 (2b) 「소유자 연결 팩토리」 행). 소유자 `DataSource`·
 * [JdbcRetentionStore]·[PurgeExpiredData] 는 모두 아래 `@Bean` 함수 **안의 지역 객체**다 —
 * 컨텍스트에 그 셋 가운데 어느 것도 빈으로 없으므로, 다른 빈이 「파기 능력」이나 「소유자 권한
 * 연결」을 주입받을 자리가 없다. `PersistenceWiring` 이 `migrateAsOwner` 의 소유자 연결을
 * 지역 객체로 두는 것과 같은 규율이고 같은 이유다.
 *
 * **런타임 풀을 쓰지 않는다**(운영자 결정 A-1): 풀의 물리 연결마다 `SET ROLE bidvector_app` 이
 * 돌고 그 역할에는 대상 표 어디에도 DELETE·UPDATE 권한이 없다. 그래서 풀을 넘기면 모든 파기가
 * 권한 오류로 끝난다 — 경계를 넓히는 대신 경계 밖(소유자 비풀링 연결)에서 돈다.
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.retention", name = ["mode"], havingValue = "once")
open class RetentionWiring {
    /**
     * **풀 `DataSource` 를 인자로 받지 않는다** — 이 배선은 런타임 풀을 손에 쥐지 않는다.
     *
     * migrate 와의 순서는 그 때문에 깨지지 않는다: migrate 는 `PersistenceWiring.dataSource`
     * 빈 생성의 부수효과이고 그 빈은 **refresh 중에** 만들어지는 싱글턴이다. 이 함수가 그보다
     * 먼저 돌더라도 여기서는 질의가 하나도 돌지 않는다([ownerDataSource] 는 연결을 열지 않고,
     * `JdbcRetentionStore` 는 생성자에서 DB 에 닿지 않는다) — 실제 질의는 `ApplicationRunner`
     * 가 refresh **뒤에** 부르는 `run()` 안에서만 돈다. 그래서 V18 이 적용되지 않은 채 마스킹
     * 질의가 도는 조합이 없다.
     */
    @Bean
    open fun retentionRunner(
        properties: PersistenceProperties,
        clock: Clock,
        termination: CollectionTermination,
    ): RetentionRunner {
        // 정책 해소도 **주입된 시계**로 한다(cr L-4) — 러너가 시계를 받으면서 정책만 시스템
        // 시계로 고르면 그 둘이 갈리는 날이 장부 밖이다.
        val referenceDate = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC)
        val policy = resolvedOrFail(RETENTION_POLICY.resolve(referenceDate), "보존 정책")
        // 마스킹이 지울 키는 개념으로 선언되고 계약이 그 개념의 키를 안다(D-6B3-3 ①). **그 계약
        // 레지스트리를 이 배선이 손에 쥐지 않는다** — 기준일만 넘기고 해소는 어댑터가 한다
        // (D-6F8-6 게이트: 「원문 값 획득」의 허용 접근자는 수집 배선 둘뿐이고, 그 게이트가 이
        // 자리를 실제로 막았다 — `commands.md`).
        val store = JdbcRetentionStore(ownerDataSource(properties), referenceDate)
        val logger = LoggerFactory.getLogger(RetentionRunner::class.java)
        return RetentionRunner(
            purge = PurgeExpiredData(store, clock, policy),
            log = CollectionLog { logger.info(it) },
            termination = termination,
        )
    }

    /**
     * 파기 전용 소유자 연결 — **지역 객체**다. `PGSimpleDataSource` 는 연결을 보관하지 않으므로
     * 이 함수가 돌아간 뒤 그 세션을 다시 얻을 경로가 컨텍스트에 없다. 이름표를 다는 이유는
     * 운영자가 `pg_stat_activity` 에서 보존 세션을 앱 풀·migration 과 가를 수 있어야 하기
     * 때문이다(`PersistenceWiring` 의 두 이름표와 같은 축).
     */
    private fun ownerDataSource(properties: PersistenceProperties): PGSimpleDataSource =
        PGSimpleDataSource().apply {
            setUrl(properties.jdbcUrl)
            user = properties.username
            password = properties.credential
            applicationName = RETENTION_APPLICATION_NAME
        }

    companion object {
        /** `pg_stat_activity.application_name` 에 실리는 이름표 — runbook 의 보존 절이 든다. */
        internal const val RETENTION_APPLICATION_NAME = "bidvector-retention"
    }
}
