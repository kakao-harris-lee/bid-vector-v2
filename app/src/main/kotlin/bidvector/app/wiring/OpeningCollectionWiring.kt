package bidvector.app.wiring

import bidvector.adapters.koneps.KONEPS_HTTP_POLICY
import bidvector.adapters.koneps.KonepsSourceConfig
import bidvector.adapters.koneps.ServiceKey
import bidvector.adapters.koneps.konepsOpeningResultSourceByNoticeDate
import bidvector.adapters.persistence.JdbcCollectedAxisStore
import bidvector.adapters.persistence.JdbcCollectionRunStore
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.app.collection.KonepsEndpointProperties
import bidvector.app.collection.KonepsOpeningEndpointProperties
import bidvector.app.collection.KonepsOpeningOperationProperties
import bidvector.app.collection.OpeningCollectionProperties
import bidvector.app.collection.OpeningCollectionRunner
import bidvector.app.collection.requireOutsideRepository
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.CollectionCallBudget
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.OpeningResultSourcePort
import bidvector.procurement.dayStartOf
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.CollectOpeningResultsUseCase
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionRangeOutcome
import bidvector.workflow.collection.CollectionSourceName
import bidvector.workflow.collection.OPENING_COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.OpeningCollectionSource
import bidvector.workflow.collection.SampleSize
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
import java.nio.file.Path
import java.time.LocalDate
import javax.sql.DataSource

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
    /**
     * 공고일 범위다 — 개찰결과 목록을 공고일 축으로 걷는다(D-6G-11). 상한은 **이 갈래의 정책**이
     * 정한다(D-6G2e-1, A-1 승인) — 공고 목록 갈래의 31일이 아니다.
     */
    @Bean
    open fun openingCollectionRange(
        properties: OpeningCollectionProperties,
        clock: Clock,
    ): CollectionRange =
        resolveCollectionRange(
            properties.from,
            properties.to,
            clock,
            "공고일 범위",
            OPENING_COLLECTION_RANGE_POLICY,
        )

    /**
     * `@ConditionalOnMissingBean` 이다 — 배선 조건 test 가 DB 없이 기동 조건만 재도록 대체 빈을 먼저
     * 등록할 수 있게 한다. 출하에서 이 자리를 덮는 빈이 없다는 것은 **선언이 아니라 실측**이다
     * (실 DB 로 뜬 출하 조립에서 빈 타입을 잰다, D-6G-44).
     */
    @Bean
    @ConditionalOnMissingBean
    open fun collectedAxisStore(dataSource: DataSource): CollectedAxisStore = JdbcCollectedAxisStore(dataSource)

    /**
     * A-1 승인 상한 — 두 값 모두 설정이 준다. 원장은 **시도 원장에서 seed** 한다(D-6G-45): 승인된
     * 총 상한은 3~4일에 걸친 여러 실행을 덮으므로 매 기동 0 에서 시작하면 그 상한이 아무것도 막지
     * 못하고, 받은 페이지만 세면 재시도·5xx·429·타임아웃이 상한 밖에서 나간다. 하루의 경계는
     * **KST 자정**이다(UTC 자정이 아니다 — 그 사이 아홉 시간의 호출이 오늘치에서 빠진다).
     */
    @Bean
    open fun openingCallBudget(
        properties: OpeningCollectionProperties,
        // **잠금을 먼저 잡고 seed 한다**(vr L-5). 둘의 순서가 뒤집히면 창이 생긴다: 두 실행이 나란히
        // seed 한 뒤 하나가 끝나고 다른 하나가 잠금을 얻으면, 그 실행은 앞 실행의 호출을 보지 못한 낡은
        // 값에서 시작한다. 이제 순서는 **의존이 강제한다** — 디렉터리를 여는 것이 곧 잠그는 것이고,
        // seed 는 그 디렉터리의 원장에서만 나온다(D-6G-57).
        runState: RunStateDirectory,
        clock: Clock,
    ): CallBudgetLedger =
        seededBudget(runState, properties.callsPerDay, properties.callsTotal, clock)

    /**
     * 실행 상태 디렉터리(D-6G-39·45) — 확정 표본과 시도 원장이 여기 있다. 저장소 밖 강제도, 디렉터리
     * 부재 거부도 **기동 시점**이다(조건이 깨지면 빈이 서지 않아 프로세스가 뜨지 않는다).
     */
    @Bean
    open fun openingRunState(properties: OpeningCollectionProperties): RunStateDirectory =
        RunStateDirectory(requireOutsideRepository(Path.of(properties.runStateDir)))

    @Bean
    open fun openingSampler(properties: OpeningCollectionProperties): StratifiedSampler =
        StratifiedSampler(SamplingSeed(properties.samplingSeed), SampleSize(properties.sampleSize))

    @Bean
    open fun openingCollectionSources(
        properties: OpeningCollectionProperties,
        endpoint: KonepsEndpointProperties,
        opening: KonepsOpeningEndpointProperties,
        credential: KonepsCredentialProperties,
        range: CollectionRange,
        runState: RunStateDirectory,
        budget: CallBudgetLedger,
        clock: Clock,
    ): OpeningCollectionSources {
        require(properties.categories.isNotEmpty()) { "bidvector.opening-collection.categories 가 비어 있다" }
        require(properties.categories.toSet().size == properties.categories.size) {
            "bidvector.opening-collection.categories 에 같은 업종이 두 번 있다"
        }
        val noticeBase = requireSafeKonepsBaseUri(endpoint.baseUrl)
        val scsbidBase = requireSafeKonepsBaseUri(opening.scsbidBaseUrl)
        val transport = konepsTransportFor(credential, range.to, runState, budget, clock)
        val config =
            KonepsSourceConfig(
                gate = transport.gate,
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
        runState: RunStateDirectory,
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
            sampleList = runState.sampleList,
            attempts = runState.attempts,
            clock = clock,
        )
    }

    @Bean
    open fun openingCollectionRunner(
        useCase: CollectOpeningResultsUseCase,
        range: CollectionRange,
        sources: OpeningCollectionSources,
        runState: RunStateDirectory,
        termination: CollectionTermination,
    ): OpeningCollectionRunner {
        val logger = LoggerFactory.getLogger(OpeningCollectionRunner::class.java)
        return OpeningCollectionRunner(
            useCase,
            range,
            sources.all,
            runState.lock,
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
