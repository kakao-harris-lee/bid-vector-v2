package bidvector.workflow.event

/**
 * 종단 행의 사유 어휘(M9 OPS-05) — 「왜 `DELIVERED`·`FAILED`·`ISOLATED` 인가」를 저장 층이
 * 답한다. 지금까지 그 사실은 relay 의 run 보고와 로그에만 있었고, run 이 끝나면 사라졌다.
 *
 * **새 처분을 만들지 않는다** — relay 의 어휘 해석표(D-6F10-12)가 이미 가른 일곱 갈래를 값으로
 * 올린 것이다. 그 가운데 [UNKNOWN_PAYLOAD] 는 V6 가 「어휘에 그 칸이 없어」 `ISOLATED`(모호)로
 * 접고 계수로만 가른 자리다(`RelayReport.Completed.unknownPayload`) — 이제 DB 가 그 둘을 가른다.
 *
 * [terminalState] 는 **사유가 목표 상태를 정한다**는 사실이다. 명령과 상태를 따로 받으면
 * 어긋난 조합(`Fail` + [ORPHANED_CLAIM])이 성립하므로 [OutboxCommand.Terminate] 는 사유만
 * 받는다. 저장 층은 이 사상을 V27 의 `(state, terminal_reason)` CHECK 로 다시 선언한다.
 */
enum class OutboxTerminalReason(
    val terminalState: OutboxEntryState,
) {
    /** 실제로 발송됐다. */
    DELIVERED(OutboxEntryState.Delivered),

    /** 같은 멱등 키가 이미 전달돼 발송 없이 종단했다(D-6F10-12 — 그것도 전달이다). */
    DUPLICATE_SKIPPED(OutboxEntryState.Delivered),

    /** 채널이 전달을 거부했다 — 「전달되지 않았고, 이 entry 로는 되지 않을 것」. */
    DELIVERY_REJECTED(OutboxEntryState.Failed),

    /** 채널 비활성·route 부재로 발송 자체가 없었다(환경 억제는 claim 전에 걸러져 이 자리에 오지 않는다). */
    DELIVERY_SUPPRESSED(OutboxEntryState.Failed),

    /** 발송했으나 결과가 모호하다 — 재시도하지 않는다(at-most-once, D-4E-2). */
    DELIVERY_OUTCOME_UNKNOWN(OutboxEntryState.Isolated),

    /** payload 타입을 몰라 **전달을 시도조차 못 했다** — 모호와 다른 사실이다. */
    UNKNOWN_PAYLOAD(OutboxEntryState.Isolated),

    /** 앞 run 이 claim 한 뒤 임대를 놓아 남은 행을 다음 run 이 격리했다(ADR 0005 D-3). */
    ORPHANED_CLAIM(OutboxEntryState.Isolated),
}
