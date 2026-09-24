package bidvector.archfixture.violating.app.http

import bidvector.procurement.Notice
import bidvector.workflow.evaluation.CandidateSourcePort

/**
 * D-6A3-25 위반 표본(verifier r2 N6 재현) — 호출 지점의 owner 가 포트 인터페이스가 아니라
 * 구체 구현 타입([RogueConcreteCandidateSource])이다. 인터페이스 이름 등식에 거는 규칙은
 * 이 형태를 못 본다 — 새 규칙은 owner 의 `isAssignableTo` 로 판정하므로 구체 타입을 거쳐도
 * 잡힌다. production classpath 에는 오르지 않는다(test 소스).
 */
class RogueConcreteCandidateSource : CandidateSourcePort {
    override fun openCandidates(): List<Notice> = emptyList()
}

class RogueConcreteAdapterCaller {
    fun call(): List<Notice> = RogueConcreteCandidateSource().openCandidates()
}
