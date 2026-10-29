package bidvector.archfixture.violating.floortrust

import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.Rate

/**
 * r1 v H-1 (a) 의 음성 — **운반 클래스** 형태(V2). 허용된 획득자에게서 원값을 받아 자기 필드에
 * 두고 그 필드의 멤버를 읽는다. 자기 필드 읽기는 획득 축의 제외 규칙에 걸리지만, 원값 타입의
 * 멤버를 만지는 것은 멤버 접근 축이 센다.
 */
class RogueFloorRateCarrier(
    private val carried: FloorRate,
) {
    fun rate(): Rate = carried.rate
}
