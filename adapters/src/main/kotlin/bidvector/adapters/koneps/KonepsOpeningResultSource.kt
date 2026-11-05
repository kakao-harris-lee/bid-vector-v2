package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.NoticeSourcePort
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
 * `openingCompleteBaseUri` — 개찰완료(`getOpengResultListInfoOpengCompt`) 전용 엔드포인트.
 * [fetchOpeningCompleteResults]가 이 값을 쓴다 — 예비가격 상세와도 다른 오퍼레이션이라 baseUri
 * 가 셋이다.
 */
class KonepsOpeningResultSource(
    private val listBaseUri: URI,
    private val listOperation: KonepsOperationDescriptor,
    private val listSourceEndpoint: SourceEndpoint,
    private val reserveDetailBaseUri: URI,
    private val openingCompleteBaseUri: URI,
    private val bidPriceFormulaABaseUri: URI,
    private val baseAmountBaseUri: URI,
    private val config: KonepsSourceConfig,
) : OpeningResultSourcePort {
    private val retryName = "koneps-opening-${listSourceEndpoint.name.lowercase()}"
    private val retry: Retry = buildKonepsRetry(retryName, config.httpPolicy)
    private val rateLimiter: RateLimiter = buildKonepsRateLimiter(retryName, config.httpPolicy)

    override fun fetchOpeningResults(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> =
        walkKonepsOpeningList(
            listBaseUri,
            listOperation,
            listSourceEndpoint,
            config,
            retry,
            rateLimiter,
            referenceDate,
            cursor,
        )

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
            SourceEndpoint.RESERVE_PRICE_DETAIL,
            evidence.noticeKeyHash,
        ) { item, itemPolicy, observedAt ->
            // 예비가격 상세는 한 공고에 복수예가 15행까지 온다
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
            SourceEndpoint.OPENING_COMPLETE,
            evidence.noticeKeyHash,
        ) { item, itemPolicy, observedAt ->
            mapMaskedOpeningItem(
                item,
                itemPolicy,
                SourceEndpoint.OPENING_COMPLETE,
                observedAt,
                KonepsOperationPolicy.OPENING_COMPLETE.rowIdentifierRawKeys,
            )
        }

    /**
     * 입찰가격산식 A 정보(M6/6G D-6G-12) — 공사 하한가 산식의 A 를 나른다.
     * [bidPriceFormulaABaseUri] 는 위 셋과 **다른 서비스**(입찰공고정보서비스)의 엔드포인트라
     * baseUri 가 넷이 된다. 항목 매핑은 같은 masking 경로를 지난다 — 이 응답에는 사업자·개인
     * 식별자 키가 없지만 계약 미등재 키를 떨어뜨리는 반전은 그대로 선다.
     */
    override fun fetchBidPriceFormulaA(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> =
        fetchSingleKonepsNotice(
            config,
            retry,
            rateLimiter,
            bidPriceFormulaABaseUri,
            KonepsOperationPolicy.BID_PRICE_FORMULA_A,
            evidence.noticeId,
            SourceEndpoint.BID_PRICE_FORMULA_A,
            evidence.noticeKeyHash,
        ) { item, itemPolicy, observedAt ->
            mapMaskedOpeningItem(
                item,
                itemPolicy,
                SourceEndpoint.BID_PRICE_FORMULA_A,
                observedAt,
                KonepsOperationPolicy.BID_PRICE_FORMULA_A.rowIdentifierRawKeys,
            )
        }

    /**
     * 기초금액 조회(M6/6G D-6G-19) — [baseAmountBaseUri] 는 **이 인스턴스의 업무 대분류**에 해당하는
     * 경로다(물품·공사·용역이 각자 다른 오퍼레이션이다). 호출부가 업무를 고르지 않는다 — 인스턴스가
     * 자기 경로를 들고 있으므로 잘못된 업무의 오퍼레이션을 부르는 조합이 생기지 않는다.
     */
    override fun fetchBaseAmount(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> =
        fetchSingleKonepsNotice(
            config,
            retry,
            rateLimiter,
            baseAmountBaseUri,
            KonepsOperationPolicy.BASE_AMOUNT_DETAIL,
            evidence.noticeId,
            SourceEndpoint.BASE_AMOUNT_DETAIL,
            evidence.noticeKeyHash,
        ) { item, itemPolicy, observedAt ->
            mapMaskedOpeningItem(
                item,
                itemPolicy,
                SourceEndpoint.BASE_AMOUNT_DETAIL,
                observedAt,
                KonepsOperationPolicy.BASE_AMOUNT_DETAIL.rowIdentifierRawKeys,
            )
        }
}

/**
 * 개찰 축 **목록** 오퍼레이션 한 쪽 걷기 — 낙찰 목록과 개찰결과 목록이 이 자리 하나를 공유한다.
 *
 * 두 소스가 같은 걷기를 각자 조립하면(창·URI·회계·매핑 네 줄) 한쪽만 고치는 표류가 생기고, 그
 * 표류는 조용하다(같은 모양을 두 벌 두는 것을 중복 게이트가 실제로 잡는다). 재시도·속도 한도는
 * **인자로 받는다** — 인스턴스마다 자기 것을 쥐므로, 이 함수가 그것을 지으면 상세 축과 목록 축이
 * 한 소스 안에서 서로 다른 한도를 갖게 된다(기존 거동이 바뀐다).
 */
internal fun walkKonepsOpeningList(
    listBaseUri: URI,
    listOperation: KonepsOperationDescriptor,
    listSourceEndpoint: SourceEndpoint,
    config: KonepsSourceConfig,
    retry: Retry,
    rateLimiter: RateLimiter,
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
        config.gate,
        KonepsCallContext(listSourceEndpoint),
        retry,
        rateLimiter,
        uriBuilder,
        config.httpPolicy,
        policy,
        config.clock,
        cursor,
    ) { item, itemPolicy, observedAt ->
        // 낙찰 목록·개찰결과 목록은 한 행=한 공고라 listOperation 이 선언한 rowIdentifierRawKeys 를
        // 그대로 쓴다(AWARD_LIST·OPENING_RESULT_LIST 둘 다 emptyList()).
        mapMaskedOpeningItem(item, itemPolicy, listSourceEndpoint, observedAt, listOperation.rowIdentifierRawKeys)
    }
}

/**
 * 낙찰 목록 소스(M9/award-ingest D-1·D-2) — **목록 걷기 하나뿐**이다.
 *
 * `KonepsOpeningResultSource` 를 쓰지 않는 이유는 그 타입이 상세 축 넷의 엔드포인트를 **필수로**
 * 요구하기 때문이다: 이 갈래는 그 넷을 부르지 않으므로 넘길 참값이 없고, 아무 값이나 넣으면
 * 「부르면 엉뚱한 곳으로 가는 포트」가 생긴다. 포트를 좁히면 그 조합 자체가 표현되지 않는다.
 *
 * 구현하는 포트는 도메인의 [NoticeSourcePort] 다 — 메서드가 목록 걷기 하나뿐이라 이 갈래가 필요한
 * 전부이고, 낙찰 목록은 **한 행 = 한 공고**라 이름과도 맞다. 이 패키지는 `procurement`·`shared-kernel`
 * 밖의 도메인·application 패키지를 참조할 수 없다(`KonepsAdapterDependencyTest`) — 포트를 새로 지을
 * 자리는 domain 뿐이고, 이미 맞는 것이 있으므로 짓지 않는다.
 */
internal class KonepsAwardListSource(
    private val listBaseUri: URI,
    private val config: KonepsSourceConfig,
) : NoticeSourcePort {
    private val retryName = "koneps-award-${SourceEndpoint.OPENING_AWARD_LIST.name.lowercase()}"
    private val retry: Retry = buildKonepsRetry(retryName, config.httpPolicy)
    private val rateLimiter: RateLimiter = buildKonepsRateLimiter(retryName, config.httpPolicy)

    override fun fetchNotices(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> =
        walkKonepsOpeningList(
            listBaseUri,
            KonepsOperationPolicy.AWARD_LIST,
            SourceEndpoint.OPENING_AWARD_LIST,
            config,
            retry,
            rateLimiter,
            referenceDate,
            cursor,
        )
}

/**
 * 낙찰 목록 소스 한 벌(M9/award-ingest) — 호출부는 **경로만** 준다. 조회 축(`inqryDiv=3`, 개찰일시)과
 * 적재 endpoint(`OPENING_AWARD_LIST`)는 이 모듈 안에 남는다: 배선이 그 둘을 고를 수 있으면 다른 축을
 * 걷거나 다른 계약 슬롯으로 적재하는 조합이 설정 한 줄로 생긴다.
 */
fun konepsAwardListSource(
    listBaseUri: URI,
    config: KonepsSourceConfig,
): NoticeSourcePort = KonepsAwardListSource(listBaseUri, config)

/**
 * 6G 표본틀 소스 한 벌(M6/6G D-6G-11·19) — 호출부는 **경로만** 준다. `inqryDiv` 축과 행 식별자 같은
 * wire 계약은 이 모듈 안에 남는다(`KonepsOperationPolicy` 는 `internal` 이다) — 배선이 조회 축을 고르면
 * 조용히 다른 축을 걷는 경로가 생긴다.
 *
 * baseUri 가 **넷**인 이유는 서비스가 둘이기 때문이다: 목록·예비가격 상세·개찰완료는 낙찰정보서비스,
 * 입찰가격산식 A·기초금액 조회는 입찰공고정보서비스다.
 */
fun konepsOpeningResultSourceByNoticeDate(
    listBaseUri: URI,
    reserveDetailBaseUri: URI,
    openingCompleteBaseUri: URI,
    bidPriceFormulaABaseUri: URI,
    baseAmountBaseUri: URI,
    config: KonepsSourceConfig,
): OpeningResultSourcePort =
    KonepsOpeningResultSource(
        listBaseUri = listBaseUri,
        listOperation = KonepsOperationPolicy.OPENING_RESULT_LIST_BY_NOTICE_DATE,
        listSourceEndpoint = SourceEndpoint.OPENING_RESULT_LIST,
        reserveDetailBaseUri = reserveDetailBaseUri,
        openingCompleteBaseUri = openingCompleteBaseUri,
        bidPriceFormulaABaseUri = bidPriceFormulaABaseUri,
        baseAmountBaseUri = baseAmountBaseUri,
        config = config,
    )
