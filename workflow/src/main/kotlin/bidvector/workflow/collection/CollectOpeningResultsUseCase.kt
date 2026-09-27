package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.CollectionRunStore
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeId
import bidvector.procurement.OpeningResultSourcePort
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawObservationStore
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.procurement.decideDetailFetch
import bidvector.workflow.evaluation.OPENING_DATE_ZONE
import bidvector.workflow.strategy.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 6G 수집 갈래가 부르는 업무 하나 — 이름·대분류·포트. 대분류는 **포트가 아니라 여기**가 안다:
 * 개찰 축 응답에는 업무 필드가 없고 어느 오퍼레이션으로 받았는가가 곧 대분류다(D-6F9-1).
 */
class OpeningCollectionSource(
    val name: CollectionSourceName,
    val division: BusinessDivision,
    val port: OpeningResultSourcePort,
)

/**
 * 실행이 멈춘 사실 — **예산과 쿼터를 구별한다.** 예산은 승인 범위를 다 쓴 것(내일 이어 돌 수 있다)이고
 * 쿼터는 KONEPS 가 거절한 것(더 부르면 거부만 쌓인다)이다. 하나로 접으면 다음 걸음을 정할 수 없다.
 */
data class OpeningCollectionHalt(
    val budgetLimit: BudgetLimit?,
    val truncationCause: TruncationCause?,
    val notAttempted: Int,
) {
    init {
        require((budgetLimit == null) != (truncationCause == null)) {
            "멈춤 사유는 예산이거나 쿼터이지 둘 다이거나 둘 다 아닐 수 없다"
        }
    }
}

/** 표본틀과 표본 — 상세를 한 번도 부르지 않고도 나온다(결과를 보기 전에 확정된다는 것의 형태). */
data class OpeningCollectionPlan(
    val frameSize: Int,
    val sample: SampleOutcome,
    val halt: OpeningCollectionHalt?,
)

data class OpeningCollectionReport(
    val frameSize: Int,
    val sample: SampleOutcome,
    val detailCalls: Int,
    val halted: OpeningCollectionHalt?,
)

/**
 * 한 표본 공고에 부를 상세 축(D-6G-20) — 예비가격 상세 · 개찰완료 · 기초금액은 **모든 업무**, A값은
 * **공사만**(D-6G-12). 기초금액 조회가 여기 드는 이유는 예가 범위율·기초금액 공개일시가 그 오퍼레이션
 * 에서만 오기 때문이다(D-6G-19).
 */
private fun detailAxesFor(division: BusinessDivision): List<DetailAxis> =
    when (division) {
        BusinessDivision.CONSTRUCTION -> {
            listOf(
                DetailAxis.RESERVE_PRICE,
                DetailAxis.OPENING_COMPLETE,
                DetailAxis.BASE_AMOUNT,
                DetailAxis.BID_PRICE_FORMULA_A,
            )
        }

        BusinessDivision.SERVICE, BusinessDivision.GOODS, BusinessDivision.FOREIGN -> {
            listOf(DetailAxis.RESERVE_PRICE, DetailAxis.OPENING_COMPLETE, DetailAxis.BASE_AMOUNT)
        }
    }

private enum class DetailAxis(
    val endpoint: SourceEndpoint,
) {
    RESERVE_PRICE(SourceEndpoint.RESERVE_PRICE_DETAIL),
    OPENING_COMPLETE(SourceEndpoint.OPENING_COMPLETE),
    BASE_AMOUNT(SourceEndpoint.BASE_AMOUNT_DETAIL),
    BID_PRICE_FORMULA_A(SourceEndpoint.BID_PRICE_FORMULA_A),
}

private fun DetailAxis.fetch(
    port: OpeningResultSourcePort,
    evidence: DetailFetchDecision.Fetch,
): SourceBatch<RawNoticeObservation> =
    when (this) {
        DetailAxis.RESERVE_PRICE -> port.fetchReservePrices(evidence)
        DetailAxis.OPENING_COMPLETE -> port.fetchOpeningCompleteResults(evidence)
        DetailAxis.BASE_AMOUNT -> port.fetchBaseAmount(evidence)
        DetailAxis.BID_PRICE_FORMULA_A -> port.fetchBidPriceFormulaA(evidence)
    }

/**
 * 개찰결과 수집 갈래(D-6G-1·11) — 세 걸음이다.
 *
 * ① **표본틀**: 개찰결과 목록을 **공고일 축**으로 걷는다(`inqryDiv` 가 그 군에서 `2` 다). 공고일로 걷기
 * 때문에 슬롯의 조회일이 곧 그 행의 공고일이고, 층(업무 × 공고 주)이 응답 필드 없이 선다 — 개찰결과 목록
 * 응답에는 공고일 항목이 없어서, 개찰일 축으로 걸으면 층을 세울 수가 없다.
 *
 * ② **표본 선택**: [StratifiedSampler]. 결과를 보기 전에, 결과와 무관한 값으로(우회 ⑦).
 *
 * ③ **표본 공고마다 상세**: 예비가격 상세 · 개찰완료(참가자 전 행) · 공사면 A값.
 *
 * **멈춤은 둘**이다. 호출 예산([CallBudgetLedger])은 승인 범위를 다 쓴 것이고, 쿼터
 * ([TruncationCause.QuotaExhausted])는 KONEPS 가 거절한 것이다. 그 밖의 절단은 그 슬롯·그 공고만 접고
 * 이어 간다 — 한 공고의 상세가 실패했다고 표본 전체를 버리면 표본이 비뚤어진다.
 *
 * canonical 승격을 하지 않는다 — 원문만 적재한다(D-6G-1 「새 표·마이그레이션 없음」). 투찰자 행은
 * raw 관측까지만 온다(D-6G-10).
 */
class CollectOpeningResultsUseCase(
    private val rawObservations: RawObservationStore,
    private val runs: CollectionRunStore,
    private val sampler: StratifiedSampler,
    policyFor: (CollectionReferenceDate) -> KonepsCollectionPolicyData,
    private val gates: DetailFetchGates,
    private val collectedAxes: CollectedAxisStore,
    private val clock: Clock,
) {
    private val framer = OpeningSampleFramer(rawObservations, runs, policyFor, clock)

    /** ①② 만 — 상세를 부르지 않는다. 같은 입력이면 [collect] 와 같은 표본이 나온다. */
    fun plan(
        range: CollectionRange,
        sources: List<OpeningCollectionSource>,
    ): OpeningCollectionPlan {
        val framing = framer.frame(range, sources, budget = null)
        return OpeningCollectionPlan(framing.candidates.size, sampleOf(framing), framing.halt)
    }

    private fun sampleOf(framing: Framing): SampleOutcome = sampler.select(framing.candidates.map { it.candidate })

    fun collect(
        range: CollectionRange,
        sources: List<OpeningCollectionSource>,
        budget: CallBudgetLedger,
    ): OpeningCollectionReport {
        val framing = framer.frame(range, sources, budget)
        return if (framing.halt != null) {
            OpeningCollectionReport(framing.candidates.size, EMPTY_SAMPLE, 0, framing.halt)
        } else {
            fanOut(framing, sampleOf(framing), budget)
        }
    }

    /** ③ 표본 공고마다 상세 — 멈추면 아직 손대지 않은 표본 수를 사유에 싣는다. */
    private fun fanOut(
        framing: Framing,
        sample: SampleOutcome,
        budget: CallBudgetLedger,
    ): OpeningCollectionReport {
        val byKey = framing.candidates.associateBy { it.candidate.key }
        val done = alreadyCollectedAxes(sample.selected.map { byKey.getValue(it).id })
        var detailCalls = 0
        for ((index, key) in sample.selected.withIndex()) {
            val picked = byKey.getValue(key)
            when (val step = fetchDetails(picked, budget, done[picked.id].orEmpty())) {
                is DetailStep.Done -> {
                    detailCalls += step.calls
                }

                is DetailStep.Halted -> {
                    val halt = step.halt.copy(notAttempted = sample.selected.size - index)
                    return OpeningCollectionReport(framing.candidates.size, sample, detailCalls + step.calls, halt)
                }
            }
        }
        return OpeningCollectionReport(framing.candidates.size, sample, detailCalls, halted = null)
    }

    /**
     * 한 공고의 상세 축 전부 — **통째로 허가되거나 통째로 거부된다**(D-6G-29 ④). 남은 몫만큼 반만
     * 부르면 그 공고는 결측이 랜덤이 아니라 예산 경계에 걸려 생기고, 표본이 비뚤어진다.
     *
     * 이미 받은 축은 **예산에서도 빼고 부르지도 않는다**(③ 이어 돌기) — 멈췄다 다시 돌 때 같은 호출을
     * 두 번 쓰지 않는다. 남은 축이 없으면 예산을 한 번도 묻지 않는다.
     */
    private fun fetchDetails(
        picked: Candidate,
        budget: CallBudgetLedger,
        alreadyDone: Set<DetailAxis>,
    ): DetailStep {
        val today = executionDay()
        val remaining = detailAxesFor(picked.source.division).filterNot { it in alreadyDone }
        val permit = if (remaining.isEmpty()) BudgetOutcome.Allowed else budget.consume(today, remaining.size)
        return when (permit) {
            BudgetOutcome.Allowed -> {
                runAxes(picked, budget, remaining, today)
            }

            is BudgetOutcome.Exhausted -> {
                DetailStep.Halted(OpeningCollectionHalt(permit.limit, null, notAttempted = 0), 0)
            }
        }
    }

    private fun runAxes(
        picked: Candidate,
        budget: CallBudgetLedger,
        axes: List<DetailAxis>,
        today: LocalDate,
    ): DetailStep {
        var calls = 0
        var halt: OpeningCollectionHalt? = null
        var index = 0
        while (halt == null && index < axes.size) {
            val batch = axes[index].fetch(picked.source.port, fetchEvidence(picked.id))
            // 이미 한 장은 `consume` 이 셌다 — 나머지 페이지만 정산한다(음수는 `settle` 이 0 으로 접는다).
            budget.settle(today, batch.accounting.pagesFetched - 1)
            calls++
            batch.items.forEach(rawObservations::append)
            recordDetailRun(batch, axes[index])
            if (batch.accounting.truncationCause == TruncationCause.QuotaExhausted) {
                halt = OpeningCollectionHalt(null, TruncationCause.QuotaExhausted, notAttempted = 0)
            }
            index++
        }
        return halt?.let { DetailStep.Halted(it, calls) } ?: DetailStep.Done(calls)
    }

    /** 상세 호출도 회계 원장에 남긴다 — 그래야 다음 실행의 예산 seed 가 이 호출들을 본다(D-6G-29 ①). */
    private fun recordDetailRun(
        batch: SourceBatch<RawNoticeObservation>,
        axis: DetailAxis,
    ) {
        val now = clock.now()
        runs.record(
            batch.accounting,
            CollectionRunMeta(CollectionReferenceDate(executionDay()), axis.endpoint, now, now),
        )
    }

    /** 이미 받은 (공고, 축) — 이어 돌기의 입력(D-6G-29 ③). */
    private fun alreadyCollectedAxes(ids: List<NoticeId>): Map<NoticeId, Set<DetailAxis>> {
        val out = mutableMapOf<NoticeId, MutableSet<DetailAxis>>()
        DetailAxis.entries.forEach { axis ->
            collectedAxes.alreadyCollected(axis.endpoint, ids).forEach { id ->
                out.getOrPut(id) { mutableSetOf() }.add(axis)
            }
        }
        return out
    }

    /** 조회 가치 술어를 거친 증거 — 6G 는 처음 받는 공고들이라 항상 `Fetch` 다(술어를 우회하지 않는다). */
    private fun fetchEvidence(id: NoticeId): DetailFetchDecision.Fetch =
        decideDetailFetch(
            noticeId = id,
            alreadyHeld = false,
            openingObservedAt = null,
            lastCheckedAt = null,
            now = clock.now(),
            gates = gates,
        ) as DetailFetchDecision.Fetch

    /**
     * 예산이 세는 「날」은 **실행 날짜**이지 조회 대상 공고일이 아니다. 조회일을 쓰면 공고일 슬롯을 넘길
     * 때마다 일 회계가 0 으로 되돌아가 일 상한이 아무것도 막지 못한다(test 가 잡은 자리).
     */
    private fun executionDay(): LocalDate = LocalDate.ofInstant(clock.now(), COLLECTION_BUDGET_ZONE)
}

private sealed interface DetailStep {
    data class Done(
        val calls: Int,
    ) : DetailStep

    data class Halted(
        val halt: OpeningCollectionHalt,
        val calls: Int,
    ) : DetailStep
}

private val EMPTY_SAMPLE = SampleOutcome(emptyList(), emptyMap())

/**
 * 예산이 세는 「하루」의 구역 — **Asia/Seoul** 이다. KONEPS 일 한도와 이 저장소의 다른 모든 날짜 축이
 * 같은 구역이라, UTC 로 세면 일 회계가 09:00 KST 에 리셋돼 **같은 KONEPS 하루 안에서 승인 일 상한을
 * 두 번** 받는다(code-review H-6).
 */
internal val COLLECTION_BUDGET_ZONE: ZoneId = OPENING_DATE_ZONE

internal fun refusal(
    budget: CallBudgetLedger,
    onDay: LocalDate,
): OpeningCollectionHalt? =
    when (val outcome = budget.consume(onDay, 1)) {
        BudgetOutcome.Allowed -> null
        is BudgetOutcome.Exhausted -> OpeningCollectionHalt(outcome.limit, null, notAttempted = 0)
    }
