package bidvector.adapters.contract

import io.grpc.Status

/*
 * M2/2D ⑥ — ADR 0010 D-4 「재시도는 멱등성과 상태로만 결정된다」의 순수 판정 함수.
 * 실제 운영 재시도 정책(횟수·백오프)의 정본은 M4 4D 의 `adapters` versioned 정책 데이터다 —
 * 여기서는 규칙만 고정하고, 표본 상한은 `contract-policy.properties`의
 * `retry.sample.max-attempts`(test 전용 값)를 쓴다.
 */

/** ADR 0010 D-4 — 재시도 가능한 transport status. */
internal fun isRetryableTransportStatus(code: Status.Code): Boolean =
    code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.RESOURCE_EXHAUSTED

/** ADR 0010 D-3 — application failure 는 `retryable` 필드가 그대로 정한다(계약 소유, 추측 없음). */
internal fun isRetryableApplicationFailure(retryable: Boolean): Boolean = retryable

/**
 * transport 재시도 드라이버 — retryable 상태를 만나면 `maxAttempts`(정책 표본) 안에서
 * 재호출하고, 재시도 불가 상태나 상한 도달이면 마지막 예외를 던진다.
 */
internal suspend fun <T> callWithTransportRetry(
    maxAttempts: Int,
    call: suspend () -> T,
): T {
    var lastError: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return call()
        } catch (error: Throwable) {
            val status = grpcStatusOf(error)
            val canRetry = status != null && isRetryableTransportStatus(status.code) && attempt < maxAttempts - 1
            if (!canRetry) throw error
            lastError = error
        }
    }
    throw lastError ?: error("retry loop 이 결과 없이 끝났다")
}

private fun grpcStatusOf(error: Throwable): Status? =
    when (error) {
        is io.grpc.StatusException -> error.status
        is io.grpc.StatusRuntimeException -> error.status
        else -> null
    }
