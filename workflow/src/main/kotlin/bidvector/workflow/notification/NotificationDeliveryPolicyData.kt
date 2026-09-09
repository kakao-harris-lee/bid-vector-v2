package bidvector.workflow.notification

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom

/**
 * 배달 경로 정책 슬롯(scope.md ⑦) — [environmentModes]는 [RuntimeEnvironment] 전 값을
 * 덮는 전사상이다(빠진 환경 = 생성 실패, 「모르는 환경은 보낸다」 경로가 없다, 우회 (6)).
 * [maskedSuffixLength]는 [MaskedTarget.mask]가 읽는 정책 값 — 리터럴을 코드에 박지 않는다.
 *
 * `NOTIFICATION_DELIVERY_POLICY`가 담는 값(환경→모드 매핑·`maskedSuffixLength=4`)은
 * 사용자 승인 2026-09-09로 확정됐다(`policy-values.md` §1·§2).
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
 * **사용자 승인 2026-09-09.** 착수 시(2026-09-09)에는 구조 검증용 placeholder였으나
 * (legacy `NON_DELIVERING_ENVIRONMENTS = {"test"}` 하나뿐이던 것과 달리 이 매핑은
 * [RuntimeEnvironment] 전 값을 강제로 덮는다, 조사 (c-6)), slice 4E 종결 승인과 함께
 * 이 값 자체가 승인됐다(`maskedSuffixLength=4`는 legacy 마스킹 함수의 끝 4자 관행 계승,
 * `legacy-behavior` 층). 정본은 `reports/evidence/m4/4e/policy-values.md §1·§2` — 값을
 * 바꾸려면 그 문서를 먼저 갱신한다(정본이 코드가 아니라 문서다, 3A `KONEPS_COLLECTION_POLICY`
 * 관례).
 */
val NOTIFICATION_DELIVERY_POLICY: EffectiveDatedPolicy<NotificationDeliveryPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m4/4e/policy-values.md §1·§2 — 사용자 승인 2026-09-09",
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
