package bidvector.adapters.contract

import io.grpc.Status
import bidvector.adapters.ml.callWithTransportRetry as mlCallWithTransportRetry
import bidvector.adapters.ml.isRetryableApplicationFailure as mlIsRetryableApplicationFailure
import bidvector.adapters.ml.isRetryableTransportStatus as mlIsRetryableTransportStatus

/*
 * M4/4D-1(D-4D-3) — 이 규칙의 실물은 `adapters/src/main/kotlin/bidvector/adapters/ml/
 * RetryRules.kt`로 승격됐다(ADR 0010 D-4의 순수 판정 함수 — 실제 운영 재시도 정책의 정본은
 * `bidvector.adapters.ml.MlCallPolicyData`). 이 파일은 2A~2D 소비자 test
 * (`ContractDeadlineCancellationRetryTest` 등)가 같은 이름·시그니처로 계속 통과하도록
 * **위임만** 한다(CPD 0, scope.md S-3) — 판정 로직을 이 파일에 다시 적지 않는다.
 */

/** ADR 0010 D-4 — 재시도 가능한 transport status. */
internal fun isRetryableTransportStatus(code: Status.Code): Boolean = mlIsRetryableTransportStatus(code)

/** ADR 0010 D-3 — application failure 는 `retryable` 필드가 그대로 정한다. */
internal fun isRetryableApplicationFailure(retryable: Boolean): Boolean = mlIsRetryableApplicationFailure(retryable)

/** transport 재시도 드라이버 — 실물은 `bidvector.adapters.ml.callWithTransportRetry`. */
internal suspend fun <T> callWithTransportRetry(
    maxAttempts: Int,
    call: suspend () -> T,
): T = mlCallWithTransportRetry(maxAttempts, call)
