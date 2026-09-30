package bidvector.workflow.collection

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.TruncationCause

/**
 * 슬롯 하나가 **소스에서 받은** 회계의 누적 — 페이지 걷기 결과를 더하기만 하고 canonical 축
 * (저장 결과)은 모른다. 두 수집 갈래가 공유한다(v2-지침서 §5 중복 금지):
 *
 * - 공고 목록 갈래([SlotTally])는 여기에 canonical 저장 결과를 얹어 `normalized` 를 다시 정의한다.
 * - 개찰결과 갈래는 canonical 승격을 하지 않으므로 [toSourceAccounting] 으로 소스 회계를 그대로 낸다.
 *
 * 절단 원인은 **마지막** 배치의 것이 슬롯의 최종 상태다.
 */
internal class SourceAccountingTally {
    var received = 0
        private set
    var normalized = 0
        private set
    var duplicate = 0
        private set
    var dropped = 0
        private set
    var truncationCause: TruncationCause? = null
        private set

    private val dropReasons = mutableMapOf<CollectionDropReason, Int>()
    private var sourceTotal: Int? = null
    private var pagesFetched = 0
    private var unknownFields = 0
    private var quotaExceeded = 0
    private var backoffSkipped = 0
    private var maskingFailures = 0
    private var rowIdentifierIndeterminate = 0

    fun absorb(source: CollectionAccounting) {
        received += source.received
        normalized += source.normalized
        duplicate += source.duplicate
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

    fun addDrop(
        reason: CollectionDropReason,
        count: Int,
    ) {
        dropReasons[reason] = (dropReasons[reason] ?: 0) + count
    }

    /** 소스 회계 그대로 — 원문만 적재하는 갈래(원문 저장은 항목을 떨어뜨리지 않는다). */
    fun toSourceAccounting(): CollectionAccounting = toAccounting(normalized, duplicate, dropped)

    /** canonical 축을 호출부가 다시 정의하는 갈래 — 항등식은 [CollectionAccounting] 생성자가 검사한다. */
    fun toAccounting(
        normalized: Int,
        duplicate: Int,
        dropped: Int,
    ): CollectionAccounting =
        CollectionAccounting(
            received = received,
            normalized = normalized,
            duplicate = duplicate,
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
}
