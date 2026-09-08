package bidvector.adapters.koneps

import bidvector.procurement.DocumentSourcePort
import bidvector.procurement.QualificationFetchDecision
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import java.net.URI

/**
 * `DocumentSourcePort` 구현(③, COL-04) — `getBidPblancListInfoLicenseLimit`(입찰공고정보서비스
 * 오퍼레이션 15, §1.9.5). 서명이 [QualificationFetchDecision.Fetch]를 요구해 D-3B2-5 (a)
 * 조회 가치 술어([bidvector.procurement.decideQualificationFetch])를 거치지 않은 호출이
 * 컴파일되지 않는다.
 *
 * license-limit 응답 필드(`lmtGrpNo`·`lmtSno`·`lcnsLmtNm`·`permsnIndstrytyList`·`rgstDt`·
 * `bsnsDivNm`·`indstrytyMfrcFldList`)는 사업자·개인 식별자가 아니다 — P-10 (a) masking 은
 * 개찰 축(`opengCorpInfo`·`bidwinnrNm`·`bidwinnrBizno`)에만 해당한다. 그래서 이 port 는
 * [mapMaskedOpeningItem]이 아니라 3B 의 [mapRawItem](전체 원문 보존 + COL-07 unknownFields
 * 계수)을 그대로 재사용한다 — 이 오퍼레이션 전용 계약 행을 새로 만들지 않는다(과잉 금지,
 * 설계 검토 (3)).
 *
 * 「제한 없음」(resultCode `00`+`totalCount=0`)은 3B 의 envelope·pagination 기계가 이미
 * 「성공 + 빈 항목」으로 낸다 — `bidNtceOrd` 누락(resultCode `08`)은 3A `resultCodeCategories`
 * 표(P-4)의 `INPUT_ERROR` 분류를 그대로 탄다. 이 port 는 어느 쪽도 새로 만들지 않는다.
 */
class KonepsLicenseLimitDocumentSource(
    private val baseUri: URI,
    private val config: KonepsSourceConfig,
) : DocumentSourcePort {
    private val retry: Retry = buildKonepsRetry("koneps-license-limit", config.httpPolicy)
    private val rateLimiter: RateLimiter = buildKonepsRateLimiter("koneps-license-limit", config.httpPolicy)

    override fun fetchQualificationText(evidence: QualificationFetchDecision.Fetch): SourceBatch<RawNoticeObservation> =
        fetchSingleKonepsNotice(
            config,
            retry,
            rateLimiter,
            baseUri,
            KonepsOperationPolicy.LICENSE_LIMIT_DETAIL,
            evidence.noticeId,
        ) { item, itemPolicy, observedAt ->
            // F-1(verifier r1) — license-limit 은 한 공고에 제한그룹번호·제한순번 축으로
            // 복수 행이 온다. 그 짝을 행 식별자에 더하지 않으면 첫 행만 남고 나머지가
            // duplicate 로 잘못 접힌다.
            mapRawItem(
                item,
                itemPolicy,
                SourceEndpoint.LICENSE_LIMIT_DETAIL,
                observedAt,
                KonepsOperationPolicy.LICENSE_LIMIT_DETAIL.rowIdentifierRawKeys,
            )
        }
}
