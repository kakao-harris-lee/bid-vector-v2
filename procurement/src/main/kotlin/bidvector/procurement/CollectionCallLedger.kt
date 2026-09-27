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
 * 이미 받은 (공고, 축)을 알려 준다(D-6G-29 ③ 이어 돌기) — 멈췄다 다시 돌 때 이미 쓴 호출을 또 쓰지
 * 않는다. 원문 관측의 존재가 곧 「받았다」이다(이 갈래는 canonical 승격을 하지 않는다).
 */
interface CollectedAxisStore {
    fun alreadyCollected(
        endpoint: SourceEndpoint,
        noticeIds: Collection<NoticeId>,
    ): Set<NoticeId>
}
