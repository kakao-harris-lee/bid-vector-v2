package bidvector.archfixture.violating.collection

import bidvector.procurement.BusinessDivision

/**
 * D-6F9-1 우회 1 위반 표본(verifier r1 F-1 재현 MV1) — 문자열 변환 **함수를 하나도 부르지 않고** enum 상수를 직접
 * 읽어 경로에서 대분류를 짓는다. 이전 게이트(멤버 이름 목록)의 시야 밖이던 표기다(바이트코드로는 `getstatic`).
 */
class RogueDivisionFromEnumConstant {
    fun divisionOf(operationPath: String): BusinessDivision =
        when {
            operationPath.endsWith("Cnstwk") -> BusinessDivision.CONSTRUCTION
            else -> BusinessDivision.SERVICE
        }
}
