package bidvector.adapters.extraction

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Duration

/**
 * chunk·호출 예산·timeout·circuit breaker 값(⑤, ADR 0010 D-1 「값은 정책 데이터, 규칙은
 * ADR」·D-5 「circuit breaker 는 소비자 쪽 정책」) — main 리터럴 0, 운영 인스턴스는
 * [EXTRACTION_POLICY] 하나다. `breakerFailureRateThresholdPercent`를 `Int`(0~100)로 두는
 * 이유는 `api-type-policy.properties`가 부동소수 저장을 domain 공개 API 에서 막는 규율과
 * 같은 방향을 정책 표현에도 지키기 위해서다 — Resilience4j `float` 변환은 배선 지점
 * (`ResilientLlmCall.kt`) 한 곳에서만 한다.
 */
data class ExtractionPolicyData(
    val chunkChars: Int,
    val maxChunksPerDocument: Int,
    val maxCallsPerDocument: Int,
    val maxTokensPerCall: Int,
    /**
     * resilience4j `TimeLimiter`의 시한 — 이 값이 「timeout」의 정본이다(scope ⑤).
     * [httpRequestTimeout]과 값을 공유하지 않는다(verifier r1 F-2) — 예전엔 둘이 같은
     * 값을 써서 JDK `HttpClient`의 자체 시한과 `TimeLimiter`가 경합했다(같은 시각에
     * 둘 다 만료 가능 → `Uncertain(Timeout)`과 `Uncertain(TransportFailed)`가 실행마다
     * 갈렸다, `ExtractionFailOpenTest` flaky 실측 S-2·S-4). `httpRequestTimeout`이 이
     * 값보다 항상 크므로(`init` 강제) `TimeLimiter`가 항상 먼저 끊어 분류가 결정론적이다.
     */
    val callTimeout: Duration,
    /**
     * JDK `HttpClient` 자신의 요청 시한 — [callTimeout]보다 항상 크다(`init`). 이 값이
     * 만료되는 정상 상황은 없어야 한다 — `TimeLimiter`가 항상 먼저 끊고 실행 중이던
     * 호출을 취소(interrupt)한다. 존재 이유는 순수히 방어적 상한(스레드 누수 봉쇄)이다.
     */
    val httpRequestTimeout: Duration,
    val breakerFailureRateThresholdPercent: Int,
    val breakerSlidingWindowSize: Int,
    val breakerWaitDurationInOpenState: Duration,
    val attachmentFetchLimits: AttachmentFetchLimitsPolicy,
) {
    init {
        require(chunkChars > 0) { "chunkChars는 0보다 커야 한다: $chunkChars" }
        require(maxChunksPerDocument > 0) { "maxChunksPerDocument는 0보다 커야 한다: $maxChunksPerDocument" }
        require(maxCallsPerDocument > 0) { "maxCallsPerDocument는 0보다 커야 한다: $maxCallsPerDocument" }
        require(maxTokensPerCall > 0) { "maxTokensPerCall은 0보다 커야 한다: $maxTokensPerCall" }
        require(httpRequestTimeout > callTimeout) {
            "httpRequestTimeout($httpRequestTimeout)은 callTimeout($callTimeout)보다 커야 한다 " +
                "— 같거나 작으면 TimeLimiter와 HttpClient 자체 시한이 경합한다(verifier r1 F-2)"
        }
        require(breakerFailureRateThresholdPercent in MIN_PERCENT..MAX_PERCENT) {
            "breakerFailureRateThresholdPercent는 $MIN_PERCENT~$MAX_PERCENT 이어야 한다: $breakerFailureRateThresholdPercent"
        }
        require(breakerSlidingWindowSize > 0) { "breakerSlidingWindowSize는 0보다 커야 한다: $breakerSlidingWindowSize" }
    }

    private companion object {
        const val MIN_PERCENT = 1
        const val MAX_PERCENT = 100
    }
}

/** [AttachmentFetchLimits]의 정책 표현 — procurement 값 객체는 여기서 값이 채워진 뒤 만들어진다. */
data class AttachmentFetchLimitsPolicy(
    val maxBytes: Long,
    val timeout: Duration,
) {
    init {
        require(maxBytes > 0) { "maxBytes는 0보다 커야 한다: $maxBytes" }
    }
}

/**
 * 운영 정책 인스턴스(D-3C-5) — 초기값은 「보수적 + 관측 갱신」(3B `KONEPS_HTTP_POLICY`와
 * 같은 배관 관례). test 는 이 인스턴스를 그대로 쓰지 않는다 — 좁은 값의 별도 인스턴스
 * (`adapters/src/test`)를 쓴다(3B `testKonepsHttpPolicy` 관례).
 */
val EXTRACTION_POLICY: EffectiveDatedPolicy<ExtractionPolicyData> =
    EffectiveDatedPolicy(
        source =
            "reports/evidence/m3/3c/scope.md D-3C-5 — 「보수적 초기값 + 관측 갱신」" +
                "(3B D-3B-2·ADR 0010 D-1 과 같은 배관, 실측 근거 없음 — 관측 뒤 version 갱신)",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    ExtractionPolicyData(
                        chunkChars = 4000,
                        maxChunksPerDocument = 20,
                        maxCallsPerDocument = 20,
                        maxTokensPerCall = 4000,
                        callTimeout = Duration.ofSeconds(30),
                        httpRequestTimeout = Duration.ofSeconds(45),
                        breakerFailureRateThresholdPercent = 50,
                        breakerSlidingWindowSize = 10,
                        breakerWaitDurationInOpenState = Duration.ofSeconds(30),
                        attachmentFetchLimits =
                            AttachmentFetchLimitsPolicy(
                                maxBytes = 20L * 1024 * 1024,
                                timeout = Duration.ofSeconds(15),
                            ),
                    ),
            ),
    )
