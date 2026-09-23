package bidvector.archfixture.violating.app

import bidvector.workflow.event.OutboxPort

/**
 * D-6A3-17(a)③ 위반 표본(verifier M1 재현의 다른 축) — `app` 이 `NotificationRequestPort`
 * 를 거치지 않고 outbox 쓰기 타입(`OutboxPort`)을 직접 참조한다 — production classpath 에는
 * 오르지 않는다(test 소스).
 */
class RogueOutboxPortReferencer(
    private val outbox: OutboxPort,
)
