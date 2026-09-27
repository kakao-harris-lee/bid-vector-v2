package bidvector.workflow.collection

import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.CollectionRunStore
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeId
import bidvector.procurement.PageCursor
import bidvector.procurement.RawObservationStore
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.procurement.noticeIdIn
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.strategy.Clock
import java.time.LocalDate

/** 표본틀 한 벌 — 후보와, 다 만들지 못했으면 그 사유. */
internal class Framing(
    val candidates: List<Candidate>,
    val halt: OpeningCollectionHalt?,
)

internal class Candidate(
    val id: NoticeId,
    val candidate: SampleCandidate,
    val source: OpeningCollectionSource,
)

private sealed interface PageWalk {
    data class Next(
        val cursor: PageCursor?,
    ) : PageWalk

    data class Stop(
        val halt: OpeningCollectionHalt?,
    ) : PageWalk
}

/**
 * 표본틀 만들기(D-6G-11 ①) — 개찰결과 목록을 **공고일 축**으로 걷는다(`inqryDiv` 가 그 군에서 `2` 다).
 * 공고일로 걷기 때문에 슬롯의 조회일이 곧 그 행의 공고일이고, 층(업무 × 공고 주)이 응답 필드 없이 선다 —
 * 개찰결과 목록 응답에는 공고일 항목이 **없어서**, 개찰일 축으로 걸으면 층을 세울 수가 없다.
 *
 * 상세 수집([CollectOpeningResultsUseCase])과 갈라 둔 이유는 순서가 계약이기 때문이다: 표본은 상세를
 * 한 번도 부르지 않고 확정되어야 한다(우회 ⑦). 두 관심사가 한 클래스에 있으면 그 순서가 규율로만 선다.
 */
internal class OpeningSampleFramer(
    private val rawObservations: RawObservationStore,
    private val runs: CollectionRunStore,
    private val policyFor: (CollectionReferenceDate) -> KonepsCollectionPolicyData,
    private val clock: Clock,
) {
    fun frame(
        range: CollectionRange,
        sources: List<OpeningCollectionSource>,
    ): Framing {
        require(sources.map { it.name }.toSet().size == sources.size) { "업종 이름은 서로 달라야 한다" }
        val candidates = mutableListOf<Candidate>()
        for (noticeDate in range.dates) {
            for (source in sources) {
                val halt = frameSlot(noticeDate, source, candidates)
                if (halt != null) return Framing(candidates, halt)
            }
        }
        return Framing(candidates, halt = null)
    }

    /**
     * 슬롯 하나를 끝까지 걷는다 — 멈춰야 하면 사유를 내고, 아니면 `null`. **어느 길로 끝나든 슬롯 회계를
     * 남긴다**: 예산이 막아 도중에 그만둔 슬롯도 「어디까지 읽었는가」가 남아야 이어 돌 수 있다.
     */
    private fun frameSlot(
        noticeDate: LocalDate,
        source: OpeningCollectionSource,
        into: MutableList<Candidate>,
    ): OpeningCollectionHalt? {
        val referenceDate = CollectionReferenceDate(noticeDate)
        val startedAt = clock.now()
        val tally = SourceAccountingTally()
        var walk: PageWalk = PageWalk.Next(null)
        while (walk is PageWalk.Next) {
            walk = framePage(referenceDate, source, into, tally, walk.cursor)
        }
        val accounting = tally.toSourceAccounting()
        runs.record(
            accounting,
            CollectionRunMeta(referenceDate, SourceEndpoint.OPENING_RESULT_LIST, startedAt, clock.now()),
        )
        return (walk as PageWalk.Stop).halt
    }

    /** 페이지 한 장 — 다음 커서를 내거나 멈춘다(멈춤 사유가 없으면 슬롯이 정상으로 끝난 것이다). */
    private fun framePage(
        referenceDate: CollectionReferenceDate,
        source: OpeningCollectionSource,
        into: MutableList<Candidate>,
        tally: SourceAccountingTally,
        cursor: PageCursor?,
    ): PageWalk {
        // 상한은 **관문이** 센다(D-6G-47) — 여기서 한 번 더 세면 같은 호출을 두 번 계상하고,
        // 관문이 막지 못한 경로가 있다는 착각을 준다. 거부는 절단 사유로 올라온다.
        return readPage(referenceDate, source, into, tally, cursor)
    }

    private fun readPage(
        referenceDate: CollectionReferenceDate,
        source: OpeningCollectionSource,
        into: MutableList<Candidate>,
        tally: SourceAccountingTally,
        cursor: PageCursor?,
    ): PageWalk {
        val batch = source.port.fetchOpeningResults(referenceDate, cursor)
        // 사후 정산이 없다 — 관문이 호출 **전에** 한 번씩 세므로 페이지 수로 메울 나머지가 없다.
        tally.absorb(batch.accounting)
        val policy = policyFor(referenceDate)
        batch.items.forEach { observation ->
            rawObservations.append(observation)
            noticeIdIn(observation, policy)?.let { id ->
                into +=
                    Candidate(
                        id,
                        SampleCandidate(keyOf(id), source.division, referenceDate.date),
                        source,
                    )
            }
        }
        val cause = batch.accounting.truncationCause
        val next = batch.next?.takeIf { cause == TruncationCause.MaxPages && it != cursor }
        return when {
            // 상한 거부는 **예산 멈춤**으로 올린다 — 어느 한도였는지가 다음 걸음을 정한다.
            cause is TruncationCause.BudgetExhausted -> PageWalk.Stop(OpeningCollectionHalt(cause.limit, null, 0))

            cause == TruncationCause.QuotaExhausted -> PageWalk.Stop(OpeningCollectionHalt(null, cause, 0))

            next != null -> PageWalk.Next(next)

            else -> PageWalk.Stop(null)
        }
    }

    private fun executionDay(): LocalDate = LocalDate.ofInstant(clock.now(), COLLECTION_BUDGET_ZONE)
}

internal fun keyOf(id: NoticeId): NoticeKeyHash = NoticeKeyHash.of(id.number.value, id.round.value)
