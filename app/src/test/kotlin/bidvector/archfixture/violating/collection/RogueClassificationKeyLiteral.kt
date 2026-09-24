package bidvector.archfixture.violating.collection

/**
 * D-6F9-2 우회 4 위반 표본 — 업무구분 세부 분류 원시 키 넷을 계약 데이터 밖(어댑터·use case·러너 자리)에 문자열로 박는다.
 * 값은 각 개념의 필드 계약 raw 이름과 같아야 게이트가 잡는다 — 계약 키가 바뀌면 이 표본을 함께 고친다(게이트 test 가
 * 어긋남을 즉시 드러낸다).
 */
class RogueClassificationKeyLiteral {
    val keys: List<String> = listOf("pubPrcrmntClsfcNo", "pubPrcrmntClsfcNm", "srvceDivNm", "mainCnsttyNm")
}
