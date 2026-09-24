package bidvector.workflow.collection

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.PersistOutcome
import bidvector.procurement.TruncationCause

/**
 * 슬롯 하나의 누적 계수 — 소스 회계(페이지 걷기 결과)와 이 use case 가 더 센 것(정규화 탈락·저장 결과)을
 * 합쳐 최종 [CollectionAccounting] 을 만든다. 등식은 [CollectionAccounting] 생성자가 다시 검사한다.
 */
internal class SlotTally {
    private var received = 0
    private var sourceDuplicate = 0
    private var dropped = 0
    private val dropReasons = mutableMapOf<CollectionDropReason, Int>()
    private var sourceTotal: Int? = null
    private var pagesFetched = 0
    private var unknownFields = 0
    private var quotaExceeded = 0
    private var backoffSkipped = 0
    private var maskingFailures = 0
    private var rowIdentifierIndeterminate = 0
    private var truncationCause: TruncationCause? = null
    private var inserted = 0
    private var updated = 0
    private var unchanged = 0
    private var rejected = 0

    /** 배치 하나의 소스 회계를 더한다 — 절단 원인은 **마지막** 배치의 것이 슬롯의 최종 상태다. */
    fun absorb(source: CollectionAccounting) {
        received += source.received
        sourceDuplicate += source.duplicate
        dropped += source.dropped
        source.dropReasons.forEach { (reason, count) -> addDrop(reason, count) }
        sourceTotal = source.sourceTotal ?: sourceTotal
        pagesFetched += source.pagesFetched
        unknownFields += source.unknownFields
        quotaExceeded += source.quotaExceeded
        backoffSkipped += source.backoffSkipped
        maskingFailures += source.maskingFailures
        rowIdentifierIndeterminate += source.rowIdentifierIndeterminate
        truncationCause = source.truncationCause
    }

    fun canonicalizationDropped(reason: CollectionDropReason) {
        dropped++
        addDrop(reason, 1)
    }

    fun wrote(outcome: PersistOutcome) {
        when (outcome) {
            PersistOutcome.Inserted -> inserted++
            is PersistOutcome.Updated -> updated++
            PersistOutcome.Unchanged -> unchanged++
            is PersistOutcome.Rejected -> rejected++
        }
    }

    fun writes(): WriteTally = WriteTally(inserted, updated, unchanged, rejected)

    fun toAccounting(): CollectionAccounting =
        CollectionAccounting(
            received = received,
            normalized = inserted + updated,
            duplicate = sourceDuplicate + unchanged + rejected,
            dropped = dropped,
            dropReasons = dropReasons.toMap(),
            sourceTotal = sourceTotal,
            pagesFetched = pagesFetched,
            truncated = truncationCause != null,
            unknownFields = unknownFields,
            truncationCause = truncationCause,
            quotaExceeded = quotaExceeded,
            backoffSkipped = backoffSkipped,
            maskingFailures = maskingFailures,
            rowIdentifierIndeterminate = rowIdentifierIndeterminate,
        )

    private fun addDrop(
        reason: CollectionDropReason,
        count: Int,
    ) {
        dropReasons[reason] = (dropReasons[reason] ?: 0) + count
    }
}
