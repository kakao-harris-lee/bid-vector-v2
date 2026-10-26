package bidvector.app.http

import bidvector.adapters.strategy.StrategyEditTransaction
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyPolicyData
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.EDIT_SESSION_POLICY
import bidvector.workflow.strategy.EditSession
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditSessionSnapshot
import bidvector.workflow.strategy.EditStrategyWorkflow
import bidvector.workflow.strategy.EventSink
import bidvector.workflow.strategy.StrategyRepository
import bidvector.workflow.strategy.toSnapshot
import java.time.LocalDate
import java.util.Collections

/*
 * 전략 편집 endpoint 의 test 이중체(트랜잭션 경계 · 세션 저장소 · 사건 수집기)다.
 * `HttpTestSupport.kt` 가 파일 한도 500 줄에 정확히 닿아 test 조립에 한 줄도 더할 수 없어
 * 갈랐다(M7/7C ②) — 상한이 분할을 요구한 자리다. **가른 축을 「전략 편집」으로 고른 이유**는
 * 같은 상한 때문에 다른 두 레인이 이미 평가 축(`EvaluationTestDoubles.kt`)과 시계 축
 * (`ClockTestDoubles.kt`)을 가져갔기 때문이다 — 선언이 겹치면 병합 뒤 중복 선언으로 컴파일이
 * 깨진다. 같은 패키지라 쓰는 자리의 import 는 하나도 바뀌지 않는다.
 */

/**
 * 실 DB 없이 편집 endpoint 를 도는 트랜잭션 경계 이중체. **use case 는 실물**
 * 이다(상태 기계·멱등·만료 판정을 test 사본으로 다시 짓지 않는다) — 바뀌는 것은 커넥션
 * 경계뿐이라, 이 이중체 위에서 재는 것은 「HTTP 층이 결과를 어떤 상태 코드로 옮기는가」다.
 * 원자성 자체(전략+outbox+세션 한 커밋)는 이 자리가 아니라 실 DB test 가 잰다.
 */
class InMemoryStrategyEditTransaction(
    private val strategies: StrategyRepository,
    private val clock: Clock,
) : StrategyEditTransaction {
    val sessions: InMemoryEditSessionRepository = InMemoryEditSessionRepository()
    val events: RecordingStrategyEventSink = RecordingStrategyEventSink()

    /** Spring 컨텍스트가 test 사이에 재사용되므로 세션·발행 이력을 매 test 전에 비운다. */
    fun reset() {
        sessions.clear()
        events.published.clear()
        events.actors.clear()
    }

    override fun <T> inTransaction(action: (EditStrategyWorkflow) -> T): T {
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val sessionPolicy =
            (EDIT_SESSION_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<EditSessionPolicyData>).value
        return action(EditStrategyWorkflow(sessions, strategies, clock, events, policy, sessionPolicy))
    }
}

/** `EditSession` 은 `internal constructor` 라 저장은 값 그대로, 반환은 원시 스냅숏으로 한다(D-6B1-7). */
class InMemoryEditSessionRepository : EditSessionRepository {
    private val stored = mutableMapOf<EditSessionId, EditSession>()

    override fun load(id: EditSessionId): EditSessionSnapshot? = stored[id]?.toSnapshot()

    override fun save(session: EditSession) {
        stored[session.id] = session
    }

    fun clear() = stored.clear()
}

class RecordingStrategyEventSink : EventSink {
    val published: MutableList<StrategyEvent> = Collections.synchronizedList(mutableListOf())

    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) {
        published += event
        actors += actor
    }

    val actors: MutableList<Actor> = Collections.synchronizedList(mutableListOf())
}
