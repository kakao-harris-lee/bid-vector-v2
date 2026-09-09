package bidvector.workflow.event

/** [OutboxPort.claim]이 내는 읽기 모델(scope.md ③) — 배달 시도의 입력이다. */
data class OutboxEntry(
    val id: OutboxEntryId,
    val envelope: EventEnvelope<*>,
    val state: OutboxEntryState,
)
