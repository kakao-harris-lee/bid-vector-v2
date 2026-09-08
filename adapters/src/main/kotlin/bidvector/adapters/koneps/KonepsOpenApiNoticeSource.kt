package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeSourcePort
import bidvector.procurement.PageCursor
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import java.net.URI
import java.net.http.HttpClient
import java.time.Clock
import java.time.format.DateTimeFormatter

private const val DEFAULT_ROWS_PER_PAGE = 100
private val QUERY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
private const val LAST_MINUTE_OF_DAY_HOUR = 23
private const val LAST_MINUTE_OF_DAY_MINUTE = 59

private fun buildListUri(
    base: URI,
    serviceKey: ServiceKey,
    referenceDate: CollectionReferenceDate,
    pageNo: Int,
    numOfRows: Int,
): URI {
    val day = referenceDate.date
    val begin = day.atStartOfDay().format(QUERY_DATE_FORMAT)
    val end = day.atTime(LAST_MINUTE_OF_DAY_HOUR, LAST_MINUTE_OF_DAY_MINUTE).format(QUERY_DATE_FORMAT)
    val query =
        listOf(
            "serviceKey=${serviceKey.urlEncoded}",
            "pageNo=$pageNo",
            "numOfRows=$numOfRows",
            "type=json",
            "inqryDiv=1",
            "inqryBgnDt=$begin",
            "inqryEndDt=$end",
        ).joinToString("&")
    return URI.create("$base?$query")
}

/**
 * KONEPS 공고 목록 port 구현(①, D-3B-6 — **공고 축만**). 동기 facade — 내부는 JDK
 * `HttpClient` + Resilience4j `Retry`·`RateLimiter` **한 계층**(②, ADR 0005 D-11). 생성자
 * 주입(DI) — 전역 상태·service locator 없음(v2-지침서 §5).
 *
 * **표적조회(`inqryDiv=2`)·`OpeningResultSourcePort`·license-limit 서브콜은 이 클래스 밖이다**
 * (D-3B-6 범위 분할, scope.md 착수 시 계약 정정 ③) — 이 클래스는 조회일 기준 날짜 조회
 * (`inqryDiv=1`) 목록만 다룬다.
 *
 * **업종별(물품/용역/공사/외자) 오퍼레이션 선택은 이 slice 밖이다** — `baseUri`가 오퍼레이션
 * 경로까지 포함해 호출부(M4 workflow)가 업종별 인스턴스를 따로 구성한다. 어느 업종의 실제
 * 운영 경로가 맞는지는 실제 KONEPS 호출이 out_of_scope 라 이 slice 가 검증하지 않는다(알려진
 * 제한, checklist.md).
 */
class KonepsOpenApiNoticeSource(
    private val httpClient: HttpClient,
    private val baseUri: URI,
    private val serviceKey: ServiceKey,
    private val httpPolicy: KonepsHttpPolicyData,
    private val collectionPolicyProvider: (referenceDate: CollectionReferenceDate) -> KonepsCollectionPolicyData,
    private val clock: Clock = Clock.systemUTC(),
    private val numOfRowsPerPage: Int = DEFAULT_ROWS_PER_PAGE,
) : NoticeSourcePort {
    private val retry: Retry = buildKonepsRetry("koneps-notice-list", httpPolicy)
    private val rateLimiter: RateLimiter = buildKonepsRateLimiter("koneps-notice-list", httpPolicy)

    override fun fetchNotices(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> {
        val policy = collectionPolicyProvider(referenceDate)
        val uriBuilder =
            KonepsPageUriBuilder { pageNo ->
                buildListUri(baseUri, serviceKey, referenceDate, pageNo, numOfRowsPerPage)
            }
        // F-4(verifier r1, 3B-2) — itemMapper 기본값이 없어졌다. 이 port 는 항상 공고 축
        // 원문 보존 mapper 를 쓴다(동작 불변, 이전 판의 암묵 기본값과 같은 값을 명시할 뿐).
        return walkKonepsNoticePages(
            httpClient,
            retry,
            rateLimiter,
            uriBuilder,
            httpPolicy,
            policy,
            clock,
            cursor,
            defaultKonepsItemMapper,
        )
    }
}
