package bidvector.adapters.ml

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException

/*
 * M4/4D-1(D-4D-3) — M2/2D `ContractRetryRules`(adapters/src/test/kotlin/bidvector/adapters/
 * contract) 를 main 으로 승격한 실물. ADR 0010 D-4 「재시도는 멱등성과 상태로만 결정된다」의
 * 순수 판정 함수다. `adapters/src/test/kotlin/bidvector/adapters/contract/ContractRetryRules.kt`
 * 는 이제 이 파일을 위임만 한다(2D 소비자 test 는 그대로 통과해야 한다, scope.md S-3).
 * 실제 운영 재시도 정책(횟수·백오프)의 정본은 `MlCallPolicyData`(`MlCallPolicyData.kt`)다.
 *
 * gRPC 실패는 항상 [StatusException] 또는 [StatusRuntimeException] 둘 중 하나로 온다(계약
 * 성질) — 그래서 여기서는 그 둘만 개별로 잡는다. 그 밖(특히 coroutine 취소로 오는
 * `kotlinx.coroutines.CancellationException`)은 이 함수가 다루지 않고 그대로 전파한다
 * (v2-지침서.md §5 「detekt TooGenericExceptionCaught」 — `catch (e: Throwable)`는 쓰지 않는다).
 */

/** ADR 0010 D-4 — 재시도 가능한 transport status. */
internal fun isRetryableTransportStatus(code: Status.Code): Boolean =
    code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.RESOURCE_EXHAUSTED

/** ADR 0010 D-3 — application failure 는 `retryable` 필드가 그대로 정한다(계약 소유, 추측 없음). */
internal fun isRetryableApplicationFailure(retryable: Boolean): Boolean = retryable

/**
 * transport 재시도 드라이버 — retryable 상태를 만나면 `maxAttempts`(정책 표본) 안에서
 * 재호출하고, 재시도 불가 상태나 상한 도달이면 마지막 예외를 던진다. 2D 소비자 test
 * (`ContractDeadlineCancellationRetryTest`)가 이 함수를 직접 호출한다 — 실 gateway 경로의
 * 재시도(application failure 도 함께 보는 `callWithBoundedRetry`, `ResilientPredictionCall.kt`)
 * 와는 별개의, 더 좁은 순수 드라이버다.
 */
internal suspend fun <T> callWithTransportRetry(
    maxAttempts: Int,
    call: suspend () -> T,
): T {
    repeat(maxAttempts) { attempt ->
        val isLastAttempt = attempt == maxAttempts - 1
        try {
            return call()
        } catch (statusError: StatusException) {
            rethrowIfExhausted(statusError, statusError.status, isLastAttempt)
        } catch (statusRuntimeError: StatusRuntimeException) {
            rethrowIfExhausted(statusRuntimeError, statusRuntimeError.status, isLastAttempt)
        }
    }
    error("retry loop 이 결과 없이 끝났다")
}

private fun rethrowIfExhausted(
    error: Throwable,
    status: Status,
    isLastAttempt: Boolean,
) {
    val canRetry = isRetryableTransportStatus(status.code) && !isLastAttempt
    if (!canRetry) throw error
}

internal fun grpcStatusOf(error: Throwable): Status? =
    when (error) {
        is StatusException -> error.status
        is StatusRuntimeException -> error.status
        else -> null
    }
