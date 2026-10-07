package bidvector.archfixture.violating.app

import bidvector.adapters.evaluation.EvaluationCommitRun

/**
 * R1-L-1 위반 표본 — **바깥 클래스는 깨끗하고 중첩 클래스가** 커밋 조립 타입을 참조한다.
 * 선택자가 정확 이름 일치였을 때 이 모양이 규칙의 대상 밖이었다(`외부$중첩` 으로 컴파일되므로).
 * production classpath 에는 오르지 않는다(test 소스).
 */
class RogueDryRunNestedCommitReferencer {
    class Nested(
        private val commit: EvaluationCommitRun,
    )
}
