package bidvector.workflow.event

/** 이벤트 봉투 식별자(scope.md ①) — outbox 등록 시 고정된다. */
data class EventId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "EventId는 빈 문자열일 수 없다" }
    }
}

/** 봉투가 나르는 도메인 aggregate 식별자(scope.md ①). */
data class AggregateId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "AggregateId는 빈 문자열일 수 없다" }
    }
}

/** aggregate의 낙관적 버전(scope.md ①) — 음수는 불변식 위반이다. */
data class AggregateVersion(
    val value: Long,
) {
    init {
        require(value >= 0) { "AggregateVersion은 음수일 수 없다: $value" }
    }
}

/** 인과 사슬을 묶는 상관관계 식별자(scope.md ①) — 사슬의 기원에서는 [EventId]와 값이 같다. */
data class CorrelationId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CorrelationId는 빈 문자열일 수 없다" }
    }
}

/** 이 이벤트를 낳은 상위 이벤트의 식별자(scope.md ①) — 기원 이벤트는 null이다. */
data class CausationId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CausationId는 빈 문자열일 수 없다" }
    }
}

/**
 * 재전달 dedup 축(scope.md ④, ADR 0005 D-3) — 봉투 등록 시 고정되고 소비자는 이 값만
 * 보고 중복을 가른다. [EventId]와는 다른 축이다(재발행마다 [EventId]는 새로 나도
 * [IdempotencyKey]는 같은 업무 사실이면 같다 — `OutboxEventSinkTest` 참고).
 */
data class IdempotencyKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "IdempotencyKey는 빈 문자열일 수 없다" }
    }
}

/** outbox 행 식별자(scope.md ③) — [OutboxPort.register]가 발급한다. */
data class OutboxEntryId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "OutboxEntryId는 빈 문자열일 수 없다" }
    }
}
