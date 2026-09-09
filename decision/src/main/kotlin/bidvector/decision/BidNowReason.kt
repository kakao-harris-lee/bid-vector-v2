package bidvector.decision

import java.math.BigDecimal

/**
 * `BidNow`의 사유(scope.md ⑤, `OPEN-STR-03`) — legacy 는 정상 승격과 force-bid 우회가
 * **같은 문장**을 냈다(조사 §4 실측, N-4). 타입을 둘로 갈라 우회가 결과에서 안 보이는
 * 것을 막는다. 각 사유는 **쓴 임계와 실제 값을 함께 나른다**(DEC-12 A10, 조사 §2.4 —
 * 임계가 두 출처에 흩어져 재현 불가하던 형태를 닫는다).
 *
 * 생성자는 전부 `internal`이다(설계 검토 (2)) — [VerdictLadder.judge]를 지나지 않고 이
 * 사유를 지어내면 하류가 「우회 없었다」·「정상 승격이었다」를 거짓으로 믿는다.
 */
sealed interface BidNowReason {
    /** priority 가 bidNowThreshold 이상이다(조사 §2.2 분기 2, 정상 승격 경로). */
    @ConsistentCopyVisibility
    data class PriorityAboveBidNowThreshold internal constructor(
        val priority: BigDecimal,
        val threshold: BigDecimal,
    ) : BidNowReason

    /**
     * probability·matched 가 각자의 임계 이상이면 priority 와 무관하게 승격한다(조사
     * §5.1 실측 — `elif ... or (probability >= P and matched >= M)`). **이 타입 자체가
     * `OPEN-STR-03`의 답이다** — 우회의 존재가 이제 결과에서 보인다.
     */
    @ConsistentCopyVisibility
    data class ForceBidOverride internal constructor(
        val probability: BigDecimal,
        val matched: BigDecimal,
        val probabilityThreshold: BigDecimal,
        val matchedThreshold: BigDecimal,
    ) : BidNowReason
}
