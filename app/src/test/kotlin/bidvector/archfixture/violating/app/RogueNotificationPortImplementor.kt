package bidvector.archfixture.violating.app

import bidvector.workflow.evaluation.NotificationRequest
import bidvector.workflow.evaluation.NotificationRequestOutcome
import bidvector.workflow.evaluation.NotificationRequestPort

/**
 * D-6A3-17(a)① 위반 표본(verifier M1 재현) — `app` 이 `NotificationRequestPort` 를
 * 스스로 구현한다. `ArchitectureGateCatchesViolationsTest` 가
 * `notificationPortMustBeStructurallyClosed` 가 이 클래스를 실제로 잡는지 확인한다 —
 * production classpath 에는 오르지 않는다(test 소스).
 */
class RogueNotificationPortImplementor : NotificationRequestPort {
    override fun request(notification: NotificationRequest): NotificationRequestOutcome =
        NotificationRequestOutcome.Failed
}
