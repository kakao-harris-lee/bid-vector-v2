package bidvector.adapters.event

import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.event.ConsumerTransactionPort

/**
 * [ConsumerTransactionPort]의 구현 — 커밋 경계를 이미 진 [TransactionBoundary] 에 그대로
 * 위임한다. 이 클래스가 하는 일은 **경계의 주인을 `workflow` 로 넘겨 주는 것**뿐이다: relay 는
 * T1/T2 를 스스로 갈라야 하므로(at-most-once) 「언제 커밋하는가」를 알아야 하고, `workflow` 는
 * `TransactionBoundary` 를 이름으로 볼 수 없다(의존 방향).
 *
 * 같은 [TransactionBoundary] 인스턴스를 `JdbcOutboxPort`·`JdbcInboxPort` 에도 넘겨야 한다 —
 * 그 둘이 이 경계가 연 커넥션을 쓰는 것이 「한 커밋」의 뜻이다. 다른 인스턴스를 넘기면
 * `withConnection` 이 경계 밖 접근으로 **실행 시점에 던진다**(그 경계의 ThreadLocal 설계).
 */
class ConsumerTransactions(
    private val boundary: TransactionBoundary,
) : ConsumerTransactionPort {
    override fun <T> inTransaction(block: () -> T): T = boundary.inTransaction(block)
}
