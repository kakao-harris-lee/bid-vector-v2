package bidvector.workflow.event

import bidvector.strategy.StrategyEvent
import bidvector.workflow.strategy.Actor
import java.time.Instant

/**
 * 이벤트 봉투(scope.md ①, D-M4-4 (a)) — payload는 도메인 sealed([StrategyEvent] 등)이고
 * 봉투 자신의 소유는 `workflow`다. 아홉 필드: [eventId]·[aggregateId]·[aggregateVersion]·
 * [occurredAt]·[correlationId]·[causationId](nullable)·[idempotencyKey]·[actor](nullable)·
 * [payload].
 *
 * `@ConsistentCopyVisibility` + `internal constructor`(4A `AppliedStrategy`·`EditSession`
 * 관례, 설계 검토 (2) #1) — 임의의 `aggregateVersion`·`idempotencyKey`·`actor`로 봉투를
 * 지어 `OutboxPort.register`에 넣는 위조(값 위조 축 (1))를 막는다. 신규 봉투는 [newEnvelope]
 * (`internal` — `workflow` 안, 사실상 [OutboxEventSink])와 [forStrategyUpdated]만 낸다.
 * 읽기 프로퍼티는 전부 public이다 — 읽기는 권한을 주지 않는다(설계 검토 (2) 표 둘째 행).
 */
@ConsistentCopyVisibility
data class EventEnvelope<out P> internal constructor(
    val eventId: EventId,
    val aggregateId: AggregateId,
    val aggregateVersion: AggregateVersion,
    val occurredAt: Instant,
    val correlationId: CorrelationId,
    val causationId: CausationId?,
    val idempotencyKey: IdempotencyKey,
    val actor: Actor?,
    val payload: P,
) {
    companion object {
        /**
         * 저장소 복원 전용(persistence 어댑터, 4C-2) — **신규 이벤트 생성에 쓰지 않는다.**
         *
         * **`internal`이다(verifier H-1 시정) — 이전 판은 `public`이었다.** 「경계로
         * 처리」(설계 검토 (2) 셋째 행 — 저장소 구현자는 이미 자기 store에 임의 행을
         * 지어낼 수 있으므로 새 권한이 아니다)는 **persistence 어댑터에 대해서만** 참이고,
         * `public`은 그 권한을 **아무 모듈에나** 준다 — `actor=null`인 `StrategyUpdated`
         * 봉투를 `workflow` 밖에서 지어 `OutboxPort.register`(4C-1 자신의 port)에 넣는
         * 위조가 컴파일됐다(verifier 실측, 4A r2 H-3과 같은 형태). 위임 대상 [newEnvelope]가
         * `internal`이라는 사실은 이 함수 **자신**이 `public`이면 아무 의미가 없다 — 이
         * 함수가 바로 그 「공개된 문」이었다.
         *
         * **`internal`로 내리는 것만으로는 4C-2에서 구멍이 되돌아온다** — persistence
         * 어댑터가 실제로 DB 행을 봉투로 되살려야 할 때 이 함수를 다시 열어야 하기 때문이다.
         * 그래서 [OutboxPort.claim]의 반환형을 [EventEnvelope]가 아니라 [ClaimedOutboxRow]
         * (원시 필드)로 바꿔 **어댑터가 이 타입 자체를 다루지 않게** 한다 — 복원은
         * `workflow` 안의 [OutboxEntry.restore]만 하고, 그 함수도 `internal`로 남는다
         * (호출부는 어댑터가 아니라 `workflow` 안의 미래 배달 오케스트레이션 use case).
         */
        internal fun <P> restore(
            eventId: EventId,
            aggregateId: AggregateId,
            aggregateVersion: AggregateVersion,
            occurredAt: Instant,
            correlationId: CorrelationId,
            causationId: CausationId?,
            idempotencyKey: IdempotencyKey,
            actor: Actor?,
            payload: P,
        ): EventEnvelope<P> =
            newEnvelope(
                eventId,
                aggregateId,
                aggregateVersion,
                occurredAt,
                correlationId,
                causationId,
                idempotencyKey,
                actor,
                payload,
            )
    }
}

/**
 * 신규 봉투 생성의 일반 경로(scope.md ①) — `internal`이다: 커널 함수를 `workflow` 밖에서
 * 직접 몰아 임의 필드 값으로 봉투를 얻는 획득 경로를 컴파일 층에서 닫는다(4A H-3 교훈을
 * 미리 적용 — 설계 검토 (2) 마지막 행). `actor`는 nullable이다 — 모든 이벤트가 actor를
 * 요구하지는 않는다.
 */
internal fun <P> newEnvelope(
    eventId: EventId,
    aggregateId: AggregateId,
    aggregateVersion: AggregateVersion,
    occurredAt: Instant,
    correlationId: CorrelationId,
    causationId: CausationId?,
    idempotencyKey: IdempotencyKey,
    actor: Actor?,
    payload: P,
): EventEnvelope<P> =
    EventEnvelope(
        eventId,
        aggregateId,
        aggregateVersion,
        occurredAt,
        correlationId,
        causationId,
        idempotencyKey,
        actor,
        payload,
    )

/**
 * `StrategyUpdated` 전용 생성 경로(scope.md ①, `OPEN-STR-04` 실물) — `actor`가 non-null
 * 필수다. 「누가 바꿨나」 기록은 타입 수준에서 강제된다: 이 함수를 거치지 않고는
 * `StrategyUpdated` payload를 실은 봉투를 얻을 수 없다(`internal`이라 이마저도 `workflow`
 * 안에서만).
 */
internal fun forStrategyUpdated(
    eventId: EventId,
    aggregateId: AggregateId,
    aggregateVersion: AggregateVersion,
    occurredAt: Instant,
    correlationId: CorrelationId,
    causationId: CausationId?,
    idempotencyKey: IdempotencyKey,
    actor: Actor,
    payload: StrategyEvent.StrategyUpdated,
): EventEnvelope<StrategyEvent.StrategyUpdated> =
    newEnvelope(
        eventId,
        aggregateId,
        aggregateVersion,
        occurredAt,
        correlationId,
        causationId,
        idempotencyKey,
        actor,
        payload,
    )
