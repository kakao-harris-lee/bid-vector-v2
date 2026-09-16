package bidvector.adapters.ml

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

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

    // ---- verifier r1 F-4(medium) — 출하 인스턴스 값 자체를 재는 단언(4E M-2 관례) ----
    // policy-values.md D-4D-7 착수 placeholder 값이 조용히 바뀌어도 이 test 가 잡는다.

    @Test
    fun `ML_CALL_POLICY 값은 policy-values-md D-4D-7 착수 placeholder 와 일치한다`() {
        val resolution = ML_CALL_POLICY.resolve(LocalDate.now())
        resolution.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()
        val data = resolution.value

        data.deadlineCeiling shouldBe Duration.ofSeconds(5)
        data.maxAttempts shouldBe 3
        data.backoff shouldBe listOf(Duration.ofMillis(200), Duration.ofMillis(800))
        data.breakerFailureRateThresholdPercent shouldBe 50
        data.breakerSlidingWindowSize shouldBe 10
        data.breakerWaitDurationInOpenState shouldBe Duration.ofSeconds(30)
        data.featureSchemaVersion shouldBe "award-rate-features-v2"
    }

    // ---- D-5F2-2 — Python SUPPORTED_FEATURE_SCHEMAS 와의 동일성은 문서 대조로 잠근다 ----
    // (교차 언어 실 대조는 6C `OPEN-5E2-CROSSLANG-REAL-SERVER`). 리터럴은 `ML_CALL_POLICY`
    // 한 자리(MlCallPolicyData.kt)에만 두고, 이 test 는 그 인스턴스를 resolve 해 대조한다
    // (정책 데이터 두 자리 금지).

    @Test
    fun `featureSchemaVersion 은 5B가 신설한 award-rate-features-v2 와 같다(D-5B-1, Python SUPPORTED_FEATURE_SCHEMAS 정본)`() {
        val resolution = ML_CALL_POLICY.resolve(LocalDate.now())
        resolution.shouldBeInstanceOf<Resolution.Resolved<MlCallPolicyData>>()

        resolution.value.featureSchemaVersion shouldBe "award-rate-features-v2"
    }
}
