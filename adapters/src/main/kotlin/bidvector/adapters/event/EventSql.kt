package bidvector.adapters.event

/**
 * outbox·inbox SQL 문자열 상수(3D `Sql.kt` 관례 — mapper/adapter 코드와 분리, sizeGate).
 *
 * **`mark*` 세 상수는 production 코드와 test 가 같은 값을 공유한다**(설계 검토 (3) 미달
 * 위험 1). **M6/6F-10 부터 production 호출부가 있다** — relay use case
 * (`bidvector.workflow.notification.RelayOutboxNotifications`)가 `OutboxPort.markDelivered`/
 * `markFailed`/`markIsolated`를 port 로 부른다(`OPEN-4C2-MARK-UNEXERCISED` 종결). 통로를
 * 열어 닫은 것이 아니다: [bidvector.workflow.event.OutboxTransition]의 하위 타입 생성자는
 * 여전히 `workflow` 의 `internal` 이고, 그래서 `adapters` test 는 지금도 그 인자를 만들 수
 * 없어 **이 상수를 그대로 DB 에 실행**해 전이 UPDATE 의 효과와 `WHERE state` 거부(잘못된
 * 이전 상태에서는 영향 행 0)를 잰다. 사본을 test에 따로 두지 않는다(3D verifier가 지적한
 * 「SQL 사본」 자리를 반복하지 않는다).
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
        WHERE state = 'PENDING' AND payload_type = ?
        ORDER BY inserted_at
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """

    // claimedEntries(D-6F10-11 고아 판정의 입력) — **읽기만** 이고 행을 잠그지 않는다:
    // 상호 배제는 lease(advisory lock)가 지고, 여기서 FOR UPDATE 를 걸면 살아 있는 다른
    // 소비자의 claim 트랜잭션과 교차 대기를 만든다. `state = 'CLAIMED'` 는 어느 인덱스도
    // 덮지 않아 순차 스캔이다(부분 인덱스는 PENDING 전용) — 알려진 제한
    // `OPEN-6F10-CLAIM-INDEX`, 마이그레이션 0(A-2 (a)).
    const val SELECT_CLAIMED_BY_TYPE =
        """
        SELECT entry_id, event_id, aggregate_id, aggregate_version, occurred_at,
               correlation_id, causation_id, idempotency_key, actor_kind, actor_detail,
               payload_type, payload
        FROM outbox
        WHERE state = 'CLAIMED' AND payload_type = ?
        ORDER BY inserted_at
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
