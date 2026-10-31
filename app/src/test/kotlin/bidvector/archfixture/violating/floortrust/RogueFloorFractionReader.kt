package bidvector.archfixture.violating.floortrust

import bidvector.sharedkernel.FloorRate
import java.math.BigDecimal

/**
 * r1 v H-1 (a) 의 음성 — **파생 접근자** 형태. 원값을 **인자로 받아** 숫자만 꺼내 내보내므로
 * 획득 축(반환·필드 **타입**이 원값인 자리)에는 보이지 않는다. 멤버 접근 축만 이 형태를 센다.
 *
 * verifier 가 실측한 V1 은 이 접근자를 `procurement.Notice` 안에 두고 소비자가 `BigDecimal` 만
 * 받게 했다. production 에서 그 모양이 생기면 멤버 접근 축에 `Notice->getRate` 쌍이 새로
 * 관측된다(현재 허용에는 `Notice->hashCode` 뿐이다) — 그래서 그 자리도 이 축이 센다.
 */
class RogueFloorFractionReader {
    fun fractionOf(floorRate: FloorRate): BigDecimal = floorRate.rate.fraction
}
