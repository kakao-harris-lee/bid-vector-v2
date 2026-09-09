package bidvector.adapters.ml

import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit

/**
 * breaker 설정(scope.md ⑧) — 3C `buildLlmCircuitBreaker`와 같은 배관 의도(minimumNumberOfCalls
 * 를 창 크기와 맞춘다)이나, `.apply{}` 블록으로 구성한다 — 3C 는 `.custom()` 뒤 메서드
 * 체인이라 같은 값 셋을 같은 순서로 연쇄 호출하면 CPD 가 구조적 중복으로 잡는다(실측,
 * `qualityBaseline` 로컬 재현). 값 자체가 같은 정책 축(failure rate·sliding window·wait
 * duration)을 나르는 것은 우연이 아니라 resilience4j breaker 설정의 공통 형태다.
 */
internal fun buildPredictionCircuitBreaker(
    name: String,
    policy: MlCallPolicyData,
): CircuitBreaker {
    val builder = CircuitBreakerConfig.custom()
    builder.failureRateThreshold(policy.breakerFailureRateThresholdPercent.toFloat())
    builder.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
    builder.slidingWindowSize(policy.breakerSlidingWindowSize)
    builder.minimumNumberOfCalls(policy.breakerSlidingWindowSize)
    builder.waitDurationInOpenState(policy.breakerWaitDurationInOpenState)
    return CircuitBreaker.of(name, builder.build())
}

internal sealed interface PredictionCallOutcome {
    data class Responded(
        val response: CalculateOptimalBidResponse,
    ) : PredictionCallOutcome

    data object BreakerOpen : PredictionCallOutcome

    data class TransportFailed(
        val error: Throwable,
    ) : PredictionCallOutcome
}

/**
 * breaker + bounded retry **한 계층**(scope.md ③, ADR 0005 D-11, ADR 0010 D-5) — deadline은
 * 호출부가 stub 에 이미 건 값을 쓴다(이 함수는 시간을 직접 재지 않는다). 재시도는 transport
 * 예외(`isRetryableTransportStatus`)와 application failure(`isRetryableApplicationFailure`)를
 * **한 loop**(`callWithBoundedRetry`)에서 함께 본다 — resilience4j `Retry`를 쓰지 않는다(재시도
 * 계층이 둘이 되지 않는다). gRPC 실패는 [StatusException]·[StatusRuntimeException] 둘 중
 * 하나로만 온다 — 그 밖(coroutine 취소의 `CancellationException` 포함)은 여기서 잡지 않고
 * 그대로 전파한다(`RetryRules.kt`와 같은 이유, detekt `TooGenericExceptionCaught`).
 */
internal suspend fun callResilient(
    circuitBreaker: CircuitBreaker,
    maxAttempts: Int,
    call: suspend () -> CalculateOptimalBidResponse,
): PredictionCallOutcome {
    if (!circuitBreaker.tryAcquirePermission()) return PredictionCallOutcome.BreakerOpen
    val start = System.nanoTime()
    return try {
        val response = callWithBoundedRetry(maxAttempts, call)
        circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        PredictionCallOutcome.Responded(response)
    } catch (statusError: StatusException) {
        circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, statusError)
        PredictionCallOutcome.TransportFailed(statusError)
    } catch (statusRuntimeError: StatusRuntimeException) {
        circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, statusRuntimeError)
        PredictionCallOutcome.TransportFailed(statusRuntimeError)
    }
}

private suspend fun callWithBoundedRetry(
    maxAttempts: Int,
    call: suspend () -> CalculateOptimalBidResponse,
): CalculateOptimalBidResponse {
    for (attempt in 0 until maxAttempts) {
        val outcome = attemptOnce(call, isLastAttempt = attempt == maxAttempts - 1)
        if (outcome != null) return outcome
    }
    error("retry loop 이 결과 없이 끝났다")
}

/** 재시도할 값이면 `null`을 낸다(호출부가 다음 attempt 로 넘어간다) — 상한이면 그대로 던지거나 낸다. */
private suspend fun attemptOnce(
    call: suspend () -> CalculateOptimalBidResponse,
    isLastAttempt: Boolean,
): CalculateOptimalBidResponse? =
    try {
        val response = call()
        if (!isRetryableFailureResponse(response) || isLastAttempt) response else null
    } catch (statusError: StatusException) {
        if (!isRetryableTransportStatus(statusError.status.code) || isLastAttempt) throw statusError
        null
    } catch (statusRuntimeError: StatusRuntimeException) {
        if (!isRetryableTransportStatus(statusRuntimeError.status.code) || isLastAttempt) throw statusRuntimeError
        null
    }

private fun isRetryableFailureResponse(response: CalculateOptimalBidResponse): Boolean =
    response.resultCase == CalculateOptimalBidResponse.ResultCase.FAILURE &&
        isRetryableApplicationFailure(response.failure.retryable)
