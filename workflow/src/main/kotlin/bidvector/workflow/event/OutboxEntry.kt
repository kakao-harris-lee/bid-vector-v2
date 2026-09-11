package bidvector.workflow.event

/**
 * [OutboxPort.claim]이 낸 원시 행([ClaimedOutboxRow])을 되살린 읽기 모델(scope.md ③) —
 * 배달 시도의 입력이다. `@ConsistentCopyVisibility` + `internal constructor`(verifier H-1
 * 시정, 4A `AppliedStrategy` 관례) — `envelope` 필드가 [EventEnvelope]를 나르므로, 이
 * 타입의 생성자가 공개면 그 자체가 「완성된 봉투를 손에 넣는」 경로가 된다. 유일한 생성
 * 경로는 [restore]다.
 */
@ConsistentCopyVisibility
data class OutboxEntry internal constructor(
    val id: OutboxEntryId,
    val envelope: EventEnvelope<*>,
    val state: OutboxEntryState,
) {
    companion object {
        /**
         * [ClaimedOutboxRow]에서 항목을 되살린다(verifier H-1 시정 — `OutboxPort.claim`이
         * [EventEnvelope]가 아니라 이 원시 행을 돌려주므로, 어댑터는 [EventEnvelope]를
         * 전혀 다루지 않는다). `internal`이다 — `EventEnvelope.restore`도 `internal`이라
         * 이 함수가 `workflow` 밖에서 호출될 수 없다. `claim`이 낸 행은 정의상 `Pending`을
         * `Claimed`로 옮긴 결과이므로 상태는 항상 [OutboxEntryState.Claimed]다 — 행 자체가
         * 상태를 나르지 않는다(claim 경합·원자성은 4C-2, `OPEN-4C1-TX-CONTRACT-UNVERIFIED`).
         */
        internal fun <P> restore(row: ClaimedOutboxRow<P>): OutboxEntry =
            OutboxEntry(
                id = row.entryId,
                envelope =
                    EventEnvelope.restore(
                        eventId = row.eventId,
                        aggregateId = row.aggregateId,
                        aggregateVersion = row.aggregateVersion,
                        occurredAt = row.occurredAt,
                        correlationId = row.correlationId,
                        causationId = row.causationId,
                        idempotencyKey = row.idempotencyKey,
                        actor = row.actor,
                        payload = row.payload,
                    ),
                state = OutboxEntryState.Claimed,
            )
    }
}
