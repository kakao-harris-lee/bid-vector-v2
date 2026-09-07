package bidvector.adapters.koneps

import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.ResultCodeCategory
import bidvector.procurement.TruncationCause
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.net.URI
import java.net.http.HttpClient

private const val HTTP_TOO_MANY_REQUESTS = 429

/** 한 페이지 호출의 최종(재시도·rate limiter 를 다 거친 뒤) 판정(②③). */
internal sealed interface KonepsCallOutcome {
    data class Success(
        val body: KonepsEnvelopeOutcome.Success,
    ) : KonepsCallOutcome

    data object NoData : KonepsCallOutcome

    /** 비재시도 실패 — 재시도 가능 범주를 소진했거나 처음부터 비재시도 범주다(H-3, 사유가 갈린다). */
    data class Failed(
        val cause: TruncationCause,
        val detail: String,
    ) : KonepsCallOutcome

    /** rate limiter 대기 시간 초과 — 이 어댑터 자신의 quota 보호(호출조차 나가지 않았다). */
    data class Throttled(
        val detail: String,
    ) : KonepsCallOutcome
}

/**
 * 걷기 전체(여러 페이지 호출)에 걸쳐 quota 신호·자체 backoff 스킵을 누적한다(H-3, verifier
 * r1 — scope ② 「quota 초과는 회계에 backoffSkipped/quotaExceeded로」). `quotaExceeded`는
 * HTTP 429·`resultCode 22` 를 **관측할 때마다**(그 시도가 나중에 성공하든 소진되든) 센다.
 * `backoffSkipped`는 rate limiter 자신이 허가를 거부해(`RequestNotPermitted`) 실제 호출조차
 * 나가지 못한 시도 수다.
 */
internal class KonepsAttemptCounters {
    var quotaExceeded: Int = 0
        private set
    var backoffSkipped: Int = 0
        private set

    fun recordQuotaSignal() {
        quotaExceeded++
    }

    fun recordBackoffSkipped() {
        backoffSkipped++
    }
}

/** 전송+envelope 판정 한 스텝 — Resilience4j `retryOnResult` predicate 의 최소 단위. */
internal sealed interface KonepsRawStep {
    data class EnvelopeStep(
        val outcome: KonepsEnvelopeOutcome,
    ) : KonepsRawStep

    data class TransportStep(
        val outcome: KonepsTransportOutcome,
    ) : KonepsRawStep
}

private fun rawStep(
    httpClient: HttpClient,
    uri: URI,
    httpPolicy: KonepsHttpPolicyData,
    collectionPolicy: KonepsCollectionPolicyData,
): KonepsRawStep {
    val transport = sendKonepsRequest(httpClient, uri, httpPolicy.requestTimeout)
    return when {
        transport is KonepsTransportOutcome.Received && transport.status != HTTP_TOO_MANY_REQUESTS -> {
            KonepsRawStep.EnvelopeStep(parseKonepsEnvelope(transport.body, collectionPolicy, httpPolicy.maxJsonDepth))
        }

        else -> {
            KonepsRawStep.TransportStep(transport)
        }
    }
}

private fun isRetryableStep(step: KonepsRawStep): Boolean =
    when (step) {
        is KonepsRawStep.TransportStep -> {
            when (val outcome = step.outcome) {
                KonepsTransportOutcome.TimedOut -> true
                is KonepsTransportOutcome.TransportFailed -> true
                is KonepsTransportOutcome.Received -> outcome.status == HTTP_TOO_MANY_REQUESTS
            }
        }

        is KonepsRawStep.EnvelopeStep -> {
            val category = (step.outcome as? KonepsEnvelopeOutcome.Classified)?.category
            category == ResultCodeCategory.RETRYABLE || category == ResultCodeCategory.QUOTA_EXCEEDED
        }
    }

private fun foldFinal(step: KonepsRawStep): KonepsCallOutcome =
    when (step) {
        is KonepsRawStep.TransportStep -> {
            KonepsCallOutcome.Failed(causeFor(step), describeTransport(step.outcome))
        }

        is KonepsRawStep.EnvelopeStep -> {
            when (val envelope = step.outcome) {
                is KonepsEnvelopeOutcome.Success -> KonepsCallOutcome.Success(envelope)
                KonepsEnvelopeOutcome.NoData -> KonepsCallOutcome.NoData
                else -> KonepsCallOutcome.Failed(causeFor(step), describeEnvelope(envelope))
            }
        }
    }

private fun backoffMillisFor(
    policy: KonepsHttpPolicyData,
    attempt: Int,
): Long {
    val schedule = policy.retryBackoff
    val duration = schedule.getOrNull(attempt - 1) ?: schedule.lastOrNull() ?: java.time.Duration.ZERO
    return duration.toMillis()
}

internal fun buildKonepsRetry(
    name: String,
    policy: KonepsHttpPolicyData,
): Retry {
    val config =
        RetryConfig
            .custom<KonepsRawStep>()
            .maxAttempts(policy.maxAttempts)
            .intervalFunction(IntervalFunction { attempt -> backoffMillisFor(policy, attempt) })
            .retryOnResult { step -> isRetryableStep(step) }
            .retryOnException { it is RequestNotPermitted }
            .build()
    return Retry.of(name, config)
}

internal fun buildKonepsRateLimiter(
    name: String,
    policy: KonepsHttpPolicyData,
): RateLimiter {
    val config =
        RateLimiterConfig
            .custom()
            .limitForPeriod(policy.rateLimiterPermits)
            .limitRefreshPeriod(policy.rateLimiterPeriod)
            .timeoutDuration(policy.rateLimiterWait)
            .build()
    return RateLimiter.of(name, config)
}

/**
 * Resilience4j **한 계층**(ADR 0005 D-11) — `Retry`가 바깥, `RateLimiter`가 안쪽이다.
 * `RateLimiter`가 `RequestNotPermitted`를 던지면 `Retry`의 `retryOnException`이 그것도
 * 재시도 대상으로 잡는다(백오프를 태운 뒤에도 허가를 못 받으면 [KonepsCallOutcome
 * .Throttled]). `counters`는 매 시도(재시도 포함)를 관찰해 quota 신호·자체 throttle 스킵을
 * 센다 — Resilience4j 가 내부에서 삼키는 중간 실패도 놓치지 않는다(H-3).
 */
internal fun fetchPageResilient(
    httpClient: HttpClient,
    retry: Retry,
    rateLimiter: RateLimiter,
    uri: URI,
    httpPolicy: KonepsHttpPolicyData,
    collectionPolicy: KonepsCollectionPolicyData,
    counters: KonepsAttemptCounters,
): KonepsCallOutcome {
    val supplier = {
        val step = rawStep(httpClient, uri, httpPolicy, collectionPolicy)
        if (isQuotaSignal(step)) counters.recordQuotaSignal()
        step
    }
    val rateLimited = RateLimiter.decorateSupplier(rateLimiter, supplier)
    val counted: () -> KonepsRawStep = {
        try {
            rateLimited.get()
        } catch (refused: RequestNotPermitted) {
            counters.recordBackoffSkipped()
            throw refused
        }
    }
    val decorated = Retry.decorateSupplier(retry, counted)
    return try {
        foldFinal(decorated.get())
    } catch (throttled: RequestNotPermitted) {
        KonepsCallOutcome.Throttled("rate limiter '${throttled.causingRateLimiterName}' 허가 대기 시간 초과")
    }
}
