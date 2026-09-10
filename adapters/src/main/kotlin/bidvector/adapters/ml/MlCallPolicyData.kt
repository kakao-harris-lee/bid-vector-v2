package bidvector.adapters.ml

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Duration

/**
 * ML 호출 정책 값(scope.md ⑧, ADR 0010 D-1) — deadline·재시도·backoff·breaker 임계는
 * `adapters`의 versioned 정책 데이터다. main 리터럴 0, 운영 인스턴스는 [ML_CALL_POLICY]
 * 하나다(3C `ExtractionPolicyData` 관례). `featureSchemaVersion`은 이 어댑터가 보내는
 * `FeatureInputs` 형태의 schema 식별자(D-7 「다른 축」) — 값이 아니라 참조라 정책 데이터에
 * 둔다(`deadline_policy_version`과 같은 이유).
 */
data class MlCallPolicyData(
    val deadlineCeiling: Duration,
    val maxAttempts: Int,
    val backoff: List<Duration>,
    val breakerFailureRateThresholdPercent: Int,
    val breakerSlidingWindowSize: Int,
    val breakerWaitDurationInOpenState: Duration,
    val featureSchemaVersion: String,
) {
    init {
        require(!deadlineCeiling.isNegative && !deadlineCeiling.isZero) {
            "deadlineCeiling은 0보다 커야 한다: $deadlineCeiling"
        }
        require(maxAttempts >= 1) { "maxAttempts는 1 이상이어야 한다: $maxAttempts" }
        require(backoff.isNotEmpty()) { "backoff는 최소 한 항목이 필요하다" }
        require(backoff.all { !it.isNegative && !it.isZero }) { "backoff 항목은 전부 0보다 커야 한다: $backoff" }
        require(breakerFailureRateThresholdPercent in MIN_PERCENT..MAX_PERCENT) {
            "breakerFailureRateThresholdPercent는 $MIN_PERCENT~$MAX_PERCENT 이어야 한다: $breakerFailureRateThresholdPercent"
        }
        require(breakerSlidingWindowSize > 0) { "breakerSlidingWindowSize는 0보다 커야 한다: $breakerSlidingWindowSize" }
        require(featureSchemaVersion.isNotBlank()) { "featureSchemaVersion은 빈 문자열일 수 없다" }
    }

    private companion object {
        const val MIN_PERCENT = 1
        const val MAX_PERCENT = 100
    }
}

/**
 * 운영 정책 인스턴스(D-4D-7) — **사용자 승인 2026-09-10으로 확정됐다**(`policy-values.md`
 * §1~§3). 착수 시(2026-09-10)에는 구조 검증용 placeholder였으나(근거는 실측이 아니라 3C
 * `EXTRACTION_POLICY`와 2D `retry.sample.max-attempts=3`의 보수적 상한, ADR 0010 D-1
 * 「보수적 상한 + 측정 의무」), slice 4D-1 종결 승인과 함께 이 값 자체가 승인됐다
 * (`OPEN-4D-POLICY-VALUES` 종결). 정본은 `reports/evidence/m4/4d/policy-values.md §1~§3`
 * — 값을 바꾸려면 그 문서를 먼저 갱신한다(정본이 코드가 아니라 문서다, 3A
 * `KONEPS_COLLECTION_POLICY`·4E `NOTIFICATION_DELIVERY_POLICY` 관례). **실측 갱신
 * 경로는 `OPEN-M2-DEADLINE-VALUES`(M5 5E)로 활성 유지** — 이 승인은 값의 존재를
 * 확정했을 뿐 옳음을 실측한 것이 아니다. test 는 이 인스턴스를 쓰지 않는다 — 좁은 값의
 * 별도 인스턴스(`adapters/src/test`)를 쓴다(3B `testKonepsHttpPolicy` 관례).
 */
val ML_CALL_POLICY: EffectiveDatedPolicy<MlCallPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m4/4d/policy-values.md §1·§2·§3 — 사용자 승인 2026-09-10",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    MlCallPolicyData(
                        deadlineCeiling = Duration.ofSeconds(5),
                        maxAttempts = 3,
                        backoff = listOf(Duration.ofMillis(200), Duration.ofMillis(800)),
                        breakerFailureRateThresholdPercent = 50,
                        breakerSlidingWindowSize = 10,
                        breakerWaitDurationInOpenState = Duration.ofSeconds(30),
                        featureSchemaVersion = "bidvector.ml.v1",
                    ),
            ),
    )
