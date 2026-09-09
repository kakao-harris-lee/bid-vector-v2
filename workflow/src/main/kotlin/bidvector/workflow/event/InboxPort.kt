package bidvector.workflow.event

/**
 * consumer 측 재수신 기록 port(scope.md ④) — 구현은 4C-2, 이 slice는 test fake만. 위조해도
 * 자기 소비만 손해라 닫지 않는다(설계 검토 (2) 표 다섯째 행).
 */
interface InboxPort {
    fun hasProcessed(key: IdempotencyKey): Boolean

    fun markProcessed(key: IdempotencyKey)
}

/** [decideInbox]의 결과 — 소비 지점은 이 값만 보고 분기한다. */
sealed interface InboxDecision {
    data object Process : InboxDecision

    data object SkipDuplicate : InboxDecision
}

/**
 * dedup 순수 판정(scope.md ④, ADR 0005 D-3) — 「이미 처리했는가」한 bit만 본다. 소비
 * 경로가 이 함수만 지나면 같은 [IdempotencyKey] 재수신은 효과 0이다(호출자가 매번 새 키를
 * 지어낼 자리가 없다 — 키는 봉투 등록 시 고정, 설계 검토 (1)).
 */
fun decideInbox(alreadyProcessed: Boolean): InboxDecision =
    if (alreadyProcessed) InboxDecision.SkipDuplicate else InboxDecision.Process
