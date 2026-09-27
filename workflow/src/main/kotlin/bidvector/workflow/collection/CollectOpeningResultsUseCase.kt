package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunStore
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeId
import bidvector.procurement.OpeningResultSourcePort
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawObservationStore
import bidvector.procurement.SourceBatch
import bidvector.procurement.TruncationCause
import bidvector.procurement.decideDetailFetch
import bidvector.workflow.strategy.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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

private enum class DetailAxis { RESERVE_PRICE, OPENING_COMPLETE, BASE_AMOUNT, BID_PRICE_FORMULA_A }

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
    runs: CollectionRunStore,
    private val sampler: StratifiedSampler,
    policyFor: (CollectionReferenceDate) -> KonepsCollectionPolicyData,
    private val gates: DetailFetchGates,
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
        var detailCalls = 0
        for ((index, key) in sample.selected.withIndex()) {
            when (val step = fetchDetails(byKey.getValue(key), budget)) {
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

    private fun fetchDetails(
        picked: Candidate,
        budget: CallBudgetLedger,
    ): DetailStep {
        val today = executionDay()
        var calls = 0
        var halt: OpeningCollectionHalt? = null
        val axes = detailAxesFor(picked.source.division)
        var index = 0
        while (halt == null && index < axes.size) {
            val outcome = fetchAxis(axes[index], picked, budget, today)
            calls += outcome.calls
            halt = outcome.halt
            index++
        }
        return halt?.let { DetailStep.Halted(it, calls) } ?: DetailStep.Done(calls)
    }

    /** 상세 축 하나 — 예산이 막으면 호출 0, 쿼터가 나면 호출 1 과 멈춤 사유를 함께 낸다. */
    private fun fetchAxis(
        axis: DetailAxis,
        picked: Candidate,
        budget: CallBudgetLedger,
        today: LocalDate,
    ): AxisOutcome {
        val refused = refusal(budget, today)
        if (refused != null) return AxisOutcome(calls = 0, halt = refused)
        val batch = axis.fetch(picked.source.port, fetchEvidence(picked.id))
        budget.settle(today, batch.accounting.pagesFetched - 1)
        batch.items.forEach(rawObservations::append)
        val quota =
            batch.accounting.truncationCause
                ?.takeIf { it == TruncationCause.QuotaExhausted }
                ?.let { OpeningCollectionHalt(null, it, notAttempted = 0) }
        return AxisOutcome(calls = 1, halt = quota)
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

private class AxisOutcome(
    val calls: Int,
    val halt: OpeningCollectionHalt?,
)

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

internal val COLLECTION_BUDGET_ZONE: ZoneId = ZoneOffset.UTC

internal fun refusal(
    budget: CallBudgetLedger,
    onDay: LocalDate,
): OpeningCollectionHalt? =
    when (val outcome = budget.consume(onDay, 1)) {
        BudgetOutcome.Allowed -> null
        is BudgetOutcome.Exhausted -> OpeningCollectionHalt(outcome.limit, null, notAttempted = 0)
    }
