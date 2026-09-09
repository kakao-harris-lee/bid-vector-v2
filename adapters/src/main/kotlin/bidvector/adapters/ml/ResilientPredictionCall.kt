package bidvector.adapters.ml

import contract.bidvector.ml.v1.CalculateOptimalBidResponse
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.delay
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.toKotlinDuration

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

    /**
     * verifier r2 G-4(medium) — 호출부 예산 부족은 서버 건강 신호가 아니다. `TransportFailed`
     * 와 분리된 별도 가지라 `callResilient`가 `circuitBreaker.onError`를 부르지 않는다.
     */
    data object BudgetExhausted : PredictionCallOutcome

    data class TransportFailed(
        val error: Throwable,
    ) : PredictionCallOutcome
}

/** `callWithBoundedRetry`의 내부 신호 — 예산 소진은 예외가 아니라 이 타입으로 위로 올라간다. */
private sealed interface BoundedRetryOutcome {
    data class Success(
        val response: CalculateOptimalBidResponse,
    ) : BoundedRetryOutcome

    data object BudgetExhausted : BoundedRetryOutcome
}

/**
 * breaker + bounded retry **한 계층**(scope.md ③⑧, ADR 0005 D-11, ADR 0010 D-4·D-5) —
 * deadline은 호출부가 stub 에 이미 건 값을 쓴다(이 함수는 시간을 직접 재지 않는다 — 단,
 * `remainingBudget`으로 백오프가 예산을 넘는지는 잰다, verifier r1 F-1). 재시도는 transport
 * 예외(`isRetryableTransportStatus`)와 application failure(`isRetryableApplicationFailure`)를
 * **한 loop**(`callWithBoundedRetry`)에서 함께 보고, 재시도 사이마다 `backoff`(정책 배열,
 * attempt index 로 고른다)만큼 실제로 지연한다 — resilience4j `Retry`를 쓰지 않는다(재시도
 * 계층이 둘이 되지 않는다). **예산 소진은 breaker 계수 밖이다**(verifier r2 G-4) — 서버로
 * 나가는 호출을 한 번도 만들지 않은 채 예산이 끝나는 것은 서버 건강과 무관하므로
 * `circuitBreaker.onError`를 부르지 않는다. gRPC 실패는 [StatusException]·
 * [StatusRuntimeException] 둘 중 하나로만 온다 — 그 밖(coroutine 취소의
 * `CancellationException` 포함)은 여기서 잡지 않고 그대로 전파한다(`RetryRules.kt`와 같은
 * 이유, detekt `TooGenericExceptionCaught`).
 */
internal suspend fun callResilient(
    circuitBreaker: CircuitBreaker,
    maxAttempts: Int,
    backoff: List<Duration>,
    remainingBudget: Duration,
    call: suspend () -> CalculateOptimalBidResponse,
): PredictionCallOutcome {
    if (!circuitBreaker.tryAcquirePermission()) return PredictionCallOutcome.BreakerOpen
    val start = System.nanoTime()
    val deadlineAt = start + remainingBudget.toNanos()
    return try {
        when (val outcome = callWithBoundedRetry(maxAttempts, backoff, deadlineAt, call)) {
            is BoundedRetryOutcome.Success -> {
                circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS)
                PredictionCallOutcome.Responded(outcome.response)
            }

            BoundedRetryOutcome.BudgetExhausted -> {
                PredictionCallOutcome.BudgetExhausted
            }
        }
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
    backoff: List<Duration>,
    deadlineAt: Long,
    call: suspend () -> CalculateOptimalBidResponse,
): BoundedRetryOutcome {
    for (attempt in 0 until maxAttempts) {
        val isLastAttempt = attempt == maxAttempts - 1
        val outcome = attemptOnce(call, isLastAttempt)
        if (outcome != null) return BoundedRetryOutcome.Success(outcome)
        if (!isLastAttempt) {
            val canContinue = awaitBackoffOrSignalExhausted(attempt, backoff, deadlineAt)
            if (!canContinue) return BoundedRetryOutcome.BudgetExhausted
        }
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

/**
 * verifier r1 F-1(high) — 재시도 전에 정책 `backoff`(attempt index 로 고른다, 마지막 index
 * 이후는 마지막 값을 반복)만큼 `delay`한다(ADR 0010 D-4 「RESOURCE_EXHAUSTED(백오프 필수)」,
 * 모든 재시도 가능 status 에 균일 적용). **남은 예산이 그 백오프조차 감당하지 못하면
 * 재시도하지 않는다** — `false`를 내어 `callWithBoundedRetry`가 예외 없이
 * `BoundedRetryOutcome.BudgetExhausted`로 접게 한다(verifier r2 G-4 — 이전엔 합성
 * `Status.DEADLINE_EXCEEDED`를 던져 breaker 가 그 실패를 서버 오류로 계수했다).
 * `delay`는 verifier r2 G-6 — sub-millisecond `backoff`가 `toMillis()`로 0 절삭되지 않게
 * `kotlin.time.Duration` 오버로드(나노 보존)를 쓴다.
 */
private suspend fun awaitBackoffOrSignalExhausted(
    attempt: Int,
    backoff: List<Duration>,
    deadlineAt: Long,
): Boolean {
    val backoffDuration = backoff.getOrElse(attempt) { backoff.last() }
    val remainingNanos = deadlineAt - System.nanoTime()
    if (remainingNanos <= backoffDuration.toNanos()) return false
    delay(backoffDuration.toKotlinDuration())
    return true
}
