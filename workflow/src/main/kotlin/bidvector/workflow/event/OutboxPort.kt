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
 * **`claim`은 [EventEnvelope]를 돌려주지 않는다.** `EventEnvelope.restore`가
 * `public`이면 **어느 모듈에서든** 임의 필드로 봉투를 지어 `register`에 넣는 위조가
 * 컴파일된다(위조는 막아도 재료 획득은 열려 있는 형태). `restore`를
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

    /**
     * [kind]인 `Pending`을 `Claimed`로 옮기며 최대 [limit]개의 원시 행을 반환한다 — claim
     * 경합 제어는 4C-2(`FOR UPDATE SKIP LOCKED`).
     *
     * **[kind]를 받는다(D-6F10-13)** — 좁히지 않은 claim 은 소비자가 없는 종류
     * ([OutboxConsumerKind.StrategyUpdated])까지 집고, 전이표에 `Claimed -> Pending`
     * 간선이 없어 그 행은 격리밖에 못 간다. 「relay 가 조심한다」가 아니라 **질의가 집지
     * 않는다**로 닫는다.
     */
    fun claim(
        limit: Int,
        kind: OutboxConsumerKind,
    ): List<ClaimedOutboxRow<*>>

    /**
     * [kind]인 `Claimed` 행 전부를 **읽는다**(D-6F10-11 고아 판정의 입력) — 전이시키지
     * 않는다. 격리는 호출부가 `transitionOutbox`를 지나 [markIsolated]로 한다.
     *
     * **고아 판정은 이 목록의 시각이 아니라 호출 시점의 구조로 성립한다** — lease 를
     * **새로** 잡은 소비자가 **첫 [claim] 전에** 부르면, 그때 보이는 `Claimed`는 전부
     * 죽은 홀더가 남긴 것이다(살아 있는 홀더가 있으면 lease 를 못 잡았다). 그래서 이
     * 함수 자신은 「얼마나 오래 `Claimed`였는가」를 알 필요가 없고 행도 그 값을 나르지
     * 않는다([ClaimedOutboxRow]에 claim 시각이 없다).
     *
     * 행을 잠그지 않는다 — 상호 배제는 [ConsumerLeasePort]가 지고, 여기서 잠그면 살아
     * 있는 다른 소비자의 claim 트랜잭션과 교차 대기를 만든다.
     */
    fun claimedEntries(kind: OutboxConsumerKind): List<ClaimedOutboxRow<*>>

    /**
     * `Claimed`를 `Delivered`로 옮긴다. **갱신 계수 0 은 조용한 no-op 가 아니다**
     * (D-6F10-2) — 구현은 행을 옮기지 못하면 던진다(fail-closed). 「전이표가 거부했다」와
     * 「행이 이미 다른 상태다」는 다른 사실이고, 뒤쪽은 이 계약이 든다.
     */
    fun markDelivered(transition: OutboxTransition.ToDelivered)

    /** `Claimed`를 `Failed`로 옮긴다 — 계수 계약은 [markDelivered]와 같다. */
    fun markFailed(transition: OutboxTransition.ToFailed)

    /** `Claimed`를 `Isolated`로 옮긴다 — 계수 계약은 [markDelivered]와 같다. */
    fun markIsolated(transition: OutboxTransition.ToIsolated)
}
