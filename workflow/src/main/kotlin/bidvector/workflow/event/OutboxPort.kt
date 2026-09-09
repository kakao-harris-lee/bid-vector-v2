package bidvector.workflow.event

/**
 * outbox 저장 port(scope.md ③) — 구현은 4C-2(실 persistence 어댑터), 이 slice는 test fake만
 * 둔다. `register`가 도메인 write와 같은 트랜잭션에서 커밋된다는 계약(ADR 0005 D-2)은
 * 문면으로 선언할 뿐 강제하지 않는다 — 실 저장이 있어야 잰다(`OPEN-4C1-TX-CONTRACT-UNVERIFIED`,
 * scope.md).
 *
 * `mark*`는 [OutboxEntryState]를 직접 받지 않는다(설계 검토 (2) 표 넷째 행) — 각각
 * [OutboxTransition]의 대응 하위 타입(`internal constructor`)만 받는다. 그 값은
 * [transitionOutbox](`internal`)만 낼 수 있으므로 전이표를 거치지 않은 임의 상태 점프는
 * 이 인터페이스가 public이어도 인자 자체를 만들 방법이 없어 막힌다.
 */
interface OutboxPort {
    fun register(envelope: EventEnvelope<*>): OutboxEntryId

    /** `Pending`을 `Claimed`로 옮기며 최대 [limit]개를 반환한다 — claim 경합 제어는 4C-2. */
    fun claim(limit: Int): List<OutboxEntry>

    fun markDelivered(transition: OutboxTransition.ToDelivered)

    fun markFailed(transition: OutboxTransition.ToFailed)

    fun markIsolated(transition: OutboxTransition.ToIsolated)
}
