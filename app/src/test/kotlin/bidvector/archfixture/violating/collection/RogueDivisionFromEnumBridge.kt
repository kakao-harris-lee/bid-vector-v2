package bidvector.archfixture.violating.collection

import bidvector.procurement.BusinessDivision

/**
 * D-6F9-1 우회 1 위반 표본(verifier r1 F-1 재현 MV4) — `java.lang.Enum` 의 정적 변환으로 우회한다. 호출 소유자가
 * `java.lang.Enum` 이라 대분류 타입 이름이 **호출 자리에 없고**, 리플렉션 게이트도 `java.lang.reflect` 를 참조하지
 * 않아 조용하다. 타입 이름이 남는 유일한 자리가 클래스 객체(축 ③)다.
 */
class RogueDivisionFromEnumBridge {
    fun divisionOf(rawName: String): BusinessDivision = java.lang.Enum.valueOf(BusinessDivision::class.java, rawName)
}
