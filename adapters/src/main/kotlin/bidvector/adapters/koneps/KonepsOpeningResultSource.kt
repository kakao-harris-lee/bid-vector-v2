package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.OpeningResultSourcePort
import bidvector.procurement.PageCursor
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import java.net.URI
import java.time.format.DateTimeFormatter

private val QUERY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
private const val LAST_MINUTE_OF_DAY_HOUR = 23
private const val LAST_MINUTE_OF_DAY_MINUTE = 59

/** 조회일 하루 전체를 감싸는 (시작, 끝) 창 — 3B `KonepsOpenApiNoticeSource`의 창과 같은 규약. */
private fun openingDayWindow(referenceDate: CollectionReferenceDate): Pair<String, String> {
    val day = referenceDate.date
    val begin = day.atStartOfDay().format(QUERY_DATE_FORMAT)
    val end = day.atTime(LAST_MINUTE_OF_DAY_HOUR, LAST_MINUTE_OF_DAY_MINUTE).format(QUERY_DATE_FORMAT)
    return begin to end
}

/**
 * `OpeningResultSourcePort` 구현(①②⑥b, D-3B2-1 (a)) — **인스턴스당 오퍼레이션 하나**다. 같은
 * 클래스를 낙찰 목록(`listOperation=AWARD_LIST`, `listSourceEndpoint=OPENING_AWARD_LIST`)과
 * 개찰결과 목록(`listOperation=OPENING_RESULT_LIST`, `listSourceEndpoint=OPENING_RESULT_LIST`)
 * 두 인스턴스로 세워 4B 가 배선한다(D-3B2-2 (c) — 둘은 대안이 아니라 다른 fact 다).
 * `reserveDetailBaseUri`는 별도 오퍼레이션(예비가격 상세, `getOpengResultListInfo…
 * PreparPcDetail`)의 엔드포인트다 — `fetchOpeningResults`(목록 걷기)와 `fetchReservePrices`
 * (단건 조회)는 서로 다른 KONEPS 오퍼레이션이라 baseUri 가 둘이다.
 *
 * 개찰 축 항목은 [mapMaskedOpeningItem]으로만 관측이 된다(allow-list + `opengCorpInfo` masking,
 * P-10 (a)) — 3B `walkKonepsNoticePages`의 HTTP·Resilience4j·envelope·pagination·회계는 그대로
 * 재사용하고 항목 매핑만 다른 전략을 꽂는다.
 *
 * `openingCompleteBaseUri`(M3/3F) — 개찰완료(`getOpengResultListInfoOpengCompt`) 전용 엔드포인트.
 * [fetchOpeningCompleteResults]가 이 값을 쓴다 — 예비가격 상세와도 다른 오퍼레이션이라 baseUri
 * 가 셋이다.
 */
class KonepsOpeningResultSource(
    private val listBaseUri: URI,
    private val listOperation: KonepsOperationDescriptor,
    private val listSourceEndpoint: SourceEndpoint,
    private val reserveDetailBaseUri: URI,
    private val openingCompleteBaseUri: URI,
    private val config: KonepsSourceConfig,
) : OpeningResultSourcePort {
    private val retryName = "koneps-opening-${listSourceEndpoint.name.lowercase()}"
    private val retry: Retry = buildKonepsRetry(retryName, config.httpPolicy)
    private val rateLimiter: RateLimiter = buildKonepsRateLimiter(retryName, config.httpPolicy)

    override fun fetchOpeningResults(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> {
        val policy = config.collectionPolicyProvider(referenceDate)
        val window = openingDayWindow(referenceDate)
        val uriBuilder =
            KonepsPageUriBuilder { pageNo ->
                buildKonepsOperationUri(
                    base = listBaseUri,
                    serviceKey = config.serviceKey,
                    operation = listOperation,
                    pageNo = pageNo,
                    numOfRows = config.numOfRowsPerPage,
                    periodWindow = window,
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
            cursor,
        ) { item, itemPolicy, observedAt ->
            // F-1(verifier r1) — 낙찰 목록·개찰결과 목록은 한 행=한 공고라 listOperation
            // 이 선언한 rowIdentifierRawKeys 를 그대로 쓴다(AWARD_LIST·OPENING_RESULT_LIST
            // 둘 다 emptyList()).
            mapMaskedOpeningItem(item, itemPolicy, listSourceEndpoint, observedAt, listOperation.rowIdentifierRawKeys)
        }
    }

    /**
     * 예비가격 상세(②) — 서명이 [DetailFetchDecision.Fetch]를 요구해 3A 조회 가치 술어
     * ([bidvector.procurement.decideDetailFetch])를 거치지 않은 호출이 컴파일되지 않는다.
     * 단건 조회라 `cursor`가 없다 — 재사용하는 [walkKonepsNoticePages]가 `totalCount`(최대
     * 15 복수예가 행) 도달로 스스로 완료를 판정한다(공고당 1 호출, 우회 방지는 `maxPages`
     * 백스톱이 이미 겸한다).
     */
    override fun fetchReservePrices(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> =
        fetchSingleKonepsNotice(
            config,
            retry,
            rateLimiter,
            reserveDetailBaseUri,
            KonepsOperationPolicy.RESERVE_PRICE_DETAIL,
            evidence.noticeId,
        ) { item, itemPolicy, observedAt ->
            // F-1(verifier r1) — 예비가격 상세는 한 공고에 복수예가 15행까지 온다
            // (compnoRsrvtnPrceSno 마다 반복, §1.7.1). 그 순번을 행 식별자에 더하지
            // 않으면 14행이 duplicate 로 잘못 접힌다.
            mapMaskedOpeningItem(
                item,
                itemPolicy,
                SourceEndpoint.RESERVE_PRICE_DETAIL,
                observedAt,
                KonepsOperationPolicy.RESERVE_PRICE_DETAIL.rowIdentifierRawKeys,
            )
        }

    /**
     * 개찰완료(D-3F-1 (a), P-13 (a) 승인) — 투찰자별 행을 [mapMaskedOpeningItem]으로 관측한다
     * (계약 레지스트리 경로 — `CollectionPolicy.kt` §1.11 열, `SourceEndpoint.OPENING_COMPLETE`
     * 신규 넷째 군). `prcbdrBizno`·`prcbdrCeoNm`은 계약 미등재로 allow-list 반전에서 자동
     * 제외된다(P-10 (a) 가 구조적으로 선다) — 평가점수 넷도 같은 이유로 제외된다(P-13, scale
     * 미확정).
     */
    override fun fetchOpeningCompleteResults(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> =
        fetchSingleKonepsNotice(
            config,
            retry,
            rateLimiter,
            openingCompleteBaseUri,
            KonepsOperationPolicy.OPENING_COMPLETE,
            evidence.noticeId,
        ) { item, itemPolicy, observedAt ->
            mapMaskedOpeningItem(
                item,
                itemPolicy,
                SourceEndpoint.OPENING_COMPLETE,
                observedAt,
                KonepsOperationPolicy.OPENING_COMPLETE.rowIdentifierRawKeys,
            )
        }
}
