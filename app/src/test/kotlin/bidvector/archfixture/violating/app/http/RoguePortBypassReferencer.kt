package bidvector.archfixture.violating.app.http

import bidvector.workflow.evaluation.CandidateSourcePort

/**
 * D-6A3-17(c) 위반 표본(verifier M5 재현) — `app.http` 가 예외 목록 없이 workflow port
 * (`CandidateSourcePort`)를 직접 참조한다 — 컨트롤러가 use case 를 우회해 포트를 직접
 * 부르는 helper 를 재현한다. production classpath 에는 오르지 않는다(test 소스).
 */
class RoguePortBypassReferencer(
    private val candidateSource: CandidateSourcePort,
)
