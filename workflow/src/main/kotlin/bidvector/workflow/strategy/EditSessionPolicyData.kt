package bidvector.workflow.strategy

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Duration

/**
 * 편집 세션 timeout 정책(D-4A-3) — 값은 정책 데이터 슬롯이고 legacy 값이 없다(조사
 * 결과 — TTL 슬롯 0건). 상한은 우회 (4) 차단(생성 불변식) — 값 자체는 운영자 승인 대상.
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
 * placeholder 값(`STRATEGY_POLICY` 관례와 동형) — 4A 는 구조만 정한다. 값 자체는 운영자
 * 승인 대상이라 이 상수를 근거로 삼지 않는다.
 */
val EDIT_SESSION_POLICY: EffectiveDatedPolicy<EditSessionPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m4/4a/scope.md D-4A-3 — timeout 값 미확정, 구조만(2026-09-08)",
        entries = listOf(EffectiveFrom.Initial to EditSessionPolicyData(Duration.ofMinutes(15))),
    )
