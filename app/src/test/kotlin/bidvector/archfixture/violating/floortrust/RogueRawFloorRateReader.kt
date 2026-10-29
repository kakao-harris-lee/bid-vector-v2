package bidvector.archfixture.violating.floortrust

import bidvector.procurement.Notice
import bidvector.sharedkernel.FloorRate

/**
 * DEC-10 ⓑ 의 음성 — **새 소비자가 원값을 끌고 오는** 자리. 게시 하한율의 원값을 운반 슬롯에서
 * 꺼내 그대로 내보낸다. 이 클래스가 관측 집합에 나타나지 않으면 등식 ⓑ 는 공허하다.
 *
 * R-FLOOR-04 가 경고한 형태가 바로 이것이다 — legacy 는 「소비자가 X 만 하므로 안전」을 주석으로
 * 적었고 이 모양의 소비자가 하나 늘자 그 근거가 조용히 무효화됐다.
 */
class RogueRawFloorRateReader {
    fun rawFloorRateOf(notice: Notice): FloorRate? = notice.floorRate
}
