package bidvector.workflow.collection

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.PersistOutcome

/**
 * 공고 목록 갈래 슬롯 하나의 누적 계수 — 소스 회계는 [SourceAccountingTally] 가 지고, 이 타입은
 * **canonical 저장 결과**를 얹어 `normalized`·`duplicate`·`dropped` 를 다시 정의한다. 등식은
 * [CollectionAccounting] 생성자가 다시 검사한다.
 */
internal class SlotTally {
    private val source = SourceAccountingTally()
    private var canonicalDropped = 0
    private var inserted = 0
    private var updated = 0
    private var unchanged = 0
    private var rejected = 0

    /** 배치 하나의 소스 회계를 더한다 — 절단 원인은 **마지막** 배치의 것이 슬롯의 최종 상태다. */
    fun absorb(source: CollectionAccounting) {
        this.source.absorb(source)
    }

    fun canonicalizationDropped(reason: CollectionDropReason) {
        canonicalDropped++
        source.addDrop(reason, 1)
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
        source.toAccounting(
            normalized = inserted + updated,
            duplicate = source.duplicate + unchanged + rejected,
            dropped = source.dropped + canonicalDropped,
        )
}
