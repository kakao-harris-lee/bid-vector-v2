package bidvector.strategy

import bidvector.sharedkernel.PolicyVersion

/**
 * 전략 변경 사실을 나르는 이벤트 payload(D-4 (a), ⑨) — 봉투(event id·aggregate version·
 * idempotency/correlation/causation id)는 M4 4C가, 변경 주체(actor) 슬롯은 `OPEN-STR-04`
 * 소유라 M4 4A가 넓힌다(D-17). 전이표는 없다 — `data-dictionary.md` §2.2.6.
 */
sealed interface StrategyEvent {
    data class StrategyUpdated(
        val revision: StrategyRevision,
        val policyVersion: PolicyVersion,
    ) : StrategyEvent
}
