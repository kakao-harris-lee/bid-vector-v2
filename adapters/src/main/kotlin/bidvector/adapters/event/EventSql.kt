package bidvector.adapters.event

/**
 * outbox·inbox SQL 문자열 상수(3D `Sql.kt` 관례 — mapper/adapter 코드와 분리, sizeGate).
 *
 * **`mark*` 세 상수는 production 코드와 test 가 같은 값을 공유한다**(설계 검토 (3) 미달
 * 위험 1) — `OutboxPort.markDelivered`/`markFailed`/`markIsolated`는 이 slice에서 production
 * 호출부가 없다(`OPEN-4C2-MARK-UNEXERCISED`, 배달 오케스트레이션 인계). `adapters` test도
 * [bidvector.workflow.event.OutboxTransition]의 하위 타입을 만들 수 없어(`internal
 * constructor`) 그 port 메서드를 호출하는 test를 쓸 수 없다 — 그래서 test는 이 상수를
 * **그대로** DB에 실행해 전이 UPDATE의 효과와 `WHERE state` 거부(잘못된 이전 상태에서는
 * 영향 행 0)를 잰다. 사본을 test에 따로 두지 않는다(3D verifier가 지적한 「SQL 사본」
 * 자리를 반복하지 않는다).
 */
internal object EventSql {
    const val INSERT_OUTBOX =
        """
        INSERT INTO outbox (
            entry_id, event_id, aggregate_id, aggregate_version, occurred_at,
            correlation_id, causation_id, idempotency_key, actor_kind, actor_detail,
            payload_type, payload, state
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
        """

    // claim(⑫scope.md ④, 설계 검토 (4)-④) — `FOR UPDATE SKIP LOCKED`가 경쟁 창을 막고,
    // 같은 트랜잭션 안 MARK_CLAIMED 커밋이 그 이후를 막는다(둘은 다른 구간).
    const val SELECT_PENDING_FOR_UPDATE_SKIP_LOCKED =
        """
        SELECT entry_id, event_id, aggregate_id, aggregate_version, occurred_at,
               correlation_id, causation_id, idempotency_key, actor_kind, actor_detail,
               payload_type, payload
        FROM outbox
        WHERE state = 'PENDING'
        ORDER BY inserted_at
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """

    const val MARK_CLAIMED = "UPDATE outbox SET state = 'CLAIMED' WHERE entry_id = ? AND state = 'PENDING'"
    const val MARK_DELIVERED = "UPDATE outbox SET state = 'DELIVERED' WHERE entry_id = ? AND state = 'CLAIMED'"
    const val MARK_FAILED = "UPDATE outbox SET state = 'FAILED' WHERE entry_id = ? AND state = 'CLAIMED'"
    const val MARK_ISOLATED = "UPDATE outbox SET state = 'ISOLATED' WHERE entry_id = ? AND state = 'CLAIMED'"

    const val SELECT_OUTBOX_STATE = "SELECT state FROM outbox WHERE entry_id = ?"

    // ⑤ inbox — `ON CONFLICT DO NOTHING`이 같은 키의 이중 적재를 구조로 막는다(PK 유일성).
    const val INSERT_INBOX = "INSERT INTO inbox (idempotency_key) VALUES (?) ON CONFLICT (idempotency_key) DO NOTHING"
    const val SELECT_INBOX = "SELECT 1 FROM inbox WHERE idempotency_key = ?"
}
