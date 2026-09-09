package bidvector.adapters.ml

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.time.Duration

/** scope.md ⑧, 우회 (9) — 정책 리터럴 우회는 이 생성 불변식이 막는다. */
class MlCallPolicyDataTest {
    @Test
    fun `deadlineCeiling 은 0보다 커야 한다`() {
        shouldThrow<IllegalArgumentException> { testMlCallPolicy(deadlineCeiling = Duration.ZERO) }
    }

    @Test
    fun `maxAttempts 는 1 이상이어야 한다`() {
        shouldThrow<IllegalArgumentException> { testMlCallPolicy(maxAttempts = 0) }
    }

    @Test
    fun `backoff 는 최소 한 항목이 필요하고 전부 0보다 커야 한다`() {
        shouldThrow<IllegalArgumentException> {
            MlCallPolicyData(
                deadlineCeiling = Duration.ofSeconds(1),
                maxAttempts = 3,
                backoff = emptyList(),
                breakerFailureRateThresholdPercent = 50,
                breakerSlidingWindowSize = 4,
                breakerWaitDurationInOpenState = Duration.ofMillis(200),
                featureSchemaVersion = "v",
            )
        }
        shouldThrow<IllegalArgumentException> {
            MlCallPolicyData(
                deadlineCeiling = Duration.ofSeconds(1),
                maxAttempts = 3,
                backoff = listOf(Duration.ZERO),
                breakerFailureRateThresholdPercent = 50,
                breakerSlidingWindowSize = 4,
                breakerWaitDurationInOpenState = Duration.ofMillis(200),
                featureSchemaVersion = "v",
            )
        }
    }

    @Test
    fun `breakerFailureRateThresholdPercent 는 1~100 이어야 한다`() {
        shouldThrow<IllegalArgumentException> { testMlCallPolicy(breakerFailureRateThresholdPercent = 0) }
        shouldThrow<IllegalArgumentException> { testMlCallPolicy(breakerFailureRateThresholdPercent = 101) }
    }

    @Test
    fun `breakerSlidingWindowSize 는 0보다 커야 한다`() {
        shouldThrow<IllegalArgumentException> { testMlCallPolicy(breakerSlidingWindowSize = 0) }
    }

    @Test
    fun `featureSchemaVersion 은 빈 문자열일 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            MlCallPolicyData(
                deadlineCeiling = Duration.ofSeconds(1),
                maxAttempts = 3,
                backoff = listOf(Duration.ofMillis(1)),
                breakerFailureRateThresholdPercent = 50,
                breakerSlidingWindowSize = 4,
                breakerWaitDurationInOpenState = Duration.ofMillis(200),
                featureSchemaVersion = "",
            )
        }
    }

    @Test
    fun `ML_CALL_POLICY 운영 인스턴스는 정상 생성된다`() {
        ML_CALL_POLICY.entries.shouldNotBeEmpty()
    }
}
