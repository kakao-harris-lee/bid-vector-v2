package bidvector.workflow.access

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Duration

/**
 * 브라우저 접근 정책(M7/7D 산출물 3 「값은 정책으로 적는다」) — 세션 창 둘·실패 상한·잠금
 * 창·해시 비용. **매직 넘버를 코드에 두지 않는다**(v2-지침서 §5): 값은 이 정책 데이터에만
 * 있고 정본은 아래 [BROWSER_ACCESS_POLICY] 의 `source` 가 가리키는 문서다.
 *
 * 생성 불변식은 **값의 승인 여부와 다른 축**이다 — 상한·순서를 어기는 조합이 조립 시점에
 * 서지 못하게 한다(`EditSessionPolicyData` 와 같은 관례).
 */
data class BrowserAccessPolicyData(
    /** 마지막 사용 이후 이만큼 쓰이지 않으면 세션이 끝난다. */
    val idleWindow: Duration,
    /** 로그인 시각으로부터 이만큼 지나면 쓰이고 있어도 끝난다. */
    val absoluteWindow: Duration,
    /** 연속 실패가 이 수에 닿으면 잠근다. */
    val maxFailedAttempts: Int,
    /** 잠긴 뒤 이만큼은 올바른 값에도 거부한다. */
    val lockWindow: Duration,
    /** bcrypt cost(2^cost 회) — 느리게 만드는 값 자체가 방어다. */
    val hashCost: Int,
) {
    init {
        require(idleWindow.isPositive()) { "idleWindow 는 양수여야 한다: $idleWindow" }
        require(absoluteWindow.isPositive()) { "absoluteWindow 는 양수여야 한다: $absoluteWindow" }
        require(idleWindow <= absoluteWindow) {
            "유휴 창은 절대 창을 넘을 수 없다: idle=$idleWindow absolute=$absoluteWindow"
        }
        require(absoluteWindow <= MAX_ABSOLUTE_WINDOW) {
            "absoluteWindow 는 $MAX_ABSOLUTE_WINDOW 를 넘을 수 없다: $absoluteWindow"
        }
        require(maxFailedAttempts in MIN_ATTEMPTS..MAX_ATTEMPTS) {
            "maxFailedAttempts 는 $MIN_ATTEMPTS..$MAX_ATTEMPTS 안이어야 한다: $maxFailedAttempts"
        }
        require(lockWindow.isPositive()) { "lockWindow 는 양수여야 한다: $lockWindow" }
        require(hashCost in MIN_HASH_COST..MAX_HASH_COST) {
            "hashCost 는 $MIN_HASH_COST..$MAX_HASH_COST 안이어야 한다: $hashCost"
        }
    }

    private companion object {
        /** 하루를 넘는 세션은 「절대 만료」라는 말이 무의미해진다. */
        val MAX_ABSOLUTE_WINDOW: Duration = Duration.ofHours(24)

        /** 1 이면 한 번의 오타가 잠금이다. 상한을 두는 것은 사실상 무제한을 막기 위해서다. */
        const val MIN_ATTEMPTS = 3
        const val MAX_ATTEMPTS = 20

        /** 10 미만은 2026년 장비에서 너무 빠르고, 16 초과는 요청 하나가 초 단위로 길어진다. */
        const val MIN_HASH_COST = 10
        const val MAX_HASH_COST = 16
    }
}

/**
 * **정본은 코드가 아니라 문서다**(`EDIT_SESSION_POLICY`·`KONEPS_COLLECTION_POLICY` 관례) —
 * 값을 바꾸려면 `source` 의 문서를 먼저 갱신한다.
 */
val BROWSER_ACCESS_POLICY: EffectiveDatedPolicy<BrowserAccessPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m7/7d/policy-values.md §1 — M7/7D 세션·잠금·해시 비용",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    BrowserAccessPolicyData(
                        idleWindow = Duration.ofMinutes(30),
                        absoluteWindow = Duration.ofHours(12),
                        maxFailedAttempts = 5,
                        lockWindow = Duration.ofMinutes(15),
                        hashCost = 12,
                    ),
            ),
    )
