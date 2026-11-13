package bidvector.workflow.collection

import bidvector.procurement.CanonicalizationOutcome
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.CollectionRunStore
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeRepository
import bidvector.procurement.NoticeSourcePort
import bidvector.procurement.PageCursor
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawObservationStore
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.procurement.canonicalize
import bidvector.workflow.strategy.Clock

/** 절단 원인이 다음 걸음을 정한다 — [directive] 가 [TruncationCause] 를 소진하므로 새 원인은 컴파일이 막는다. */
private enum class WalkDirective { RESUME_SAME_SLOT, NEXT_SLOT, HALT_RUN }

private fun TruncationCause.directive(): WalkDirective =
    when (this) {
        TruncationCause.MaxPages -> WalkDirective.RESUME_SAME_SLOT

        // 쿼터든 승인 상한이든 더 부르면 거절만 쌓인다 — 실행을 멈춘다(D-6G-47).
        TruncationCause.QuotaExhausted -> WalkDirective.HALT_RUN

        is TruncationCause.BudgetExhausted -> WalkDirective.HALT_RUN

        // 차단(OPS-09)도 **실행을 멈춘다.** 401·403 은 슬롯의 사실이 아니라 자격의 사실이라
        // 다음 슬롯도 같은 답을 받는다 — 넘어가면 남은 슬롯마다 거절될 호출을 한 번씩 더 내고
        // 승인 상한만 태운다(쿼터가 실행을 멈추는 D-6G-47 과 같은 근거).
        TruncationCause.AccessBlocked -> WalkDirective.HALT_RUN

        TruncationCause.RepeatedPage,
        TruncationCause.Timeout,
        TruncationCause.TransportFailure,
        TruncationCause.ServerError,
        TruncationCause.NotRetryable,
        TruncationCause.InputError,
        TruncationCause.Unclassified,
        TruncationCause.StructureFailure,
        TruncationCause.SelfThrottled,
        -> WalkDirective.NEXT_SLOT
    }

private class PlannedSlot(
    val slot: CollectionSlot,
    val port: NoticeSourcePort,
)

/**
 * 공고 목록 수집 use case(D-6F8-1) — 조회일 × 업종마다 커서가 끝날 때까지 페이지를 읽고, 항목마다
 * **원문 저장 → [canonicalize] → 영속**을 한 번씩 부르며, 슬롯마다 회계를 [CollectionRunStore] 에 남긴다.
 * 정규화는 [canonicalize] 하나뿐이다 — 이 클래스는 원문 필드를 직접 읽지 않는다(구조 게이트가 잠근다).
 *
 * **부분 실패**: 슬롯이 전송 실패·절단으로 끝나도 회계에 원인을 싣고 다음 슬롯으로 넘어간다. 실행을
 * 멈추는 사유는 **셋**이다 — 쿼터 소진([TruncationCause.QuotaExhausted]) · 승인 상한
 * ([TruncationCause.BudgetExhausted]) · 차단([TruncationCause.AccessBlocked]). 더 부르면 계속 실패하고
 * 한도를 태운다(정본은 이 파일의 절단 사유 → 다음 걸음 표다).
 * 저장소 장애 같은 인프라 예외는 삼키지 않고 전파한다(그 슬롯의 회계는 남지 않는다).
 *
 * 원문은 canonical 저장과 무관하게 먼저 커밋된다(`RawObservationStore` 계약 ⑤) — 정규화에서 탈락하거나
 * 저장이 거부된 항목의 원문도 남는다.
 */
class CollectNoticesUseCase(
    private val rawObservations: RawObservationStore,
    private val notices: NoticeRepository,
    private val runs: CollectionRunStore,
    private val policyFor: (CollectionReferenceDate) -> KonepsCollectionPolicyData,
    private val clock: Clock,
) {
    fun collect(
        range: CollectionRange,
        sources: List<CollectionSource>,
        progress: CollectionProgress = CollectionProgress {},
    ): CollectionReport {
        require(sources.map { it.name }.toSet().size == sources.size) { "업종 이름은 서로 달라야 한다" }
        val planned =
            range.dates.flatMap { date ->
                sources.map { PlannedSlot(CollectionSlot(date, it.name), it.port) }
            }
        val reports = mutableListOf<CollectionSlotReport>()
        for ((index, next) in planned.withIndex()) {
            val report = collectSlot(next)
            reports += report
            progress.onSlotFinished(report)
            val cause = report.accounting.truncationCause
            if (cause?.directive() == WalkDirective.HALT_RUN) {
                val halt = CollectionHalt(cause, next.slot, planned.drop(index + 1).map { it.slot })
                return CollectionReport(reports, halt)
            }
        }
        return CollectionReport(reports, halted = null)
    }

    /**
     * 슬롯 하나를 커서가 끝날 때까지 걷는다.
     *
     * **총수만 누산기 밖에서 쥔다** — [SlotTally] 의 소스 누산기는 마지막 배치의 총수를 쓰는데
     * (`source.sourceTotal ?: sourceTotal`), 어댑터는 배치마다 새 누산기라 쪽 상한 재개의 첫 쪽이
     * 총수를 말하지 않으면 **0** 을 낸다. 그 0 이 앞 배치의 총수를 덮으면 회계 행이 「받은 수 ≥ 총수」가
     * 되어 덜 받은 슬롯이 다 받은 것으로 적힌다. 그래서 배치마다 본 총수의 최댓값([widerTotalOf] —
     * 형제 두 갈래와 **같은** 한 자리)을 여기서 쥐고 회계에 싣는다. 누산기 자체를 고치지 않는 것은
     * 범위다 — 그 타입을 **세** 갈래가 공유하므로 거동이 함께 바뀐다.
     */
    private fun collectSlot(planned: PlannedSlot): CollectionSlotReport {
        val referenceDate = CollectionReferenceDate(planned.slot.referenceDate)
        val policy = policyFor(referenceDate)
        val startedAt = clock.now()
        val tally = SlotTally()
        var claimedTotal: Int? = null
        var cursor: PageCursor? = null
        do {
            val batch = planned.port.fetchNotices(referenceDate, cursor)
            tally.absorb(batch.accounting)
            claimedTotal = widerTotalOf(claimedTotal, batch.accounting.sourceTotal)
            batch.items.forEach { ingest(it, policy, tally) }
            val resume = resumeCursor(batch, cursor)
            cursor = resume
        } while (resume != null)
        val accounting = tally.toAccounting().copy(sourceTotal = claimedTotal)
        runs.record(accounting, CollectionRunMeta(referenceDate, SourceEndpoint.NOTICE_LIST, startedAt, clock.now()))
        return CollectionSlotReport(planned.slot, accounting, tally.writes())
    }

    private fun ingest(
        observation: RawNoticeObservation,
        policy: KonepsCollectionPolicyData,
        tally: SlotTally,
    ) {
        val observationKey = rawObservations.append(observation)
        when (val outcome = canonicalize(observation, policy)) {
            is CanonicalizationOutcome.Dropped -> tally.canonicalizationDropped(outcome.reason)
            is CanonicalizationOutcome.Normalized -> tally.wrote(notices.persist(outcome.command, observationKey))
        }
    }

    /** 이어 읽을 커서 — 페이지 상한으로 잘렸고 진전이 있을 때만(같은 커서를 다시 부르지 않는다). */
    private fun resumeCursor(
        batch: SourceBatch<RawNoticeObservation>,
        previous: PageCursor?,
    ): PageCursor? =
        batch.next?.takeIf {
            batch.accounting.truncationCause?.directive() == WalkDirective.RESUME_SAME_SLOT && it != previous
        }
}
