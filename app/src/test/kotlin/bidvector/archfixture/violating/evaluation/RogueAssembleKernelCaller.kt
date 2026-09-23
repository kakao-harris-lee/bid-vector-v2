package bidvector.archfixture.violating.evaluation

import bidvector.strategy.assembleKeywordScopeText

/**
 * D-6A3-9 위반 표본 — 허용 목록(`NoticeWatchSubjectPortKt`) 밖에서 감시 텍스트 조립 커널을
 * 직접 부른다. `ArchitectureGateCatchesViolationsTest`가 `assembleCallersMustBeAllowedSet`
 * 이 이 클래스를 실제로 잡는지 확인한다 — production classpath 에는 오르지 않는다(test 소스).
 */
class RogueAssembleKernelCaller {
    fun leak(title: String?): String = assembleKeywordScopeText(title, null).value
}
