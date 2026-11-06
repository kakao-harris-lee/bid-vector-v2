package bidvector.app.wiring

import bidvector.adapters.persistence.JdbcOpeningRawAxisSource
import bidvector.adapters.persistence.JdbcOpeningResultRepository
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.collection.OpeningPromotionProperties
import bidvector.app.collection.OpeningPromotionRunner
import bidvector.app.collection.requireOutsideRepository
import bidvector.procurement.CollectionReferenceDate
import bidvector.workflow.collection.PromoteOpeningResultsUseCase
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.strategy.Clock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.nio.file.Path
import javax.sql.DataSource

/**
 * 개찰 결과 canonical 승격 배선(M7/7C⑤-0) — **`bidvector.opening-promote.mode=once` 일 때만** 이
 * 설정 전체가 올라온다. 속성이 없으면(기본) 러너도, 읽기 어댑터도, canonical 저장소도 서지 않는다.
 * **끄는 법이 그 한 줄이다**(rollback.md 가 이 경로를 적는다).
 *
 * `JdbcOpeningResultRepository` 를 **출하에서 처음으로 꽂는 자리**다 — 그 전까지 V4·V5 의 canonical
 * 슬롯에는 쓰기 경로가 없었다(7C⑤ checklist §0.1 실측).
 *
 * KONEPS 를 부르지 않는다 — 서비스 키 설정을 요구하지 않는다(추출 배선과 같은 성질).
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.opening-promote", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(OpeningPromotionProperties::class)
open class OpeningPromotionWiring {
    /**
     * 수집이 남긴 실행 상태를 **읽는다** — 승격은 표본을 확정하지도, 원장에 쓰지도 않는다. 디렉터리
     * 부재와 저장소 안 경로 거부는 **기동 시점**이다(조건이 깨지면 빈이 서지 않아 프로세스가 뜨지 않는다).
     */
    @Bean
    open fun openingPromotionRunState(properties: OpeningPromotionProperties): RunStateDirectory =
        RunStateDirectory(requireOutsideRepository(Path.of(properties.runStateDir)))

    @Bean
    open fun promoteOpeningResultsUseCase(
        dataSource: DataSource,
        properties: OpeningPromotionProperties,
        runState: RunStateDirectory,
        clock: Clock,
    ): PromoteOpeningResultsUseCase {
        val referenceDate = CollectionReferenceDate(properties.referenceDate)
        return PromoteOpeningResultsUseCase(
            rawAxes = JdbcOpeningRawAxisSource(dataSource),
            results = JdbcOpeningResultRepository(dataSource),
            attempts = runState.attempts,
            sampleList = runState.sampleList,
            policyFor = ::collectionPolicyAt,
            // 승격 대상 원문의 적재 시각 하한 — 보존 기간을 이 배선이 다시 적지 않는다(D-7C5P-3).
            //
            // **해소 기준일이 시계가 아니라 운영자 입력이다**(cr M-1). 그래서 보존 개정(1년)이 이 창을
            // 넓히는 것은 **조건부**다 — `referenceDate` 가 개정일 이후일 때만이고, 과거 수집일을 주면
            // 이 창은 90일로 선다(파기 러너는 같은 정책을 주입 시계의 KST 날짜로 해소한다 — D-8).
            // 같은 한 자리 정책이 두 시대로 해소될 수 있다는 뜻이고, 그 비대칭은 runbook 알려진 제한 22 다.
            retention = resolvedOrFail(RETENTION_POLICY.resolve(properties.referenceDate), "보존 정책"),
            referenceDate = referenceDate,
            clock = clock,
        )
    }

    @Bean
    open fun openingPromotionRunner(
        useCase: PromoteOpeningResultsUseCase,
        runState: RunStateDirectory,
        termination: CollectionTermination,
    ): OpeningPromotionRunner {
        val logger = LoggerFactory.getLogger(OpeningPromotionRunner::class.java)
        return OpeningPromotionRunner(useCase, runState, CollectionLog { logger.info(it) }, termination)
    }
}
