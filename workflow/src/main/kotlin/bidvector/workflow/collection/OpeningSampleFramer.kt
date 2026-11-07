package bidvector.workflow.collection

import bidvector.procurement.BudgetLimit
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
    /**
     * 절단된 슬롯 수(D-6G-50) — 쿼터·상한이 아닌 절단은 멈춤을 내지 않고 조용히 접힌다. 그대로
     * 표본을 확정하면 **반쪽 표본틀에서 뽑은 표본이 영구히 굳는다**(vr M-3): 실패한 슬롯의 공고는
     * 다음 실행에 표본틀에 들어와도 표본은 이미 확정돼 있어 영영 뽑히지 않는다.
     */
    val truncatedSlots: Int = 0,
)

internal class Candidate(
    val id: NoticeId,
    val candidate: SampleCandidate,
    val source: OpeningCollectionSource,
    /**
     * 개찰 일시의 **선언**(M6/G1-C03) — 조회 가치 술어의 age-gate 앵커다. 표본 **선택**에는 들지
     * 않는다: 결과를 보기 전에 표본을 확정한다는 계약(우회 ⑦)은 그대로이고, 이 값은 뽑힌 뒤 「언제
     * 부를까」에만 쓴다. 지금 이 경로가 싣지 못하는 사실은 [OpeningAnchor] 가 이름으로 나른다.
     */
    val openingAnchor: OpeningAnchor,
)

/** 슬롯 하나의 끝 — 멈춤 사유와 「끝까지 읽었는가」. 둘은 다른 축이다(절단은 멈춤이 아니다). */
private class SlotOutcome(
    val halt: OpeningCollectionHalt?,
    val truncated: Boolean,
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
        var truncated = 0
        for (noticeDate in range.dates) {
            for (source in sources) {
                val outcome = frameSlot(noticeDate, source, candidates)
                if (outcome.halt != null) return Framing(candidates, outcome.halt, truncated)
                if (outcome.truncated) truncated++
            }
        }
        return Framing(candidates, halt = null, truncatedSlots = truncated)
    }

    /**
     * 슬롯 하나를 끝까지 걷는다 — 멈춰야 하면 사유를 내고, 아니면 `null`. **어느 길로 끝나든 슬롯 회계를
     * 남긴다**: 예산이 막아 도중에 그만둔 슬롯도 「어디까지 읽었는가」가 남아야 이어 돌 수 있다.
     */
    private fun frameSlot(
        noticeDate: LocalDate,
        source: OpeningCollectionSource,
        into: MutableList<Candidate>,
    ): SlotOutcome {
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
        return SlotOutcome((walk as PageWalk.Stop).halt, accounting.truncationCause != null)
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
                        // 이 한 줄이 별 slice 의 바꿀 자리다 — 지금 이 갈래는 개찰 일시를 싣지 못한다.
                        OpeningAnchor.NotCarriedByFrame,
                    )
            }
        }
        val cause = batch.accounting.truncationCause
        val next = batch.next?.takeIf { cause == TruncationCause.MaxPages && it != cursor }
        return when (val directive = cause.openingDirective()) {
            // 상한 거부는 **예산 멈춤**으로 올린다 — 어느 한도였는지가 다음 걸음을 정한다.
            is OpeningWalkDirective.HaltBudget -> {
                PageWalk.Stop(OpeningCollectionHalt(directive.limit, null, 0))
            }

            is OpeningWalkDirective.HaltSource -> {
                PageWalk.Stop(OpeningCollectionHalt(null, directive.cause, 0))
            }

            OpeningWalkDirective.Continue -> {
                next?.let(PageWalk::Next) ?: PageWalk.Stop(null)
            }
        }
    }

    private fun executionDay(): LocalDate = LocalDate.ofInstant(clock.now(), COLLECTION_BUDGET_ZONE)
}

internal fun keyOf(id: NoticeId): NoticeKeyHash = NoticeKeyHash.of(id.number.value, id.round.value)

/**
 * 개찰 걷기가 절단 사유를 만났을 때의 **다음 걸음**(M9/ops-code · cr HIGH · v M-1).
 *
 * 앞 판은 이 판정이 두 자리에 `else` 를 가진 `when {}` 으로 흩어져 있었다 — `BudgetExhausted` 와
 * `QuotaExhausted` 만 보고 나머지를 전부 「계속」으로 접었다. 그래서 `AccessBlocked` 가 생겨도
 * **컴파일이 그 두 자리를 가리키지 않았고**, 자격이 끊긴 날(키 만료·IP 차단) 표본 공고 × 축마다
 * 거절될 호출을 한 번씩 더 내며 공고 갈래와 **공유하는 승인 상한**(D-6G-47)을 태웠다. 개찰 run
 * 자신은 `halted == null` 이라 COMPLETE(0) 로 끝나 그 소진이 보이지도 않았다.
 *
 * 이제 표가 **한 자리**이고 [TruncationCause] 를 소진한다 — 새 사유가 생기면 컴파일이 여기를
 * 가리키고, 두 소비 자리는 이 답을 읽을 뿐이라 둘이 갈릴 수 없다.
 * **답이 값을 나른다**(cr r2 MEDIUM). 앞 판은 이것이 enum 이라 두 소비 자리가 사유를 다시 꺼내야
 * 했고, 그 꺼냄이 무검사 다운캐스트(`as TruncationCause.BudgetExhausted`)와 「`HALT_SOURCE` 면
 * `cause` 가 null 이 아니다」는 **자리 밖 관례**였다. 표가 틀리는 날의 모양이 컴파일 실패가 아니라
 * 운영 중 `ClassCastException`·`require` 였다 — 열거만 닫고 값은 열어 둔 셈이다. 지금은 답이 그
 * 값을 들고 오므로 두 소비 자리에 캐스트도 null 관례도 없다.
 */
internal sealed interface OpeningWalkDirective {
    /** 우리 승인 상한이 막았다 — **어느 한도였는지**가 다음 걸음을 정한다. */
    data class HaltBudget(
        val limit: BudgetLimit,
    ) : OpeningWalkDirective

    /** 원천이 거절했다(쿼터·차단) — 더 부르면 거절만 쌓인다. 회계에는 **그 사유**가 실린다. */
    data class HaltSource(
        val cause: TruncationCause,
    ) : OpeningWalkDirective

    /** 이 공고·축의 사실이다 — 다음 축·다음 공고로 간다. */
    data object Continue : OpeningWalkDirective
}

/**
 * 소진 `when` — `else` 가 없다.
 *
 * **차단이 [OpeningWalkDirective.HaltSource] 인 근거**: 401·403 은 그 축의 사실이 아니라 **자격의
 * 사실**이라 다음 축·다음 공고도 같은 답을 받는다. 공고 갈래에 `HALT_RUN` 을 준 근거와 같고
 * (`CollectNoticesUseCase`), 쿼터와 같은 자리에 두는 것은 둘 다 「원천이 거절했다」이기 때문이다 —
 * 회계에서는 `AccessBlocked` 와 `QuotaExhausted` 가 여전히 다른 값이다(운영자의 다음 할 일이 다르다).
 */
internal fun TruncationCause?.openingDirective(): OpeningWalkDirective =
    when (this) {
        null -> OpeningWalkDirective.Continue

        is TruncationCause.BudgetExhausted -> OpeningWalkDirective.HaltBudget(limit)

        TruncationCause.QuotaExhausted,
        TruncationCause.AccessBlocked,
        -> OpeningWalkDirective.HaltSource(this)

        TruncationCause.MaxPages,
        TruncationCause.RepeatedPage,
        TruncationCause.Timeout,
        TruncationCause.TransportFailure,
        TruncationCause.ServerError,
        TruncationCause.NotRetryable,
        TruncationCause.InputError,
        TruncationCause.Unclassified,
        TruncationCause.StructureFailure,
        TruncationCause.SelfThrottled,
        -> OpeningWalkDirective.Continue
    }
