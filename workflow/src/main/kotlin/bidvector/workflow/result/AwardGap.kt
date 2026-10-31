package bidvector.workflow.result

import bidvector.bidding.SubmittedBidAmount
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.export

/**
 * 내 투찰가와 **확정** 낙찰가의 차이.
 *
 * 「차이가 없다」를 `0` 으로 나르지 않는다 — 확정 낙찰가가 없는 것([AwardAbsent])과 같은
 * 금액을 낸 것(`Computed(0)`)은 다른 사실이다.
 */
sealed interface AwardGap {
    /** 확정 낙찰가가 없다. **잠정 1위 금액으로 채우지 않는다**(D-7C5B-2). */
    data object AwardAbsent : AwardGap

    /**
     * 원 단위 차이(내 금액 − 확정 낙찰가). 음수는 내가 더 낮게 냈다는 뜻이다.
     *
     * **과세 축을 단언하지 않는다.** 확정 낙찰가의 과세 축은 언제나 `UNKNOWN` 이고
     * (`AwardAmount.vatTreatment` 가 상수다 — 상류가 선언하지 않는다) 투찰가는 운영자가
     * 선언한 축이라, 둘이 같은 축이라는 근거가 없다. 그래서 이 값은 「같은 축의 차이」가
     * 아니라 **「표기된 두 수의 차이」**다(알려진 제한).
     */
    data class Computed(
        val differenceWon: Long,
        val currency: Currency,
    ) : AwardGap
}

/**
 * 차이를 낸다(순수).
 *
 * **시그니처에 잠정 금액 자리가 없다** — 이것이 「잠정을 확정으로 쓰지 않는다」의 구조적
 * 차단면이다(`judgeAwardOutcome` 이 금액 인자를 아예 두지 않은 것과 같은 수법). 호출부가
 * `ProvisionalRankOne` 의 금액을 넘기려면 그 값을 [AwardAmount] 로 만들어야 하고, 그 타입은
 * 개찰 결과의 **확정 축**에서만 복원된다.
 *
 * **통화 불일치 갈래를 두지 않는다.** [Currency] 의 값이 `KRW` 하나뿐이라 두 금액의 통화는
 * 언제나 같다 — 닿지 않는 갈래는 변이로 잴 수 없어 방어처럼 보이는 빈자리가 된다. 그 전제는
 * `MyBidResultTallyTest` 의 통화 축 단언이 래칫으로 지킨다: 통화 어휘가 늘면 그 test 가 먼저
 * 붉으면서 이 자리를 가리킨다(죽은 분기 대신 살아 있는 단언).
 */
fun awardGapOf(
    submitted: SubmittedBidAmount,
    confirmedAward: AwardAmount?,
): AwardGap {
    val award = confirmedAward?.export() ?: return AwardGap.AwardAbsent
    return AwardGap.Computed(submitted.won - award.won, submitted.currency)
}
