package bidvector.workflow.notification

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom

/**
 * 배달 경로 정책 슬롯(scope.md ⑦) — [environmentModes]는 [RuntimeEnvironment] 전 값을
 * 덮는 전사상이다(빠진 환경 = 생성 실패, 「모르는 환경은 보낸다」 경로가 없다, 우회 (6)).
 * [maskedSuffixLength]는 [MaskedTarget.mask]가 읽는 정책 값 — 리터럴을 코드에 박지 않는다.
 *
 * **값은 착수 placeholder다.** 정본은 `reports/evidence/m4/4e/policy-values.md §1` —
 * 승인 대기(`OPEN-4E-POLICY-VALUES`).
 */
data class NotificationDeliveryPolicyData(
    val environmentModes: Map<RuntimeEnvironment, DeliveryMode>,
    val maskedSuffixLength: Int,
) {
    init {
        require(environmentModes.keys == RuntimeEnvironment.entries.toSet()) {
            "environmentModes는 RuntimeEnvironment 전 값을 덮어야 한다: ${environmentModes.keys}"
        }
        require(maskedSuffixLength >= 1) {
            "maskedSuffixLength는 1 이상이어야 한다: $maskedSuffixLength"
        }
    }
}

/**
 * 착수 placeholder(승인 대기, `OPEN-4E-POLICY-VALUES`) — 추천값은
 * `reports/evidence/m4/4e/policy-values.md §1`(legacy 마스킹 함수의 끝 4자 관행 계승,
 * `legacy-behavior` 층). `NON_DELIVERING_ENVIRONMENTS = {"test"}` 하나뿐이던
 * legacy 와 달리 이 매핑은 [RuntimeEnvironment] 전 값을 강제로 덮는다(조사 (c-6)).
 */
val NOTIFICATION_DELIVERY_POLICY: EffectiveDatedPolicy<NotificationDeliveryPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m4/4e/policy-values.md §1 — 착수 placeholder, 승인 대기(OPEN-4E-POLICY-VALUES)",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    NotificationDeliveryPolicyData(
                        environmentModes =
                            mapOf(
                                RuntimeEnvironment.Production to DeliveryMode.Live,
                                RuntimeEnvironment.Staging to DeliveryMode.DryRun,
                                RuntimeEnvironment.Development to DeliveryMode.DryRun,
                                RuntimeEnvironment.Test to DeliveryMode.Blocked,
                            ),
                        maskedSuffixLength = 4,
                    ),
            ),
    )
