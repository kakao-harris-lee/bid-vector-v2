package bidvector.app.wiring

import bidvector.adapters.koneps.KONEPS_HTTP_POLICY
import bidvector.adapters.koneps.KonepsHttpPolicyData
import bidvector.adapters.koneps.KonepsOpenApiNoticeSource
import bidvector.adapters.koneps.ServiceKey
import bidvector.adapters.persistence.JdbcCollectionRunStore
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcRawObservationStore
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionProperties
import bidvector.app.collection.CollectionRunner
import bidvector.app.collection.CollectionTermination
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.app.collection.KonepsEndpointProperties
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
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.net.http.HttpClient
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.system.exitProcess

/** 조립된 업종 소스 목록 — `List` 빈은 Spring 의 컬렉션 주입과 섞이므로 한 겹 감싼다. */
class CollectionSources(
    val all: List<CollectionSource>,
)

/**
 * 일회성 수집 배선(D-6F8-3, M6/6F-8) — **`bidvector.collection.mode=once` 일 때만** 이 설정 전체가 올라온다.
 * 속성이 없으면(기본) 러너도, 서비스 키 바인딩도, 수집용 어댑터 생성도 일어나지 않는다 — 평가 endpoint 만 쓰는
 * 배포는 KONEPS 키 없이 그대로 뜬다. 켜졌을 때의 설정 오류(범위 상한·미래·미지 업종·키 부재)는 전부
 * **기동 실패**다(조용히 자르거나 기본값으로 메우지 않는다).
 *
 * 이 클래스는 조립만 한다: 설정 값의 형식을 검사하려고 [CollectionRange]·[CollectionSourceName] 을 만들 뿐
 * 수집 포트와 정규화 함수를 직접 부르지 않는다(use case 가 유일한 호출자다 — 구조 게이트). 서비스 키 원문은
 * [collectionSources] 한 곳에서 [ServiceKey] 로 감싼 뒤 다시 다루지 않는다.
 */
@Configuration
@ConditionalOnProperty(prefix = "bidvector.collection", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(
    CollectionProperties::class,
    KonepsCredentialProperties::class,
    KonepsEndpointProperties::class,
)
open class CollectionWiring {
    @Bean
    open fun collectionRange(
        properties: CollectionProperties,
        clock: Clock,
    ): CollectionRange {
        val today = LocalDate.ofInstant(clock.now(), OPENING_DATE_ZONE)
        val policy = resolved(COLLECTION_RANGE_POLICY.resolve(today), "수집 범위 정책")
        return when (val outcome = CollectionRange.of(properties.from, properties.to, today, policy)) {
            is CollectionRangeOutcome.Valid -> outcome.range
            is CollectionRangeOutcome.Rejected -> error("수집 범위가 유효하지 않다: ${outcome.reason}")
        }
    }

    @Bean
    open fun collectionSources(
        properties: CollectionProperties,
        endpoint: KonepsEndpointProperties,
        credential: KonepsCredentialProperties,
        range: CollectionRange,
    ): CollectionSources {
        require(properties.categories.isNotEmpty()) { "bidvector.collection.categories 가 비어 있다" }
        require(properties.categories.toSet().size == properties.categories.size) {
            "bidvector.collection.categories 에 같은 업종이 두 번 있다"
        }
        val baseUri = endpointBaseUri(endpoint)
        val serviceKey = ServiceKey.of(credential.serviceKey)
        val httpPolicy = resolved(KONEPS_HTTP_POLICY.resolve(range.to), "KONEPS 전송 정책")
        val httpClient = HttpClient.newBuilder().connectTimeout(httpPolicy.requestTimeout).build()
        val sources =
            properties.categories.map { category ->
                val name = requireNotNull(CollectionSourceName.of(category)) { "업종 이름 형식이 유효하지 않다" }
                val operation = endpoint.operations[category] ?: error("오퍼레이션이 등재되지 않은 업종이다: ${name.value}")
                val operationUri = URI.create("$baseUri/$operation")
                CollectionSource(name, sourceFor(httpClient, operationUri, serviceKey, httpPolicy))
            }
        return CollectionSources(sources)
    }

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
    open fun collectionTermination(context: ConfigurableApplicationContext): CollectionTermination =
        CollectionTermination { exitCode ->
            exitProcess(SpringApplication.exit(context, ExitCodeGenerator { exitCode }))
        }

    @Bean
    open fun collectionRunner(
        useCase: CollectNoticesUseCase,
        range: CollectionRange,
        sources: CollectionSources,
        termination: CollectionTermination,
    ): CollectionRunner {
        val logger = LoggerFactory.getLogger(CollectionRunner::class.java)
        return CollectionRunner(useCase, range, sources.all, CollectionLog { logger.info(it) }, termination)
    }

    private fun sourceFor(
        httpClient: HttpClient,
        baseUri: URI,
        serviceKey: ServiceKey,
        httpPolicy: KonepsHttpPolicyData,
    ): KonepsOpenApiNoticeSource =
        KonepsOpenApiNoticeSource(
            httpClient = httpClient,
            baseUri = baseUri,
            serviceKey = serviceKey,
            httpPolicy = httpPolicy,
            collectionPolicyProvider = ::collectionPolicyAt,
        )
}

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

/**
 * 서비스 키는 요청 URI 의 쿼리로 실려 나간다 — 평문 http 로 외부 호스트를 부르면 키가 그대로 노출된다. https 이거나
 * 로컬 mock 서버(loopback)일 때만 받는다(설정 실수는 기동 실패, D-6F8-4).
 */
private fun endpointBaseUri(endpoint: KonepsEndpointProperties): String {
    val uri = URI.create(endpoint.baseUrl.trimEnd('/'))
    val secure = uri.scheme.equals("https", ignoreCase = true)
    val loopback = uri.scheme.equals("http", ignoreCase = true) && uri.host in LOOPBACK_HOSTS
    require(secure || loopback) { "bidvector.koneps.base-url 은 https 이거나 loopback 호스트여야 한다" }
    return uri.toString()
}

/** 정책 해소 실패는 기동(또는 첫 조회) 실패다 — 값을 지어내지 않는다(`PersistenceWiring` 의 정책 해소와 같은 형태). */
private fun <T> resolved(
    resolution: Resolution<T>,
    label: String,
): T = (resolution as? Resolution.Resolved<T>)?.value ?: error("$label 이 해소되지 않았다: $resolution")

private fun collectionPolicyAt(referenceDate: CollectionReferenceDate): KonepsCollectionPolicyData =
    resolved(KONEPS_COLLECTION_POLICY.resolve(referenceDate.date), "KONEPS 수집 정책")
