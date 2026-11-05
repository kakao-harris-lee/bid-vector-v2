package bidvector.app.wiring

import bidvector.adapters.koneps.KonepsSourceConfig
import bidvector.adapters.koneps.konepsAwardListSource
import bidvector.adapters.persistence.JdbcCollectionRunStore
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.app.collection.AwardCollectionProperties
import bidvector.app.collection.AwardCollectionRunner
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.collection.KonepsAwardEndpointProperties
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.app.collection.requireOutsideRepository
import bidvector.procurement.CallBudgetLedger
import bidvector.procurement.CollectionReferenceDate
import bidvector.workflow.collection.AWARD_COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.AwardCollectionSource
import bidvector.workflow.collection.CollectAwardListUseCase
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionSourceName
import bidvector.workflow.strategy.Clock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.net.URI
import java.nio.file.Path
import javax.sql.DataSource

/** 조립된 낙찰 목록 소스 — `List` 빈은 Spring 컬렉션 주입과 섞이므로 한 겹 감싼다(형제 갈래 관례). */
class AwardCollectionSources(
    val all: List<AwardCollectionSource>,
)

/**
 * 낙찰 목록 수집 배선(M9/award-ingest D-1·D-2) — **`bidvector.award-collection.mode=once` 일 때만** 이
 * 설정 전체가 올라온다. 속성이 없으면(기본) 러너도, 서비스 키 바인딩도, 이 갈래의 어댑터 생성도
 * 일어나지 않는다. **끄는 법이 그 한 줄이다**(rollback.md 가 이 경로를 적는다).
 *
 * 켜졌을 때의 설정 오류(범위·미지 업종·상한 값·키 부재·실행 상태 디렉터리 부재)는 전부 **기동 실패**다.
 *
 * 이 클래스는 조립만 한다 — 수집 포트를 직접 부르지 않는다(use case 가 유일한 호출자다, 구조 게이트).
 * 개찰결과 갈래의 설정 타입을 하나도 건드리지 않는다: 두 갈래는 **다른 실행 상태 디렉터리**와 다른
 * 상한 회계를 쓰고, 한 프로세스에 함께 켤 수 없다(일회 러너는 하나다).
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.award-collection", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(
    AwardCollectionProperties::class,
    KonepsCredentialProperties::class,
    KonepsAwardEndpointProperties::class,
)
open class AwardCollectionWiring {
    /**
     * **개찰일** 범위다 — 낙찰 목록을 개찰일시 축으로 걷는다. 상한은 **이 갈래의 정책**이 정한다
     * (형제 갈래의 120일도, 공고 목록 갈래의 31일도 아니다 — 호출 수가 창 길이에 비례하는 갈래의
     * 조각 상한이다).
     */
    @Bean
    open fun awardCollectionRange(
        properties: AwardCollectionProperties,
        clock: Clock,
    ): CollectionRange =
        resolveCollectionRange(
            properties.from,
            properties.to,
            clock,
            "개찰일 범위",
            AWARD_COLLECTION_RANGE_POLICY,
        )

    /**
     * 실행 상태 디렉터리 — 이 갈래의 시도 원장이 여기 있다. 저장소 밖 강제도, 디렉터리 부재 거부도
     * **기동 시점**이다(조건이 깨지면 빈이 서지 않아 프로세스가 뜨지 않는다).
     */
    @Bean
    open fun awardRunState(properties: AwardCollectionProperties): RunStateDirectory =
        RunStateDirectory(requireOutsideRepository(Path.of(properties.runStateDir)))

    /**
     * 승인 상한 원장 — **시도 원장에서 seed** 한다(형제 갈래와 같은 자리·같은 이유). 잠금을 먼저 잡고
     * seed 하는 순서는 의존이 강제한다: 디렉터리를 여는 것이 곧 잠그는 것이고 seed 는 그 디렉터리의
     * 원장에서만 나온다.
     */
    @Bean
    open fun awardCallBudget(
        properties: AwardCollectionProperties,
        awardRunState: RunStateDirectory,
        clock: Clock,
    ): CallBudgetLedger = seededBudget(awardRunState, properties.callsPerDay, properties.callsTotal, clock)

    @Bean
    open fun awardCollectionSources(
        properties: AwardCollectionProperties,
        award: KonepsAwardEndpointProperties,
        credential: KonepsCredentialProperties,
        awardCollectionRange: CollectionRange,
        awardRunState: RunStateDirectory,
        awardCallBudget: CallBudgetLedger,
        clock: Clock,
    ): AwardCollectionSources {
        require(properties.categories.isNotEmpty()) { "bidvector.award-collection.categories 가 비어 있다" }
        require(properties.categories.toSet().size == properties.categories.size) {
            "bidvector.award-collection.categories 에 같은 업종이 두 번 있다"
        }
        val scsbidBase = requireSafeKonepsBaseUri(award.scsbidBaseUrl)
        val transport = konepsTransportFor(credential, awardCollectionRange.to, awardRunState, awardCallBudget, clock)
        val config =
            KonepsSourceConfig(
                gate = transport.gate,
                serviceKey = transport.serviceKey,
                httpPolicy = transport.httpPolicy,
                collectionPolicyProvider = ::collectionPolicyAt,
                numOfRowsPerPage = award.rowsPerPage,
            )
        val sources =
            properties.categories.map { category ->
                val name = requireNotNull(CollectionSourceName.of(category)) { "업종 이름 형식이 유효하지 않다" }
                val operation =
                    award.operations[category] ?: error("낙찰 목록 오퍼레이션이 등재되지 않은 업종이다: ${name.value}")
                AwardCollectionSource(name, konepsAwardListSource(URI.create("$scsbidBase/$operation"), config))
            }
        return AwardCollectionSources(sources)
    }

    /**
     * 원문만 적재하는 use case — canonical 저장소를 받지 않는다(이 갈래에는 승격 경로가 **없다**).
     * 필드 계약 레지스트리는 정책에서 꺼내 원문 저장 어댑터에 그대로 넘긴다(값을 읽지 않는다).
     */
    @Bean
    open fun collectAwardListUseCase(
        dataSource: DataSource,
        properties: AwardCollectionProperties,
        awardCollectionRange: CollectionRange,
        clock: Clock,
    ): CollectAwardListUseCase =
        CollectAwardListUseCase(
            rawObservations =
                JdbcRawObservationStore(
                    dataSource,
                    collectionPolicyAt(CollectionReferenceDate(awardCollectionRange.to)).fieldContracts,
                    properties.releaseSha,
                ),
            runs = JdbcCollectionRunStore(dataSource),
            clock = clock,
        )

    @Bean
    open fun awardCollectionRunner(
        useCase: CollectAwardListUseCase,
        awardCollectionRange: CollectionRange,
        sources: AwardCollectionSources,
        awardRunState: RunStateDirectory,
        termination: CollectionTermination,
    ): AwardCollectionRunner {
        val logger = LoggerFactory.getLogger(AwardCollectionRunner::class.java)
        return AwardCollectionRunner(
            useCase,
            awardCollectionRange,
            sources.all,
            awardRunState,
            CollectionLog { logger.info(it) },
            termination,
        )
    }
}
