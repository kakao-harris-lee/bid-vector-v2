package bidvector.archfixture.violating.collection

import bidvector.procurement.BusinessDivision

/**
 * M6/6F-9 D-6F9-1 우회 1 음성 대조 — 이 클래스는 **잡히면 안 된다**. 대분류를 생성 인자로 받아 자기 필드에 두고
 * 자기 필드만 읽는다: 값을 **얻는** 자리는 이것을 만드는 쪽이고 그 자리는 이미 값 획득 축에 쌍으로 열거된다
 * (`KonepsOpenApiNoticeSource` 같은 운반 슬롯과 같은 모양이다).
 *
 * `CleanNameLookup`(대분류를 아예 언급하지 않는다)과 달리 이 대조는 **공허하지 않다** — 축 ②가 자기 소유 읽기를
 * 제외하는 분기(호출자 == 소유자)를 지우면 대분류를 나르기만 하는 클래스가 전부 위반으로 신고되고 이 단언이 RED 가
 * 된다(code-review r2 LOW, 측정). 즉 게이트의 과잉 경계가 이 파일에 잠긴다.
 */
class CleanDivisionCarrier(
    private val division: BusinessDivision,
) {
    fun carried(): BusinessDivision = division
}
