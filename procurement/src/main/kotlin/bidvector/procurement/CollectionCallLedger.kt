package bidvector.procurement

import java.time.Instant

/** 승인 호출 예산이 이미 쓴 몫 — 총계와 오늘치(D-6G-29 ①). */
data class CallSpend(
    val total: Int,
    val today: Int,
) {
    init {
        require(total >= 0 && today >= 0) { "쓴 호출 수는 음수일 수 없다: total=$total today=$today" }
        require(today <= total) { "오늘치($today)가 총계($total)보다 클 수 없다" }
    }
}

/**
 * 호출 예산 원장의 **영속 읽기**(D-6G-29 ①) — 승인 상한(A-1: 일 20,000 · 총 80,000)은 3~4일에 걸친
 * **여러 실행**을 덮는다. 원장이 프로세스 메모리에만 있으면 매 기동마다 총계가 0 으로 되돌아가
 * 승인된 총 상한이 실제로는 아무것도 막지 못한다(code-review H-5).
 *
 * `since` 는 **승인이 시작된 시점**이다 — 그 전의 수집 이력(다른 slice 의 실수집)을 이 예산에 계상하지
 * 않는다. 값은 설정이 주고 기본값이 없다.
 *
 * 세는 단위는 `collection_run.pages_fetched` 다 — 공고 목록 갈래와 개찰 축 갈래가 **같은 표**에 남기므로
 * A-1 이 「6G 의 모든 KONEPS 호출」을 덮는다는 문면이 한 원장으로 선다(D-6G-29 ⑥).
 */
interface CollectionCallLedgerStore {
    fun spentSince(
        since: Instant,
        dayStart: Instant,
    ): CallSpend
}

/**
 * 이미 받은 (공고, 축)을 알려 준다(D-6G-29 ③ 이어 돌기) — 멈췄다 다시 돌 때 이미 쓴 호출을 또 쓰지
 * 않는다. 원문 관측의 존재가 곧 「받았다」이다(이 갈래는 canonical 승격을 하지 않는다).
 */
interface CollectedAxisStore {
    fun alreadyCollected(
        endpoint: SourceEndpoint,
        noticeIds: Collection<NoticeId>,
    ): Set<NoticeId>
}
