package bidvector.workflow.collection

import bidvector.procurement.AttemptLedger
import bidvector.procurement.BudgetLimit
import bidvector.procurement.BusinessDivision
import bidvector.procurement.COLLECTION_BUDGET_ZONE
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.CollectionAccounting
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
import bidvector.workflow.collection.NoticeKeyHash
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
    /** **손도 대지 않은** 표본 수 — 반쯤 받은 공고는 여기 들지 않는다(D-6G-44 K6). */
    val notAttempted: Int,
    /**
     * 멈춘 그 공고를 **반쯤 받았는가.** 예산은 공고 단위로 통째로 허가하므로(D-6G-29 ④) 반쪽이
     * 생기지 않지만, 쿼터 거절은 축 사이에서 온다 — 그때 그 공고는 축 일부만 적재된 채 남는다.
     * 이 사실을 계수에 접으면 「손대지 않았다」가 거짓이 되고, 이어 돌기가 무엇을 다시 불러야
     * 하는지도 흐려진다.
     */
    val partialNotice: Boolean = false,
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
    /** 확정 표본인데 이번 표본틀에서 보이지 않은 공고 수 — 표본을 바꾸지 않고 사실만 센다(D-6G-39). */
    val sampleUnseen: Int,
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
    sampler: StratifiedSampler,
    policyFor: (CollectionReferenceDate) -> KonepsCollectionPolicyData,
    private val gates: DetailFetchGates,
    private val collectedAxes: CollectedAxisStore,
    sampleList: SampleListLedger,
    private val attempts: AttemptLedger,
    private val clock: Clock,
) {
    private val framer = OpeningSampleFramer(rawObservations, runs, policyFor, clock)
    private val samples = SampleResolution(sampler, sampleList)

    /**
     * ①② 만 — 상세를 부르지 않는다. **표본을 확정하지도 않는다**: 시험 삼아 돌린 계획이 표본을
     * 못 박으면 그 뒤의 수집이 계획의 창에 묶인다. 이미 확정돼 있으면 그것을 보여준다.
     */
    fun plan(
        range: CollectionRange,
        sources: List<OpeningCollectionSource>,
    ): OpeningCollectionPlan {
        val framing = framer.frame(range, sources)
        return OpeningCollectionPlan(framing.candidates.size, samples.preview(framing.candidates), framing.halt)
    }

    /**
     * **다시 뽑지 않는다**(D-6G-39) — 첫 실행이 확정한 표본이 이후 모든 실행의 기준이다. 늦게 개찰된
     * 공고가 창에 들어와도 표본은 그대로이고, 확정 표본인데 이번 표본틀에서 보이지 않은 공고는 부르지
     * 않고 [OpeningCollectionReport.sampleUnseen] 로 센다.
     */
    fun collect(
        range: CollectionRange,
        sources: List<OpeningCollectionSource>,
    ): OpeningCollectionReport {
        val framing = framer.frame(range, sources)
        if (framing.halt != null) {
            return OpeningCollectionReport(framing.candidates.size, EMPTY_SAMPLE, 0, 0, framing.halt)
        }
        val framed = samples.resolve(framing.candidates)
        return fanOut(framing, framed.sample, framed.unseen)
    }

    /** ③ 표본 공고마다 상세 — 멈추면 아직 손대지 않은 표본 수를 사유에 싣는다. */
    private fun fanOut(
        framing: Framing,
        sample: SampleOutcome,
        sampleUnseen: Int,
    ): OpeningCollectionReport {
        val byKey = framing.candidates.associateBy { it.candidate.key }
        val done = alreadyCollectedAxes(sample.selected.map { byKey.getValue(it).id })
        var detailCalls = 0
        for ((index, key) in sample.selected.withIndex()) {
            val picked = byKey.getValue(key)
            when (val step = fetchDetails(picked, done[picked.id].orEmpty())) {
                is DetailStep.Done -> {
                    detailCalls += step.calls
                }

                is DetailStep.Halted -> {
                    // 멈춘 그 공고는 반쯤 받았으면 「손대지 않은」 수에서 뺀다(D-6G-44 K6).
                    val untouched = sample.selected.size - index - if (step.halt.partialNotice) 1 else 0
                    val halt = step.halt.copy(notAttempted = untouched)
                    return OpeningCollectionReport(
                        framing.candidates.size,
                        sample,
                        detailCalls + step.calls,
                        sampleUnseen,
                        halt,
                    )
                }
            }
        }
        return OpeningCollectionReport(framing.candidates.size, sample, detailCalls, sampleUnseen, halted = null)
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
        alreadyDone: Set<DetailAxis>,
    ): DetailStep {
        // 상한 판정은 **관문**이 한다(D-6G-47) — 여기서 걸음 단위로 미리 세면 셈의 출처가 둘이
        // 되고, 걸음 단위 근사(받은 페이지)와 관문의 시도 수가 갈린다. 거부는 절단 사유로 온다.
        val remaining = detailAxesFor(picked.source.division).filterNot { it in alreadyDone }
        return runAxes(picked, remaining)
    }

    private fun runAxes(
        picked: Candidate,
        axes: List<DetailAxis>,
    ): DetailStep {
        var calls = 0
        var halt: OpeningCollectionHalt? = null
        var index = 0
        while (halt == null && index < axes.size) {
            val batch = axes[index].fetch(picked.source.port, fetchEvidence(picked.id))
            calls++
            batch.items.forEach(rawObservations::append)
            recordDetailRun(batch, axes[index])
            val cause = batch.accounting.truncationCause
            if (cause is TruncationCause.BudgetExhausted) {
                halt = OpeningCollectionHalt(cause.limit, null, notAttempted = 0)
            } else if (cause == TruncationCause.QuotaExhausted) {
                halt =
                    OpeningCollectionHalt(
                        null,
                        TruncationCause.QuotaExhausted,
                        notAttempted = 0,
                        // 마지막 축이 아니면 이 공고는 반쪽이다 — 남은 축이 적재되지 않았다.
                        partialNotice = index < axes.size - 1,
                    )
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

    /**
     * 이어 돌기의 입력(D-6G-29 ③ · D-6G-45) — **시도 원장 ∪ 원문 관측**이다. 원문만 보면 빈 응답이
     * 영원히 다시 불리고, 시도 원장만 보면 원장 없이 적재된 앞 실행의 원문을 못 본다.
     */
    private fun alreadyCollectedAxes(ids: List<NoticeId>): Map<NoticeId, Set<DetailAxis>> {
        val out = mutableMapOf<NoticeId, MutableSet<DetailAxis>>()
        DetailAxis.entries.forEach { axis ->
            collectedAxes.alreadyCollected(axis.endpoint, ids).forEach { id ->
                out.getOrPut(id) { mutableSetOf() }.add(axis)
            }
        }
        // 실행마다 한 번 읽는다 — 한 프로세스가 두 번 돌면 앞 실행의 시도도 보여야 한다.
        val attempted = attempts.read().settledAxes()
        ids.forEach { id ->
            val tried = attempted[NoticeKeyHash.of(id.number.value, id.round.value).value].orEmpty()
            DetailAxis.entries.filter { it.endpoint in tried }.forEach { axis ->
                out.getOrPut(id) { mutableSetOf() }.add(axis)
            }
        }
        return out
    }

    /** 조회 가치 술어를 거친 증거 — 6G 는 처음 받는 공고들이라 항상 `Fetch` 다(술어를 우회하지 않는다). */
    private fun fetchEvidence(id: NoticeId): DetailFetchDecision.Fetch =
        decideDetailFetch(
            noticeId = id,
            noticeKeyHash = NoticeKeyHash.of(id.number.value, id.round.value).value,
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
