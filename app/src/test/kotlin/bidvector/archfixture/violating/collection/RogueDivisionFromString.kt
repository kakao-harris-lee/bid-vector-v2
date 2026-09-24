package bidvector.archfixture.violating.collection

import bidvector.procurement.BusinessDivision
import java.net.URI

/**
 * D-6F9-1 우회 1 위반 표본 — 어댑터 자리에서 대분류를 URL 경로 문자열에서 짓는다(관측이 구조로 나르는 값을 우회). 게이트는
 * 컴파일된 호출 그래프에서 대분류 문자열 변환 멤버의 호출자 쌍을 본다.
 */
class RogueDivisionFromString {
    fun divisionOf(operationUri: URI): BusinessDivision? =
        BusinessDivision.fromLabel(operationUri.path.substringAfterLast('/'))
}
