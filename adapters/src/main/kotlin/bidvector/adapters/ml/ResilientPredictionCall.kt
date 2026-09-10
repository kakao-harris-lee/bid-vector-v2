package bidvector.adapters.ml

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.grpc.stub.AbstractStub
import kotlinx.coroutines.delay
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.toKotlinDuration

/**
 * breaker 설정(scope.md ⑧, D-4D2-4) — 예측(4D-1)과 임베딩(4D-2) 두 서비스가 공유한다
 * (`buildPredictionCircuitBreaker`에서 `buildMlCircuitBreaker`로 순수 rename, 설계 검토 (2)
 * 처방 4 — 공유가 된 자리에 예측 전용 이름이 남으면 다음 독자가 틀린 사실을 읽는다). 3C
 * `buildLlmCircuitBreaker`와 같은 배관 의도(minimumNumberOfCalls 를 창 크기와 맞춘다)이나,
 * `.apply{}` 블록으로 구성한다 — 3C 는 `.custom()` 뒤 메서드 체인이라 같은 값 셋을 같은
 * 순서로 연쇄 호출하면 CPD 가 구조적 중복으로 잡는다(실측, `qualityBaseline` 로컬 재현).
 */
internal fun buildMlCircuitBreaker(
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

/**
 * D-4D2-4 — 응답 타입을 타입 매개변수화했다(이전 `PredictionCallOutcome`, 응답이
 * `CalculateOptimalBidResponse`로 박혀 있었다). `internal`을 유지한다 — `adapters` 모듈
 * 안에서만 보인다((2b) 표 「신설 public 0」, 설계 검토 (2) 처방 3).
 */
internal sealed interface MlCallOutcome<out R> {
    data class Responded<out R>(
        val response: R,
    ) : MlCallOutcome<R>

    data object BreakerOpen : MlCallOutcome<Nothing>

    /**
     * verifier r2 G-4(medium) — 호출부 예산 부족은 서버 건강 신호가 아니다. `TransportFailed`
     * 와 분리된 별도 가지라 `callResilient`가 `circuitBreaker.onError`를 부르지 않는다.
     */
    data object BudgetExhausted : MlCallOutcome<Nothing>

    data class TransportFailed(
        val error: Throwable,
    ) : MlCallOutcome<Nothing>
}

/** `callWithBoundedRetry`의 내부 신호 — 예산 소진은 예외가 아니라 이 타입으로 위로 올라간다. */
private sealed interface BoundedRetryOutcome<out R> {
    data class Success<out R>(
        val response: R,
    ) : BoundedRetryOutcome<R>

    data object BudgetExhausted : BoundedRetryOutcome<Nothing>
}

/**
 * breaker + bounded retry **한 계층**(scope.md ③⑧, ADR 0005 D-11, ADR 0010 D-4·D-5) —
 * deadline은 호출부가 stub 에 이미 건 값을 쓴다(이 함수는 시간을 직접 재지 않는다 — 단,
 * `remainingBudget`으로 백오프가 예산을 넘는지는 잰다, verifier r1 F-1). 재시도는 transport
 * 예외(`isRetryableTransportStatus`)와 application failure([isRetryableFailure], D-4D2-4로
 * 호출부가 넘기는 술어 — 예측은 `resultCase == FAILURE && retryable`, 임베딩은
 * `EmbedTextResponse`의 같은 모양)를 **한 loop**(`callWithBoundedRetry`)에서 함께 보고,
 * 재시도 사이마다 `backoff`(정책 배열, attempt index 로 고른다)만큼 실제로 지연한다
 * (resilience4j `Retry`를 쓰지 않는다 — 재시도 계층이 둘이 되지 않는다). **예산 소진은
 * breaker 계수 밖이다**(verifier r2 G-4) — 서버로 나가는 호출을 한 번도 만들지 않은 채
 * 예산이 끝나는 것은 서버 건강과 무관하므로 `circuitBreaker.onError`를 부르지 않는다.
 * permit 을 얻은 뒤의 결말은 `settlePermit` 하나로 좁힌다(verifier r3 H-1, 아래 참고 —
 * **이 구조와 분기 조건은 D-4D2-4에서도 한 줄도 바뀌지 않았다**). gRPC 실패는
 * [StatusException]·[StatusRuntimeException] 둘 중 하나로만 온다 — 그 밖(coroutine 취소의
 * `CancellationException` 포함)은 여기서 잡지 않고 그대로 전파한다(`RetryRules.kt`와 같은
 * 이유, detekt `TooGenericExceptionCaught`).
 */
internal suspend fun <R> callResilient(
    circuitBreaker: CircuitBreaker,
    maxAttempts: Int,
    backoff: List<Duration>,
    remainingBudget: Duration,
    isRetryableFailure: (R) -> Boolean,
    call: suspend () -> R,
): MlCallOutcome<R> {
    if (!circuitBreaker.tryAcquirePermission()) return MlCallOutcome.BreakerOpen
    return settlePermit(circuitBreaker) {
        val deadlineAt = System.nanoTime() + remainingBudget.toNanos()
        callWithBoundedRetry(maxAttempts, backoff, deadlineAt, isRetryableFailure, call)
    }
}

/**
 * D-4D2-4 처방 3(cpd 블록 3) — 예측·임베딩 두 gateway 가 각자 반복하던 「stub 에 deadline
 * 을 걸고 이미 매핑된 proto 요청으로 [callResilient]를 부른다」 배관을 하나로 좁힌다.
 * 요청 매핑(`mapRequest`/`mapEmbedRequest`)은 도메인 요청 타입이 둘이라 호출부에 남긴다
 * — 이 함수는 이미 매핑된 [protoRequest]만 받는다. deadline 이 걸린 stub([S])을 함께
 * 돌려주는 이유는 두 gateway 모두 release 대조 단계(`fetchPromoted`)에서 같은 stub 으로
 * 추가 RPC(`GetModelMetadata`/`GetEmbeddingMetadata`)를 부르기 때문이다. `settlePermit`의
 * try/finally 와 breaker 분기(`callResilient`)는 한 줄도 건드리지 않는다 — 이 함수는 그
 * 앞의 매핑 없는 배관만 묶는다.
 */
internal suspend fun <S : AbstractStub<S>, Req, Resp> callMlRpc(
    stub: S,
    protoRequest: Req,
    circuitBreaker: CircuitBreaker,
    policy: MlCallPolicyData,
    remaining: Duration,
    isRetryableFailure: (Resp) -> Boolean,
    invoke: suspend (S, Req) -> Resp,
): Pair<S, MlCallOutcome<Resp>> {
    val stubWithDeadline = stub.withDeadlineAfter(remaining.toMillis(), TimeUnit.MILLISECONDS)
    val outcome =
        callResilient(circuitBreaker, policy.maxAttempts, policy.backoff, remaining, isRetryableFailure) {
            invoke(stubWithDeadline, protoRequest)
        }
    return stubWithDeadline to outcome
}

/**
 * verifier r3 H-1(high) — permit 을 이미 얻은 뒤의 **모든** 경로를 이 함수 하나로 좁혀
 * `try`/`finally`로 결말을 구조로 강제한다. 이전 결함: `callResilient`가 permit 을 얻은
 * 뒤 `BudgetExhausted` 가지에서 `onSuccess`·`onError`·`releasePermission` 중 아무것도
 * 부르지 않고 그냥 반환했다 — CLOSED 에서는 무해했으나(permit 이 무제한) HALF_OPEN 은
 * `permittedNumberOfCallsInHalfOpenState`(기본 10)이 유한해, 예산 소진 호출이 그 permit 을
 * 계속 먹기만 하고 반납하지 않아 breaker 가 회복 불가능한 HALF_OPEN 에 영구히 갇혔다(실측:
 * permit 수보다 많은 예산 소진 호출 뒤 넉넉한 예산 호출도 전부 `CircuitOpen`, 서버 호출 0).
 *
 * 처방은 「가지마다 `releasePermission()` 한 줄」이 아니다 — 그러면 다음에 새 가지가 늘 때
 * 같은 실수가 재발한다. 이 함수를 거치는 한 [operation]의 결과가 무엇이든(그리고 잡히지
 * 않는 예외가 나가더라도) `finally`가 안전망으로 동작해 셋(`onSuccess`·`onError`·
 * `releasePermission`) 중 정확히 하나가 항상 불린다 — `settled` 플래그가 그 중 하나가 이미
 * 불렸음을 표시하고, `finally`는 그렇지 않은 경우에만 `releasePermission()`으로 닫는다.
 * `BudgetExhausted`는 서버를 향한 호출이 실패했다는 신호가 아니므로(백오프를 감당 못 해
 * 재시도를 포기한 것뿐) `releasePermission()`만 부른다 — `onError`가 아니다(verifier r2
 * G-4 의 의미를 그대로 지킨다, CLOSED 에서 예산 소진이 breaker 건강에 영향을 주면 안 된다).
 */
private suspend fun <R> settlePermit(
    circuitBreaker: CircuitBreaker,
    operation: suspend () -> BoundedRetryOutcome<R>,
): MlCallOutcome<R> {
    val start = System.nanoTime()
    var settled = false
    return try {
        when (val result = operation()) {
            is BoundedRetryOutcome.Success -> {
                circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS)
                settled = true
                MlCallOutcome.Responded(result.response)
            }

            BoundedRetryOutcome.BudgetExhausted -> {
                circuitBreaker.releasePermission()
                settled = true
                MlCallOutcome.BudgetExhausted
            }
        }
    } catch (statusError: StatusException) {
        circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, statusError)
        settled = true
        MlCallOutcome.TransportFailed(statusError)
    } catch (statusRuntimeError: StatusRuntimeException) {
        circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, statusRuntimeError)
        settled = true
        MlCallOutcome.TransportFailed(statusRuntimeError)
    } finally {
        if (!settled) circuitBreaker.releasePermission()
    }
}

private suspend fun <R> callWithBoundedRetry(
    maxAttempts: Int,
    backoff: List<Duration>,
    deadlineAt: Long,
    isRetryableFailure: (R) -> Boolean,
    call: suspend () -> R,
): BoundedRetryOutcome<R> {
    for (attempt in 0 until maxAttempts) {
        val isLastAttempt = attempt == maxAttempts - 1
        val outcome = attemptOnce(call, isRetryableFailure, isLastAttempt)
        if (outcome != null) return BoundedRetryOutcome.Success(outcome)
        if (!isLastAttempt) {
            val canContinue = awaitBackoffOrSignalExhausted(attempt, backoff, deadlineAt)
            if (!canContinue) return BoundedRetryOutcome.BudgetExhausted
        }
    }
    error("retry loop 이 결과 없이 끝났다")
}

/** 재시도할 값이면 `null`을 낸다(호출부가 다음 attempt 로 넘어간다) — 상한이면 그대로 던지거나 낸다. */
private suspend fun <R> attemptOnce(
    call: suspend () -> R,
    isRetryableFailure: (R) -> Boolean,
    isLastAttempt: Boolean,
): R? =
    try {
        val response = call()
        if (!isRetryableFailure(response) || isLastAttempt) response else null
    } catch (statusError: StatusException) {
        if (!isRetryableTransportStatus(statusError.status.code) || isLastAttempt) throw statusError
        null
    } catch (statusRuntimeError: StatusRuntimeException) {
        if (!isRetryableTransportStatus(statusRuntimeError.status.code) || isLastAttempt) throw statusRuntimeError
        null
    }

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
