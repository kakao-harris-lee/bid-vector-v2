package bidvector.archfixture.violating.app

import bidvector.workflow.collection.CollectNoticesUseCase

/**
 * D-6F8-6 위반 표본 — 러너·배선 밖의 클래스가 수집 use case 를 참조한다(컨트롤러·이벤트 리스너로 수집을 여는 길).
 * production classpath 에는 오르지 않는다(test 소스).
 */
class RogueUseCaseCaller(
    val useCase: CollectNoticesUseCase,
)
