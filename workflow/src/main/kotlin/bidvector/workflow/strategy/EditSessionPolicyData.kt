package bidvector.workflow.strategy

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Duration

/**
 * 편집 세션 timeout 정책(D-4A-3) — 값은 정책 데이터 슬롯이고 legacy 값이 없다(조사
 * 결과 — TTL 슬롯 0건). 상한(24시간)은 우회 (4) 차단용 생성 불변식이고 값 자체가 승인
 * 대상은 아니다 — `EDIT_SESSION_POLICY` 가 담는 timeout 값(15분)은 사용자 승인
 * 2026-09-09 로 확정됐다(`policy-values.md` §1).
 */
data class EditSessionPolicyData(
    val timeoutWindow: Duration,
) {
    init {
        require(!timeoutWindow.isZero && !timeoutWindow.isNegative) {
            "timeoutWindow는 양수여야 한다: $timeoutWindow"
        }
        require(timeoutWindow <= MAX_TIMEOUT_WINDOW) {
            "timeoutWindow는 $MAX_TIMEOUT_WINDOW 를 넘을 수 없다: $timeoutWindow"
        }
    }

    private companion object {
        val MAX_TIMEOUT_WINDOW: Duration = Duration.ofHours(24)
    }
}

/**
 * **사용자 승인 2026-09-09 — 15분.** 착수 시(2026-09-08)에는 구조 검증용 placeholder였으나
 * (legacy 에 대응 TTL 슬롯 0건이라 재활용할 값이 없었다), slice 4A 종결 승인과 함께 이 값
 * 자체가 승인됐다. 정본은 `reports/evidence/m4/4a/policy-values.md` §1 — 값을 바꾸려면
 * 그 문서를 먼저 갱신한다(정본이 코드가 아니라 문서다, 3A `KONEPS_COLLECTION_POLICY` 관례).
 */
val EDIT_SESSION_POLICY: EffectiveDatedPolicy<EditSessionPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m4/4a/policy-values.md §1 — 사용자 승인 2026-09-09, D-4A-3 timeout=15분",
        entries = listOf(EffectiveFrom.Initial to EditSessionPolicyData(Duration.ofMinutes(15))),
    )
