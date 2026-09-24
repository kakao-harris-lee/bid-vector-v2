package bidvector.archfixture.violating.app

import bidvector.procurement.RawNoticeObservation

/**
 * D-6F8-6 위반 표본 — app 의 이웃 클래스가 원문 관측의 항목 원문 텍스트를 꺼낸다(계약 없이 값을 얻는 두 번째 길).
 * 통과 전용 타입의 멤버 접근 규칙이 모듈 전체(app 포함)에 걸려 있어야 잡힌다. production classpath 에는 오르지 않는다.
 */
class RogueRawFieldPeek(
    private val observation: RawNoticeObservation,
) {
    fun itemJson(): String? = observation.sourceText
}
