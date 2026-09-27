package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.DetailFetchGates
import bidvector.procurement.NoticeId
import bidvector.procurement.OpeningResultSourcePort
import bidvector.procurement.PageCursor
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.workflow.strategy.Clock
import java.time.LocalDate

/** 6G 수집 갈래 test 의 fake 포트·조립 — 값과 호출 기록뿐이고 mock framework 는 없다. */
internal class ScriptedOpeningPort(
    private val division: BusinessDivision,
) : OpeningResultSourcePort {
    /** (공고일 → 그 슬롯이 낼 공고번호들). */
    val listScript = mutableMapOf<LocalDate, List<String>>()
    var listTruncation: TruncationCause? = null
    var listPagesFetched: Int = 1

    val listCalls = mutableListOf<LocalDate>()
    val reservePriceCalls = mutableListOf<NoticeId>()
    val openingCompleteCalls = mutableListOf<NoticeId>()
    val formulaACalls = mutableListOf<NoticeId>()

    override fun fetchOpeningResults(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> {
        listCalls += referenceDate.date
        val numbers = listScript[referenceDate.date].orEmpty()
        val items = numbers.map { openingListObservation(it) }
        return SourceBatch(
            items,
            sourceAccounting(
                normalized = items.size,
                pagesFetched = listPagesFetched,
                truncationCause = listTruncation,
            ),
            next = null,
        )
    }

    override fun fetchReservePrices(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        reservePriceCalls += evidence.noticeId
        return detailBatch(evidence, SourceEndpoint.RESERVE_PRICE_DETAIL)
    }

    override fun fetchOpeningCompleteResults(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        openingCompleteCalls += evidence.noticeId
        return detailBatch(evidence, SourceEndpoint.OPENING_COMPLETE)
    }

    val baseAmountCalls = mutableListOf<NoticeId>()

    override fun fetchBaseAmount(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        baseAmountCalls += evidence.noticeId
        return detailBatch(evidence, SourceEndpoint.BASE_AMOUNT_DETAIL)
    }

    override fun fetchBidPriceFormulaA(evidence: DetailFetchDecision.Fetch): SourceBatch<RawNoticeObservation> {
        check(division == BusinessDivision.CONSTRUCTION) { "A값은 공사에서만 불려야 한다" }
        formulaACalls += evidence.noticeId
        return detailBatch(evidence, SourceEndpoint.BID_PRICE_FORMULA_A)
    }

    private fun detailBatch(
        evidence: DetailFetchDecision.Fetch,
        endpoint: SourceEndpoint,
    ): SourceBatch<RawNoticeObservation> {
        val item = observationOf(evidence.noticeId.number.value, endpoint)
        return SourceBatch(listOf(item), sourceAccounting(normalized = 1), next = null)
    }
}

private fun openingListObservation(number: String): RawNoticeObservation =
    observationOf(number, SourceEndpoint.OPENING_RESULT_LIST)

private fun observationOf(
    number: String,
    endpoint: SourceEndpoint,
): RawNoticeObservation =
    RawNoticeObservation.of(
        mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
        endpoint,
        COLLECTION_NOW,
    )

internal class OpeningFixture(
    targetPerStratum: Int,
    private val budget: CollectionCallBudget = CollectionCallBudget(perDay = 20_000, total = 80_000),
) {
    val raw = RecordingRawStore()
    val runs = RecordingRunStore()
    val construction = ScriptedOpeningPort(BusinessDivision.CONSTRUCTION)
    val service = ScriptedOpeningPort(BusinessDivision.SERVICE)

    private val sources =
        listOf(
            OpeningCollectionSource(sourceName("construction"), BusinessDivision.CONSTRUCTION, construction),
            OpeningCollectionSource(sourceName("service"), BusinessDivision.SERVICE, service),
        )

    private val useCase =
        CollectOpeningResultsUseCase(
            rawObservations = raw,
            runs = runs,
            sampler = StratifiedSampler(SamplingSeed("6g-test-seed"), targetPerStratum),
            policyFor = { COLLECTION_POLICY },
            gates = DetailFetchGates(ageGateHours = 24, recheckGateHours = 48),
            clock = Clock { COLLECTION_NOW },
        )

    private var from: LocalDate? = null
    private var to: LocalDate? = null

    fun listRows(
        division: BusinessDivision,
        noticeDate: String,
        count: Int,
    ) {
        val day = LocalDate.parse(noticeDate)
        val port = portFor(division)
        // 합성 공고번호에 업무·공고일을 섞는다 — 같은 번호를 다른 층에 두면 키 해시가 같아 후보가 접힌다.
        port.listScript[day] = (1..count).map { "SYN-6G-$division-$noticeDate-%04d".format(it) }
        from = listOfNotNull(from, day).min()
        to = listOfNotNull(to, day).max()
    }

    fun run(): OpeningCollectionReport =
        useCase.collect(range(), sources, CallBudgetLedger(budget, COLLECTION_BUDGET_DAY))

    fun plan(): SampleOutcome = useCase.plan(range(), sources).sample

    fun hashOf(id: NoticeId): String = NoticeKeyHash.of(id.number.value, id.round.value).value

    private fun portFor(division: BusinessDivision): ScriptedOpeningPort =
        when (division) {
            BusinessDivision.CONSTRUCTION -> construction
            else -> service
        }

    private fun range(): CollectionRange = rangeOf(requireNotNull(from).toString(), requireNotNull(to).toString())
}

internal val COLLECTION_BUDGET_DAY: LocalDate = LocalDate.ofInstant(COLLECTION_NOW, COLLECTION_BUDGET_ZONE)
