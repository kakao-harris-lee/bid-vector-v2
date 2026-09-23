package bidvector.archfixture.violating.app.wiring

import bidvector.procurement.Notice
import bidvector.workflow.evaluation.CandidateSourcePort

/**
 * D-6A3-25 위반 표본(verifier r2 N5 재현) — `app.wiring` 에 놓인 헬퍼가 포트 인터페이스를
 * 직접 호출한다. 이전 규칙은 `app.http` 패키지 하나만 봤지만 새 규칙은 app production
 * 전체(패키지 무관)를 본다 — 헬퍼의 위치가 `app.http` 밖이어도 잡힌다. production
 * classpath 에는 오르지 않는다(test 소스).
 */
class RogueWiringPortCaller(
    private val candidateSource: CandidateSourcePort,
) {
    fun call(): List<Notice> = candidateSource.openCandidates()
}
