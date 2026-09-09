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
         * `internal`이 아니라 `public`이다(설계 검토 (2) 셋째 행 — 「경계로 처리」) —
         * 저장소 구현자는 이미 자기 store에 임의 행을 지어낼 수 있으므로 이 함수가 새로
         * 주는 권한은 없다(4A (d)와 같은 위협 모델 경계). **신규 발행 경로([newEnvelope]·
         * [forStrategyUpdated])와 이 함수를 같은 호출부에서 섞지 않는다** — 이름·이 KDoc이
         * 그 경계를 못박는다. 아홉 필드를 채워 같은 private 생성자에 넘기는 몸통은
         * [newEnvelope]와 같으므로 중복을 두지 않고 그대로 위임한다(§5 중복 금지) — 이
         * 위임이 경계를 흐리지 않는 이유는 `newEnvelope` 자체가 `internal`이라 이 위임을
         * 거치지 않고는 `workflow` 밖에서 여전히 부를 수 없기 때문이다.
         */
        fun <P> restore(
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
