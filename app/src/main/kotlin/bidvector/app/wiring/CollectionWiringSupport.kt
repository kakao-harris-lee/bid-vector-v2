package bidvector.app.wiring

import bidvector.adapters.koneps.KONEPS_HTTP_POLICY
import bidvector.adapters.koneps.KonepsHttpPolicyData
import bidvector.adapters.koneps.ServiceKey
import bidvector.app.collection.KonepsCredentialProperties
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.COLLECTION_RANGE_POLICY
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.CollectionRangeOutcome
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import bidvector.workflow.strategy.Clock
import java.net.URI
import java.net.http.HttpClient
import java.time.LocalDate

/**
 * 두 수집 배선(공고 목록 갈래·개찰결과 갈래)이 함께 쓰는 조립 규칙 — 같은 형태를 두 벌 두면 한쪽만
 * 고치는 표류가 생긴다(v2-지침서 §5 중복 금지, CPD 가 실제로 잡았다).
 */
private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

/**
 * 서비스 키는 요청 URI 의 쿼리로 실려 나간다 — 평문 http 로 외부 호스트를 부르면 키가 그대로 노출된다.
 * https 이거나 로컬 mock(loopback)일 때만 받는다(설정 실수는 기동 실패, D-6F8-4).
 */
internal fun requireSafeKonepsBaseUri(raw: String): String {
    val uri = URI.create(raw.trimEnd('/'))
    val secure = uri.scheme.equals("https", ignoreCase = true)
    val loopback = uri.scheme.equals("http", ignoreCase = true) && uri.host in LOOPBACK_HOSTS
    require(secure || loopback) { "KONEPS base-url 은 https 이거나 loopback 호스트여야 한다" }
    return uri.toString()
}

/** 정책 해소 실패는 기동(또는 첫 조회) 실패다 — 값을 지어내지 않는다. */
internal fun <T> resolvedOrFail(
    resolution: Resolution<T>,
    label: String,
): T = (resolution as? Resolution.Resolved<T>)?.value ?: error("$label 이 해소되지 않았다: $resolution")

internal fun collectionPolicyAt(referenceDate: CollectionReferenceDate): KonepsCollectionPolicyData =
    resolvedOrFail(KONEPS_COLLECTION_POLICY.resolve(referenceDate.date), "KONEPS 수집 정책")

/**
 * 조회 범위 — 상한·미래 금지는 정책이 정하고 위반은 **기동 실패**다(조용히 자르지 않는다). 두 갈래가
 * 같은 정책을 쓰되 **무엇의 날짜인가는 다르다**: 공고 목록 갈래는 조회일, 개찰결과 갈래는 공고일이다.
 */
internal fun resolveCollectionRange(
    from: LocalDate,
    to: LocalDate,
    clock: Clock,
    label: String,
): CollectionRange {
    val today = LocalDate.ofInstant(clock.now(), OPENING_DATE_ZONE)
    val policy = resolvedOrFail(COLLECTION_RANGE_POLICY.resolve(today), "수집 범위 정책")
    return when (val outcome = CollectionRange.of(from, to, today, policy)) {
        is CollectionRangeOutcome.Valid -> outcome.range
        is CollectionRangeOutcome.Rejected -> error("$label 이 유효하지 않다: ${outcome.reason}")
    }
}

/**
 * KONEPS 호출 한 벌의 전송 자리 — 서비스 키·전송 정책·HTTP 클라이언트. **원문 키를 읽는 자리가 여기
 * 하나**이고(두 배선이 각자 읽던 것을 모았다) 감싼 뒤에는 [ServiceKey] 밖으로 다시 나오지 않는다.
 */
internal class KonepsTransport(
    val serviceKey: ServiceKey,
    val httpPolicy: KonepsHttpPolicyData,
    val httpClient: HttpClient,
)

internal fun konepsTransportFor(
    credential: KonepsCredentialProperties,
    on: LocalDate,
): KonepsTransport {
    val httpPolicy = resolvedOrFail(KONEPS_HTTP_POLICY.resolve(on), "KONEPS 전송 정책")
    return KonepsTransport(
        serviceKey = ServiceKey.of(credential.serviceKey),
        httpPolicy = httpPolicy,
        httpClient = HttpClient.newBuilder().connectTimeout(httpPolicy.requestTimeout).build(),
    )
}
