package bidvector.workflow.collection

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.TruncationCause

/**
 * canonical 저장 결과 계수 — 회계의 `normalized`(= inserted + updated)·`duplicate`(⊇ unchanged +
 * rejected)를 되짚을 수 있게 남긴다. 거부([rejected])는 canonical 을 바꾸지 않았으므로 회계에서는
 * `duplicate` 로 접히지만 거부 사실은 여기와 `rejected_write` 감사 표에 남는다.
 */
data class WriteTally(
    val inserted: Int,
    val updated: Int,
    val unchanged: Int,
    val rejected: Int,
)

/** 슬롯 하나의 최종 결과 — 원문·키·공고 내용을 나르지 않는다(건수와 열거값뿐). */
data class CollectionSlotReport(
    val slot: CollectionSlot,
    val accounting: CollectionAccounting,
    val writes: WriteTally,
)

/** 쿼터 소진으로 실행이 멈춘 사실 — 멈춘 자리와 끝내 부르지 않은 슬롯을 싣는다. */
data class CollectionHalt(
    val cause: TruncationCause,
    val at: CollectionSlot,
    val notAttempted: List<CollectionSlot>,
)

data class CollectionReport(
    val slots: List<CollectionSlotReport>,
    val halted: CollectionHalt?,
)

/** 슬롯이 끝날 때마다 불린다 — 긴 실행의 진행 표시와 중도 실패 지점 파악에 쓴다. */
fun interface CollectionProgress {
    fun onSlotFinished(report: CollectionSlotReport)
}
