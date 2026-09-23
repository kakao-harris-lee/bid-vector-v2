package bidvector.archfixture.violating.app

import bidvector.workflow.evaluation.OutboxNotificationRequestPort

/**
 * D-6A3-17(a)② 위반 표본 — `app` 이 허용 목록 밖의 `NotificationRequestPort` 구현체
 * (`OutboxNotificationRequestPort`, 실 production 클래스)를 참조한다. `Recording
 * NotificationRequestPort` 가 아닌 다른 구현을 배선하는 우회를 재현한다 — production
 * classpath 에는 오르지 않는다(test 소스).
 */
class RogueOutboxNotificationReferencer(
    private val port: OutboxNotificationRequestPort,
)
