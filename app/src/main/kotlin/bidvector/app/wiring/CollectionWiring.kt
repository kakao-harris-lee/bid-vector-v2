package bidvector.app.wiring

import bidvector.adapters.koneps.KONEPS_HTTP_POLICY
import bidvector.adapters.koneps.KonepsHttpPolicyData
import bidvector.adapters.koneps.KonepsOpenApiNoticeSource
import bidvector.adapters.koneps.ServiceKey
import bidvector.adapters.persistence.JdbcCollectionRunStore
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionProperties
import bidvector.app.collection.CollectionRunner
import bidvector.app.collection.CollectionTermination
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.app.collection.KonepsEndpointProperties
import bidvector.app.collection.requireOutsideRepository
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.CollectNoticesUseCase
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionRangeOutcome
import bidvector.workflow.collection.CollectionSource
import bidvector.workflow.collection.CollectionSourceName
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import bidvector.workflow.strategy.Clock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.net.URI
import java.nio.file.Path
import java.time.LocalDate
import javax.sql.DataSource

/** 조립된 업종 소스 목록 — `List` 빈은 Spring 의 컬렉션 주입과 섞이므로 한 겹 감싼다. */
class CollectionSources(
    val all: List<CollectionSource>,
)

/**
 * 일회성 수집 배선(D-6F8-3) — **`bidvector.collection.mode=once` 일 때만** 이 설정 전체가 올라온다.
 * 속성이 없으면(기본) 러너도, 서비스 키 바인딩도, 수집용 어댑터 생성도 일어나지 않는다 — 평가 endpoint 만 쓰는
 * 배포는 KONEPS 키 없이 그대로 뜬다. 켜졌을 때의 설정 오류(범위 상한·미래·미지 업종·키 부재)는 전부
 * **기동 실패**다(조용히 자르거나 기본값으로 메우지 않는다).
 *
 * 이 클래스는 조립만 한다: 설정 값의 형식을 검사하려고 [CollectionRange]·[CollectionSourceName] 을 만들 뿐
 * 수집 포트와 정규화 함수를 직접 부르지 않는다(use case 가 유일한 호출자다 — 구조 게이트). 서비스 키 원문은
 * [collectionSources] 한 곳에서 [ServiceKey] 로 감싼 뒤 다시 다루지 않는다.
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.collection", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(
    CollectionProperties::class,
    KonepsCredentialProperties::class,
    KonepsEndpointProperties::class,
)
open class CollectionWiring {
    /**
     * 이 갈래의 상한은 31일 그대로다(6F-8 D-6F8-3) — 호출 수가 창 길이에 비례한다. 개찰 갈래의 긴
     * 창을 쓰는 정책을 여기 꽂으면 그 한 줄이 호출 폭주 방지를 함께 연다(D-6G2e-1).
     */
    @Bean
    open fun collectionRange(
        properties: CollectionProperties,
        clock: Clock,
    ): CollectionRange =
        resolveCollectionRange(
            properties.from,
            properties.to,
            clock,
            "수집 범위",
            COLLECTION_RANGE_POLICY,
        )

    @Bean
    open fun collectionSources(
        properties: CollectionProperties,
        endpoint: KonepsEndpointProperties,
        credential: KonepsCredentialProperties,
        range: CollectionRange,
        runState: RunStateDirectory,
        budget: CallBudgetLedger,
        clock: Clock,
    ): CollectionSources {
        require(properties.categories.isNotEmpty()) { "bidvector.collection.categories 가 비어 있다" }
        require(properties.categories.toSet().size == properties.categories.size) {
            "bidvector.collection.categories 에 같은 업종이 두 번 있다"
        }
        val baseUri = requireSafeKonepsBaseUri(endpoint.baseUrl)
        val transport = konepsTransportFor(credential, range.to, runState, budget, clock)
        val sources =
            properties.categories.map { category ->
                val name = requireNotNull(CollectionSourceName.of(category)) { "업종 이름 형식이 유효하지 않다" }
                val operation = endpoint.operations[category] ?: error("오퍼레이션이 등재되지 않은 업종이다: ${name.value}")
                val operationUri = URI.create("$baseUri/${operation.path}")
                CollectionSource(name, sourceFor(transport, operationUri, operation.division))
            }
        return CollectionSources(sources)
    }

    /** 개찰 갈래와 **같은 실행 상태**를 쓴다(D-6G-47) — 두 갈래의 호출이 한 원장에서 합쳐진다. */
    @Bean
    open fun collectionRunState(properties: CollectionProperties): RunStateDirectory =
        RunStateDirectory(requireOutsideRepository(Path.of(properties.runStateDir)))

    @Bean
    open fun collectionCallBudget(
        properties: CollectionProperties,
        runState: RunStateDirectory,
        clock: Clock,
    ): CallBudgetLedger = seededBudget(runState, properties.callsPerDay, properties.callsTotal, clock)

    @Bean
    open fun collectNoticesUseCase(
        dataSource: DataSource,
        properties: CollectionProperties,
        range: CollectionRange,
        clock: Clock,
    ): CollectNoticesUseCase {
        val fieldContracts = collectionPolicyAt(CollectionReferenceDate(range.to)).fieldContracts
        return CollectNoticesUseCase(
            rawObservations = JdbcRawObservationStore(dataSource, fieldContracts, properties.releaseSha),
            notices = JdbcNoticeRepository(dataSource),
            runs = JdbcCollectionRunStore(dataSource),
            policyFor = ::collectionPolicyAt,
            clock = clock,
        )
    }

    @Bean
    open fun collectionRunner(
        useCase: CollectNoticesUseCase,
        range: CollectionRange,
        sources: CollectionSources,
        runState: RunStateDirectory,
        termination: CollectionTermination,
    ): CollectionRunner {
        val logger = LoggerFactory.getLogger(CollectionRunner::class.java)
        return CollectionRunner(
            useCase,
            range,
            sources.all,
            runState.lock,
            CollectionLog { logger.info(it) },
            termination,
        )
    }

    private fun sourceFor(
        transport: KonepsTransport,
        baseUri: URI,
        division: BusinessDivision,
    ): KonepsOpenApiNoticeSource =
        KonepsOpenApiNoticeSource(
            gate = transport.gate,
            baseUri = baseUri,
            serviceKey = transport.serviceKey,
            httpPolicy = transport.httpPolicy,
            collectionPolicyProvider = ::collectionPolicyAt,
            businessDivision = division,
        )
}
