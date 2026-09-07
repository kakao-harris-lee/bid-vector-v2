package bidvector.adapters.extraction

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

internal fun buildLlmCircuitBreaker(
    name: String,
    policy: ExtractionPolicyData,
): CircuitBreaker {
    val config =
        CircuitBreakerConfig
            .custom()
            .failureRateThreshold(policy.breakerFailureRateThresholdPercent.toFloat())
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(policy.breakerSlidingWindowSize)
            // resilience4j 기본 minimumNumberOfCalls=100 은 정책 slidingWindowSize보다
            // 커서 breaker가 실제로 열리지 않는다(실측) — 같은 창 크기로 맞춘다.
            .minimumNumberOfCalls(policy.breakerSlidingWindowSize)
            .waitDurationInOpenState(policy.breakerWaitDurationInOpenState)
            .build()
    return CircuitBreaker.of(name, config)
}

internal fun buildLlmTimeLimiter(
    name: String,
    policy: ExtractionPolicyData,
): TimeLimiter {
    val config = TimeLimiterConfig.custom().timeoutDuration(policy.callTimeout).build()
    return TimeLimiter.of(name, config)
}

internal class LlmCallException(
    val failure: LlmCallFailure,
) : RuntimeException(failure.detail)

internal sealed interface ResilientCallOutcome {
    data class Success(
        val response: LlmResponse,
    ) : ResilientCallOutcome

    data object BreakerOpen : ResilientCallOutcome

    data object TimedOut : ResilientCallOutcome

    data class Failed(
        val failure: LlmCallFailure,
    ) : ResilientCallOutcome
}

/**
 * Resilience4j **한 계층**(TimeLimiter+CircuitBreaker, ADR 0005 D-11) — 재시도는 하지
 * 않는다(예산 안에서 1회, 실패는 그대로 `Uncertain`). [executor]가 [CompletableFuture]를
 * 만들어야 `TimeLimiter.executeFutureSupplier`가 실제로 시간을 잰다 — 동기 호출 자체에는
 * 시간 제한을 걸 수 없다.
 */
internal fun callWithResilience(
    circuitBreaker: CircuitBreaker,
    timeLimiter: TimeLimiter,
    executor: ExecutorService,
    call: () -> LlmCallOutcome,
): ResilientCallOutcome {
    val guarded: Supplier<LlmResponse> =
        CircuitBreaker.decorateSupplier(circuitBreaker) {
            when (val outcome = call()) {
                is LlmCallOutcome.Success -> outcome.response
                is LlmCallOutcome.Failure -> throw LlmCallException(outcome.failure)
            }
        }
    return try {
        val response = timeLimiter.executeFutureSupplier { CompletableFuture.supplyAsync(guarded, executor) }
        ResilientCallOutcome.Success(response)
    } catch (
        // breaker open·timeout 은 이름 하나로 관측된다(scope.md ⑤) — 원인 세부는 breaker
        // 상태·정책 timeout 값 자체이지 이 예외가 더 주지 않는다(3B `KonepsTransportOutcome`
        // 의 같은 판단).
        @Suppress("SwallowedException")
        notPermitted: CallNotPermittedException,
    ) {
        ResilientCallOutcome.BreakerOpen
    } catch (
        @Suppress("SwallowedException")
        timeout: TimeoutException,
    ) {
        ResilientCallOutcome.TimedOut
    } catch (callFailed: LlmCallException) {
        ResilientCallOutcome.Failed(callFailed.failure)
    }
}
