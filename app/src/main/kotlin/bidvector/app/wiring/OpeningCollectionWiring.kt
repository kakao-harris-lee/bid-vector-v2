package bidvector.app.wiring

import bidvector.adapters.koneps.KONEPS_HTTP_POLICY
import bidvector.adapters.koneps.KonepsSourceConfig
import bidvector.adapters.koneps.ServiceKey
import bidvector.adapters.koneps.konepsOpeningResultSourceByNoticeDate
import bidvector.adapters.persistence.JdbcCollectedAxisStore
import bidvector.adapters.persistence.JdbcCollectionCallLedgerStore
import bidvector.adapters.persistence.JdbcCollectionRunLease
import bidvector.adapters.persistence.JdbcCollectionRunStore
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.adapters.snapshot.FileSampleListLedger
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.app.collection.KonepsEndpointProperties
import bidvector.app.collection.KonepsOpeningEndpointProperties
import bidvector.app.collection.KonepsOpeningOperationProperties
import bidvector.app.collection.OpeningCollectionProperties
import bidvector.app.collection.OpeningCollectionRunner
import bidvector.app.collection.requireOutsideRepository
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.CollectionCallLedgerStore
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.OpeningResultSourcePort
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.CallBudgetLedger
import bidvector.workflow.collection.CollectOpeningResultsUseCase
import bidvector.workflow.collection.CollectionCallBudget
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionRangeOutcome
import bidvector.workflow.collection.CollectionSourceName
import bidvector.workflow.collection.OpeningCollectionSource
import bidvector.workflow.collection.SamplingSeed
import bidvector.workflow.collection.StratifiedSampler
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import bidvector.workflow.strategy.Clock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.LocalDate
import javax.sql.DataSource

internal const val OPENING_COLLECTION_LOCK_KEY = 6_020_260_927L

/** 조립된 개찰 축 소스 목록 — `List` 빈은 Spring 컬렉션 주입과 섞이므로 한 겹 감싼다. */
class OpeningCollectionSources(
    val all: List<OpeningCollectionSource>,
)

/**
 * 개찰결과 수집 배선(M6/6G D-6G-1·11·19) — **`bidvector.opening-collection.mode=once` 일 때만** 이 설정
 * 전체가 올라온다. 속성이 없으면(기본) 러너도, 서비스 키 바인딩도, 이 갈래의 어댑터 생성도 일어나지
 * 않는다. 켜졌을 때의 설정 오류(범위·미지 업종·상한 값·키 부재)는 전부 **기동 실패**다.
 *
 * 이 클래스는 조립만 한다 — 수집 포트를 직접 부르지 않는다(use case 가 유일한 호출자다, 구조 게이트).
 * 서비스 키 원문은 [openingCollectionSources] 한 곳에서 [ServiceKey] 로 감싼 뒤 다시 다루지 않는다.
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.opening-collection", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(
    OpeningCollectionProperties::class,
    KonepsCredentialProperties::class,
    KonepsEndpointProperties::class,
    KonepsOpeningEndpointProperties::class,
)
open class OpeningCollectionWiring {
    /** 공고일 범위다 — 개찰결과 목록을 공고일 축으로 걷는다(D-6G-11). */
    @Bean
    open fun openingCollectionRange(
        properties: OpeningCollectionProperties,
        clock: Clock,
    ): CollectionRange = resolveCollectionRange(properties.from, properties.to, clock, "공고일 범위")

    /**
     * 두 저장소는 `@ConditionalOnMissingBean` 이다 — 배선 조건 test 가 DB 없이 기동 조건만 재도록
     * 대체 빈을 먼저 등록할 수 있게 한다. 출하에서는 이 자리를 덮는 빈이 없어 JDBC 구현이 선다.
     */
    @Bean
    @ConditionalOnMissingBean
    open fun collectionCallLedgerStore(dataSource: DataSource): CollectionCallLedgerStore =
        JdbcCollectionCallLedgerStore(dataSource)

    @Bean
    @ConditionalOnMissingBean
    open fun collectedAxisStore(dataSource: DataSource): CollectedAxisStore = JdbcCollectedAxisStore(dataSource)

    /**
     * A-1 승인 상한 — 두 값 모두 설정이 준다. 원장은 **영속에서 seed** 한다(D-6G-29 ①): 승인된 총
     * 상한은 3~4일에 걸친 여러 실행을 덮으므로, 매 기동마다 0 에서 시작하면 그 상한이 실제로는
     * 아무것도 막지 못한다.
     */
    @Bean
    open fun openingCallBudget(
        properties: OpeningCollectionProperties,
        ledger: CollectionCallLedgerStore,
        clock: Clock,
    ): CallBudgetLedger {
        val today = LocalDate.ofInstant(clock.now(), OPENING_DATE_ZONE)
        val spent = ledger.spentSince(properties.budgetSince, today.atStartOfDay(OPENING_DATE_ZONE).toInstant())
        return CallBudgetLedger(
            CollectionCallBudget(properties.callsPerDay, properties.callsTotal),
            today,
            spent,
        )
    }

    /**
     * 표본 목록 파일(D-6G-39) — 첫 실행이 확정하고 이후 실행은 읽기만 한다. 저장소 밖 강제는 기동
     * 시점이다(경로가 안이면 빈 생성이 실패해 프로세스가 서지 않는다).
     */
    @Bean
    open fun openingSampleListLedger(properties: OpeningCollectionProperties): FileSampleListLedger =
        FileSampleListLedger(requireOutsideRepository(Path.of(properties.sampleListFile)))

    @Bean
    open fun openingSampler(properties: OpeningCollectionProperties): StratifiedSampler =
        StratifiedSampler(SamplingSeed(properties.samplingSeed), properties.targetPerStratum)

    @Bean
    open fun openingCollectionSources(
        properties: OpeningCollectionProperties,
        endpoint: KonepsEndpointProperties,
        opening: KonepsOpeningEndpointProperties,
        credential: KonepsCredentialProperties,
        range: CollectionRange,
    ): OpeningCollectionSources {
        require(properties.categories.isNotEmpty()) { "bidvector.opening-collection.categories 가 비어 있다" }
        require(properties.categories.toSet().size == properties.categories.size) {
            "bidvector.opening-collection.categories 에 같은 업종이 두 번 있다"
        }
        val noticeBase = requireSafeKonepsBaseUri(endpoint.baseUrl)
        val scsbidBase = requireSafeKonepsBaseUri(opening.scsbidBaseUrl)
        val transport = konepsTransportFor(credential, range.to)
        val config =
            KonepsSourceConfig(
                httpClient = transport.httpClient,
                serviceKey = transport.serviceKey,
                httpPolicy = transport.httpPolicy,
                collectionPolicyProvider = ::collectionPolicyAt,
            )
        val sources =
            properties.categories.map { category ->
                val name = requireNotNull(CollectionSourceName.of(category)) { "업종 이름 형식이 유효하지 않다" }
                val operation =
                    opening.operations[category] ?: error("개찰 축 오퍼레이션이 등재되지 않은 업종이다: ${name.value}")
                OpeningCollectionSource(
                    name,
                    operation.division,
                    sourceFor(operation, noticeBase, scsbidBase, opening, config),
                )
            }
        return OpeningCollectionSources(sources)
    }

    @Bean
    open fun collectOpeningResultsUseCase(
        dataSource: DataSource,
        properties: OpeningCollectionProperties,
        range: CollectionRange,
        sampler: StratifiedSampler,
        collectedAxes: CollectedAxisStore,
        sampleList: FileSampleListLedger,
        clock: Clock,
    ): CollectOpeningResultsUseCase {
        val policy = collectionPolicyAt(CollectionReferenceDate(range.to))
        return CollectOpeningResultsUseCase(
            rawObservations = JdbcRawObservationStore(dataSource, policy.fieldContracts, properties.releaseSha),
            runs = JdbcCollectionRunStore(dataSource),
            sampler = sampler,
            policyFor = ::collectionPolicyAt,
            gates = policy.detailFetchGates,
            collectedAxes = collectedAxes,
            sampleList = sampleList,
            clock = clock,
        )
    }

    /**
     * 실행 잠금의 키 — 이 갈래 하나를 가리키는 상수다(다른 수집 갈래와 겹치지 않는 임의의 값).
     * advisory lock 은 키 공간이 전역이므로 값 자체에 뜻이 없어도 되지만 **고정**이어야 한다.
     */
    @Bean
    open fun openingCollectionRunLease(dataSource: DataSource): JdbcCollectionRunLease =
        JdbcCollectionRunLease(dataSource, OPENING_COLLECTION_LOCK_KEY)

    @Bean
    open fun openingCollectionRunner(
        useCase: CollectOpeningResultsUseCase,
        range: CollectionRange,
        sources: OpeningCollectionSources,
        budget: CallBudgetLedger,
        lease: JdbcCollectionRunLease,
        termination: CollectionTermination,
    ): OpeningCollectionRunner {
        val logger = LoggerFactory.getLogger(OpeningCollectionRunner::class.java)
        return OpeningCollectionRunner(
            useCase,
            range,
            sources.all,
            budget,
            lease,
            CollectionLog { logger.info(it) },
            termination,
        )
    }

    /**
     * 한 업무의 포트 하나 — baseUri 가 **넷**이다(목록·예비가격 상세는 낙찰정보서비스, A값·기초금액은
     * 입찰공고정보서비스). 개찰완료는 업무 접미가 없는 단일 오퍼레이션이라 경로가 하나다.
     */
    private fun sourceFor(
        operation: KonepsOpeningOperationProperties,
        noticeBase: String,
        scsbidBase: String,
        opening: KonepsOpeningEndpointProperties,
        config: KonepsSourceConfig,
    ): OpeningResultSourcePort =
        konepsOpeningResultSourceByNoticeDate(
            listBaseUri = URI.create("$scsbidBase/${operation.openingResultListPath}"),
            reserveDetailBaseUri = URI.create("$scsbidBase/${operation.reservePriceDetailPath}"),
            openingCompleteBaseUri = URI.create("$scsbidBase/${opening.openingCompletePath}"),
            bidPriceFormulaABaseUri = URI.create("$noticeBase/${opening.bidPriceFormulaAPath}"),
            baseAmountBaseUri = URI.create("$noticeBase/${operation.baseAmountPath}"),
            config = config,
        )
}
