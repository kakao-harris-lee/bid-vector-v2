package bidvector.adapters.ml

import java.time.Duration

/**
 * M4/4D-2(cpd 블록 2) — 예측(`ML_CALL_POLICY`)과 임베딩(`EMBEDDING_CALL_POLICY`)이 착수
 * 시 같은 숫자를 쓰는 것은 우연이 아니라 **둘 다 5E 실측 전 placeholder**이기 때문이다
 * (`OPEN-M2-DEADLINE-VALUES`·`OPEN-4D2-POLICY-VALUES`). `ML_CALL_POLICY`가 2026-09-10에
 * 사용자 승인을 받은 것은 「지금은 이 값을 쓴다」는 확정이지 옳음의 실측이 아니다
 * (`MlCallPolicyData.kt` KDoc). 값이 우연히 같은 동안은 이 공용 팩토리로 리터럴 중복을
 * 닫되, **정책 레지스트리는 여전히 둘**이다(D-4D2-2, 값 슬롯 분리) — 어느 한쪽이 먼저
 * 실측되어 다른 값이 되면 그 slice 가 이 팩토리 호출을 그치고 자기 값을 인라인한다.
 */
internal fun placeholderMlCallPolicy(featureSchemaVersion: String): MlCallPolicyData =
    MlCallPolicyData(
        deadlineCeiling = Duration.ofSeconds(PLACEHOLDER_DEADLINE_CEILING_SECONDS),
        maxAttempts = PLACEHOLDER_MAX_ATTEMPTS,
        backoff =
            listOf(
                Duration.ofMillis(PLACEHOLDER_BACKOFF_FIRST_MILLIS),
                Duration.ofMillis(PLACEHOLDER_BACKOFF_SECOND_MILLIS),
            ),
        breakerFailureRateThresholdPercent = PLACEHOLDER_BREAKER_FAILURE_RATE_PERCENT,
        breakerSlidingWindowSize = PLACEHOLDER_BREAKER_SLIDING_WINDOW_SIZE,
        breakerWaitDurationInOpenState = Duration.ofSeconds(PLACEHOLDER_BREAKER_WAIT_SECONDS),
        featureSchemaVersion = featureSchemaVersion,
    )

// detekt MagicNumber — placeholder 리터럴 자체가 D-4D2-2·OPEN-4D2-POLICY-VALUES 의 근거라
// 이름을 붙인다(위 KDoc과 값이 같은 이유를 그대로 옮긴 것뿐, `MlCallPolicyData.kt`의
// MIN_PERCENT/MAX_PERCENT 관례).
private const val PLACEHOLDER_DEADLINE_CEILING_SECONDS = 5L
private const val PLACEHOLDER_MAX_ATTEMPTS = 3
private const val PLACEHOLDER_BACKOFF_FIRST_MILLIS = 200L
private const val PLACEHOLDER_BACKOFF_SECOND_MILLIS = 800L
private const val PLACEHOLDER_BREAKER_FAILURE_RATE_PERCENT = 50
private const val PLACEHOLDER_BREAKER_SLIDING_WINDOW_SIZE = 10
private const val PLACEHOLDER_BREAKER_WAIT_SECONDS = 30L
