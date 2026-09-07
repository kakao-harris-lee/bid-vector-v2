package bidvector.adapters.koneps

import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.sharedkernel.Resolution
import java.time.Duration

/**
 * 시나리오 test 전용 HTTP 정책 — 운영 정책([KONEPS_HTTP_POLICY])과 같은 형태, 값만 ms 단위로
 * 좁혀 test 가 초 단위로 끝나게 한다(운영값 그대로면 429 회복 대기가 조사 a-4 의 "~2분"까지
 * 늘어난다).
 */
internal fun testKonepsHttpPolicy(
    maxAttempts: Int = 3,
    maxPages: Int = 5,
    requestTimeout: Duration = Duration.ofMillis(200),
    maxJsonDepth: Int = 32,
): KonepsHttpPolicyData =
    KonepsHttpPolicyData(
        requestTimeout = requestTimeout,
        maxAttempts = maxAttempts,
        retryBackoff = List(maxAttempts - 1) { Duration.ofMillis(10) },
        rateLimiterPermits = 1000,
        rateLimiterPeriod = Duration.ofMillis(100),
        rateLimiterWait = Duration.ofSeconds(2),
        maxPages = maxPages,
        maxJsonDepth = maxJsonDepth,
    )

/** 3A 운영 정책을 그대로 resolve — 3B 는 필드 계약·resultCode 범주표를 재선언하지 않는다. */
internal fun resolvedCollectionPolicy(referenceDate: CollectionReferenceDate): KonepsCollectionPolicyData {
    val resolution = KONEPS_COLLECTION_POLICY.resolve(referenceDate.date)
    check(resolution is Resolution.Resolved) { "KONEPS_COLLECTION_POLICY 가 $referenceDate 에 적용되지 않는다" }
    return resolution.value
}
