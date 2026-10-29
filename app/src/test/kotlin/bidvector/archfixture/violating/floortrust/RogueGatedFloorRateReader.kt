package bidvector.archfixture.violating.floortrust

import bidvector.decision.PlausibleFloorRate
import bidvector.sharedkernel.Rate

/**
 * DEC-10 ⓐ 의 음성 — 게이트 통과 값의 멤버를 만지는 **새 소비자**. 원값을 되꺼낼 수는 없지만
 * (`internal`), 통과 값이 닿는 자리가 늘어나는 것 자체가 허용 목록에 한 줄을 요구한다.
 */
class RogueGatedFloorRateReader {
    fun rateOf(gated: PlausibleFloorRate): Rate = gated.rate
}
