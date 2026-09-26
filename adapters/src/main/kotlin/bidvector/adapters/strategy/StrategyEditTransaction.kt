package bidvector.adapters.strategy

import bidvector.adapters.event.JdbcEventIdFactory
import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.sharedkernel.Resolution
import bidvector.strategy.StrategyPolicyData
import bidvector.workflow.event.EventIdFactory
import bidvector.workflow.event.OutboxEventSink
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditStrategyWorkflow

/**
 * 편집 use case 를 **한 트랜잭션 안에서** 빌려주는 경계(M6/6A-2b D-6A2b-3) — 호출부는
 * [EditStrategyWorkflow] 하나만 받고, 그 use case 가 쥔 포트(전략 저장소·세션 저장소·
 * outbox sink)는 이 경계 밖으로 나가지 않는다.
 *
 * **인터페이스인 이유는 test 이중화가 아니라 의존 방향이다.** `app` 은 `adapters` 를 볼 수
 * 있지만 그 반대는 없다(v2-지침서 §3.1) — 그래서 이 계약은 `adapters` 에 산다. `app` 쪽
 * 실행기(`app.wiring`)는 이 타입만 알고, 컨트롤러(`app.http`)는 그 실행기만 안다
 * (D-6A2b-8 의존 게이트가 그 두 층을 구조로 강제한다).
 */
interface StrategyEditTransaction {
    fun <T> inTransaction(action: (EditStrategyWorkflow) -> T): T
}

/**
 * [StrategyEditTransaction] 의 JDBC 구현(D-6A2b-3) — **요청마다** [TransactionBoundary] 를
 * 열고 그 커넥션 위에 저장소 셋과 outbox sink 를 세워 use case 를 조립한다
 * (`EvaluationDryRunFactory` 의 요청 스코프 조립 선례). 전략 저장 → outbox 등록 → 세션
 * 전진이 **함께 커밋되거나 함께 롤백된다** — 4A 가 알려진 제한으로 남긴 「발행 실패 뒤
 * 세션 미전진」 창이 여기서 닫힌다.
 *
 * 왜 `app` 이 아니라 여기인가: 이 조립은 `JdbcOutboxPort`·[OutboxEventSink] 를 이름으로
 * 부른다. `app` production 은 outbox 쓰기 타입을 참조하지 못한다(D-6A3-17(a)③ 게이트,
 * dry-run effect 0) — 그 게이트를 넓히는 대신 조립을 어댑터 층에 둔다.
 *
 * 저장소 인스턴스를 재사용하지 않고 트랜잭션마다 새로 만드는 이유는 상태가 아니라
 * **경계**다 — 저장소는 [TransactionBoundary] 를 `ConnectionSource` 로 받아 그 트랜잭션의
 * 커넥션만 쓰고, 경계 밖에서 부르면 `withConnection` 이 실행 시점에 던진다.
 */
class JdbcStrategyEditTransaction(
    private val transactions: TransactionBoundary,
    private val strategyPolicy: Resolution.Resolved<StrategyPolicyData>,
    private val sessionPolicy: EditSessionPolicyData,
    private val clock: Clock,
    private val eventIds: EventIdFactory = JdbcEventIdFactory(),
) : StrategyEditTransaction {
    override fun <T> inTransaction(action: (EditStrategyWorkflow) -> T): T =
        transactions.inTransaction { action(assemble()) }

    private fun assemble(): EditStrategyWorkflow =
        EditStrategyWorkflow(
            sessions = JdbcEditSessionRepository(transactions),
            strategies = JdbcStrategyRepository(transactions, strategyPolicy),
            clock = clock,
            events = OutboxEventSink(JdbcOutboxPort(transactions), eventIds, clock),
            strategyPolicy = strategyPolicy,
            sessionPolicy = sessionPolicy,
        )
}
