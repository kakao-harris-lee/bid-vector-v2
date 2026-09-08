package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeId
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import java.net.URI
import java.net.http.HttpClient
import java.time.Clock
import java.time.LocalDate

private const val DEFAULT_ROWS_PER_PAGE = 100

/**
 * 3B-2 가 더하는 koneps port 구현([KonepsOpeningResultSource]·[KonepsLicenseLimitDocumentSource])
 * 이 공유하는 생성자 형태(v2-지침서 §5 중복 금지, CPD) — HttpClient·serviceKey·httpPolicy·정책
 * provider·clock·페이지 크기는 어느 오퍼레이션을 부르든 같은 배선이다. `baseUri`는 오퍼레이션
 * 마다 달라 이 값 밖에 남긴다(한 port 구현이 서로 다른 baseUri 를 둘 이상 쓸 수 있다 —
 * [KonepsOpeningResultSource]의 목록 축·예비가격 상세 축). 3B `KonepsOpenApiNoticeSource`는
 * 이 타입보다 먼저 있던 기존 코드라 재사용하지 않는다 — 그 파일을 고치면 3B 기존 test 를
 * 한 줄도 고치지 않는다는 제약(scope.md in_scope 주석)에 걸린다.
 */
data class KonepsSourceConfig(
    val httpClient: HttpClient,
    val serviceKey: ServiceKey,
    val httpPolicy: KonepsHttpPolicyData,
    val collectionPolicyProvider: (referenceDate: CollectionReferenceDate) -> KonepsCollectionPolicyData,
    val clock: Clock = Clock.systemUTC(),
    val numOfRowsPerPage: Int = DEFAULT_ROWS_PER_PAGE,
)

/**
 * `noticeId` 단건 조회 한 벌 — 예비가격 상세([KonepsOpeningResultSource.fetchReservePrices])와
 * license-limit([KonepsLicenseLimitDocumentSource.fetchQualificationText])가 공유하는 배선
 * (CPD 회피, v2-지침서 §5 중복 금지) — 오퍼레이션·baseUri·항목 매핑 전략만 호출부가 다르게
 * 준다. 단건 조회에는 자연스러운 조회 기준일이 없어 `clock` 기준 오늘 날짜로 정책을 해석한다
 * (`KONEPS_COLLECTION_POLICY`가 아직 단일 항목뿐이라 어느 날짜든 같은 값이 나온다 — 판단이
 * 갈린 지점, 날짜 의존 정책이 생기면 재검토 대상).
 */
internal fun fetchSingleKonepsNotice(
    config: KonepsSourceConfig,
    retry: Retry,
    rateLimiter: RateLimiter,
    baseUri: URI,
    operation: KonepsOperationDescriptor,
    noticeId: NoticeId,
    itemMapper: KonepsItemMapper,
): SourceBatch<RawNoticeObservation> {
    val referenceDate = CollectionReferenceDate(LocalDate.now(config.clock))
    val policy = config.collectionPolicyProvider(referenceDate)
    val uriBuilder =
        KonepsPageUriBuilder { pageNo ->
            buildKonepsOperationUri(
                base = baseUri,
                serviceKey = config.serviceKey,
                operation = operation,
                pageNo = pageNo,
                numOfRows = config.numOfRowsPerPage,
                noticeId = noticeId,
            )
        }
    return walkKonepsNoticePages(
        config.httpClient,
        retry,
        rateLimiter,
        uriBuilder,
        config.httpPolicy,
        policy,
        config.clock,
        cursor = null,
        itemMapper,
    )
}
