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
 *
 * **`claim`은 [EventEnvelope]를 돌려주지 않는다(verifier H-1 시정).** 이전 판은
 * `List<OutboxEntry>`(완성된 봉투를 나름)를 돌려줬는데, `EventEnvelope.restore`가
 * `public`이라 **어느 모듈에서든** 임의 필드로 봉투를 지어 `register`에 넣는 위조가
 * 컴파일됐다(4A r2 H-3과 같은 형태 — 위조는 막았지만 재료 획득은 열려 있었다). `restore`를
 * `internal`로 내리면 그 자체로는 안전해지지만, 4C-2의 persistence 어댑터가 실제로 DB
 * 행을 봉투로 되살려야 하는 순간 그 함수를 다시 열어야 하고 같은 구멍이 되돌아온다.
 * **그래서 배치를 바꾼다**: `claim`은 [ClaimedOutboxRow](완성 전 원시 필드)만 돌려주고,
 * 어댑터는 [EventEnvelope]를 전혀 다루지 않는다. 원시 행 → [OutboxEntry] 복원은
 * `workflow` 안의 `internal` 매핑([OutboxEntry.restore])이 진다 — 그 함수는 4C-2 에서도
 * 계속 `internal`일 수 있다(호출부가 `workflow` 안의 미래 배달 오케스트레이션 use case이지
 * 어댑터가 아니기 때문이다).
 */
interface OutboxPort {
    fun register(envelope: EventEnvelope<*>): OutboxEntryId

    /** `Pending`을 `Claimed`로 옮기며 최대 [limit]개의 원시 행을 반환한다 — claim 경합 제어는 4C-2. */
    fun claim(limit: Int): List<ClaimedOutboxRow<*>>

    fun markDelivered(transition: OutboxTransition.ToDelivered)

    fun markFailed(transition: OutboxTransition.ToFailed)

    fun markIsolated(transition: OutboxTransition.ToIsolated)
}
