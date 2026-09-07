package bidvector.adapters.koneps

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Duration

/**
 * KONEPS HTTP 전송 정책(②, D-3B-2·D-M3-5) — timeout·재시도 상한·백오프 일정·rate limiter·
 * pagination 백스톱을 한 데이터로 묶는다(매직넘버 금지 — main 리터럴 없이 이 값을 통해서만
 * 어댑터가 그 수를 읽는다). `retryBackoff`는 base·multiplier(`Double`) 대신 **시도별 대기
 * 목록**으로 둔다 — `api-type-policy` 의 raw 부동소수 금지와 같은 방향을 정책 표현에도 지킨다.
 */
data class KonepsHttpPolicyData(
    val requestTimeout: Duration,
    val maxAttempts: Int,
    val retryBackoff: List<Duration>,
    val rateLimiterPermits: Int,
    val rateLimiterPeriod: Duration,
    val rateLimiterWait: Duration,
    val maxPages: Int,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts는 1 이상이어야 한다: $maxAttempts" }
        require(retryBackoff.size == maxAttempts - 1) {
            "retryBackoff 길이(${retryBackoff.size})는 maxAttempts-1(${maxAttempts - 1})과 같아야 한다 — 시도별 대기"
        }
        require(rateLimiterPermits >= 1) { "rateLimiterPermits는 1 이상이어야 한다: $rateLimiterPermits" }
        require(maxPages >= 1) { "maxPages는 1 이상이어야 한다: $maxPages" }
    }
}

/**
 * 운영 정책 인스턴스(D-M3-2·D-M3-5 (a)) — 초기값은 「보수적 + 관측 갱신」, 출처는 조사
 * a-4·a-5(`_workspace/m3-prep/01_scout_collection.md`) — **429 원인은 동시성, 회복 약
 * 2분**. rate limiter 의 1차 축은 동시성 상한(허용 1 permit/period)이고 총량 예산은
 * 2차(`OPEN-COL-05`). test 는 이 인스턴스를 그대로 쓰지 않는다 — 같은 형태를 좁은 값으로
 * 둔 별도 인스턴스(`adapters/src/test`)를 쓴다(운영값 그대로면 test 가 조사 a-4 의 "~2분"까지
 * 늘어난다).
 */
val KONEPS_HTTP_POLICY: EffectiveDatedPolicy<KonepsHttpPolicyData> =
    EffectiveDatedPolicy(
        source =
            "_workspace/m3-prep/01_scout_collection.md 조사 a-4·a-5(legacy-behavior/observed, " +
                "429 회복 ~2분·원인은 동시성) — 「보수적 + 관측 갱신」(D-M3-5 (a))",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    KonepsHttpPolicyData(
                        requestTimeout = Duration.ofSeconds(10),
                        maxAttempts = 4,
                        retryBackoff = listOf(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(15)),
                        rateLimiterPermits = 1,
                        rateLimiterPeriod = Duration.ofSeconds(1),
                        rateLimiterWait = Duration.ofSeconds(20),
                        maxPages = 50,
                    ),
            ),
    )
